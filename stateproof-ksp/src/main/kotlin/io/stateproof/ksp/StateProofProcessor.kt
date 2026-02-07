package io.stateproof.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.validate
import java.io.OutputStream

class StateProofProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    private val logger = environment.logger
    private val codeGenerator = environment.codeGenerator

    override fun process(resolver: com.google.devtools.ksp.processing.Resolver): List<KSAnnotated> {
        val symbols = resolver
            .getSymbolsWithAnnotation(ANNOTATION_FQN)
            .toList()

        val invalid = symbols.filterNot { it.validate() }

        val functions = symbols.filterIsInstance<KSFunctionDeclaration>()
        if (functions.isEmpty()) return invalid

        val metas = mutableListOf<StateMachineMeta>()

        for (fn in functions) {
            val returnType = fn.returnType?.resolve()
            val returnFqn = returnType?.declaration?.qualifiedName?.asString()
            if (returnFqn != STATE_MACHINE_FQN) {
                logger.error(
                    "@StateProofStateMachine must return $STATE_MACHINE_FQN, found: $returnFqn",
                    fn
                )
                continue
            }

            val eventType = returnType.arguments.getOrNull(1)?.type?.resolve()
            if (eventType == null) {
                logger.error("Unable to resolve event type for @StateProofStateMachine", fn)
                continue
            }

            val annotation = fn.annotations.firstOrNull {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_FQN
            }

            val args = annotation?.arguments?.associateBy { it.name?.asString() } ?: emptyMap()
            val name = (args["name"]?.value as? String).orEmpty()
            val testPackage = (args["testPackage"]?.value as? String).orEmpty()
            val testClassName = (args["testClassName"]?.value as? String).orEmpty()
            val eventPrefix = (args["eventPrefix"]?.value as? String).orEmpty()
            val stateMachineFactory = (args["stateMachineFactory"]?.value as? String).orEmpty()

            val additionalImports = (args["additionalImports"]?.value as? List<*>)
                ?.filterIsInstance<String>()
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val targets = (args["targets"]?.value as? List<*>)
                ?.filterIsInstance<String>()
                ?.map { it.lowercase() }
                ?.ifEmpty { listOf("jvm", "android") }
                ?: listOf("jvm", "android")

            val baseName = deriveBaseName(name, fn.simpleName.asString())
            val sanitizedBaseName = sanitizeBaseName(baseName)

            val packageName = fn.packageName.asString()
            val factoryFunctionName = "create${sanitizedBaseName}ForIntrospection"
            val factoryFileName = "StateProofIntrospectionFactory_${sanitizedBaseName}"
            val factoryFqn = "$packageName.${factoryFileName}Kt#$factoryFunctionName"

            val callExpression = buildCallExpression(fn)
            if (callExpression == null) {
                logger.error("Unsupported @StateProofStateMachine target (must be top-level or object/companion)", fn)
                continue
            }

            val parameters = fn.parameters.mapNotNull { param ->
                val paramName = param.name?.asString()
                if (paramName.isNullOrBlank()) {
                    logger.error("All parameters must have a name", param)
                    return@mapNotNull null
                }
                val type = param.type.resolve()
                val typeString = renderType(type)
                val rawType = renderRawType(type)
                ParamInfo(paramName, typeString, rawType)
            }

            val eventClassName = eventType.declaration.simpleName.asString()
            val eventClassFqn = eventType.declaration.qualifiedName?.asString() ?: eventClassName

            metas.add(
                StateMachineMeta(
                    name = if (name.isNotBlank()) name else sanitizedBaseName,
                    baseName = sanitizedBaseName,
                    packageName = packageName,
                    returnType = renderType(returnType),
                    factoryFunctionName = factoryFunctionName,
                    factoryFileName = factoryFileName,
                    factoryFqn = factoryFqn,
                    callExpression = callExpression,
                    parameters = parameters,
                    eventClassName = eventClassName,
                    eventClassFqn = eventClassFqn,
                    testPackage = testPackage,
                    testClassName = testClassName,
                    eventPrefix = eventPrefix,
                    stateMachineFactory = stateMachineFactory,
                    additionalImports = additionalImports,
                    targets = targets,
                    containingFile = fn.containingFile,
                )
            )
        }

        if (metas.isEmpty()) return invalid

        for (meta in metas) {
            generateFactory(meta)
        }

        generateRegistry(metas)

        return invalid
    }

    private fun generateFactory(meta: StateMachineMeta) {
        val dependencies = meta.containingFile?.let { Dependencies(false, it) } ?: Dependencies(false)
        val file = try {
            codeGenerator.createNewFile(dependencies, meta.packageName, meta.factoryFileName)
        } catch (_: Exception) {
            return
        }

        val builder = StringBuilder()
        builder.appendLine("package ${meta.packageName}")
        builder.appendLine()
        builder.appendLine("import io.stateproof.registry.StateProofAutoMocks")
        builder.appendLine()
        builder.appendLine("fun ${meta.factoryFunctionName}(): ${meta.returnType} {")

        if (meta.parameters.isEmpty()) {
            builder.appendLine("    return ${meta.callExpression}()")
        } else {
            builder.appendLine("    return ${meta.callExpression}(")
            meta.parameters.forEach { param ->
                builder.appendLine("        ${param.name} = StateProofAutoMocks.provide(${param.rawType}::class) as ${param.type},")
            }
            builder.appendLine("    )")
        }

        builder.appendLine("}")

        file.writeText(builder.toString())
    }

    private fun generateRegistry(metas: List<StateMachineMeta>) {
        val packageName = "io.stateproof.registry.generated"
        val hash = metas.joinToString("|") { it.factoryFqn }.hashCode().toUInt().toString(16).uppercase()
        val className = "StateProofRegistry_$hash"
        val classFqn = "$packageName.$className"

        val deps = Dependencies(true, *metas.mapNotNull { it.containingFile }.toTypedArray())
        val file = try {
            codeGenerator.createNewFile(deps, packageName, className)
        } catch (_: Exception) {
            return
        }

        val builder = StringBuilder()
        builder.appendLine("package $packageName")
        builder.appendLine()
        builder.appendLine("import io.stateproof.registry.StateMachineDescriptor")
        builder.appendLine("import io.stateproof.registry.StateMachineRegistry")
        builder.appendLine()
        builder.appendLine("class $className : StateMachineRegistry {")
        builder.appendLine("    override fun getStateMachines(): List<StateMachineDescriptor> = listOf(")

        for (meta in metas) {
            builder.appendLine("        StateMachineDescriptor(")
            builder.appendLine("            name = \"${escape(meta.name)}\",")
            builder.appendLine("            baseName = \"${escape(meta.baseName)}\",")
            builder.appendLine("            packageName = \"${escape(meta.packageName)}\",")
            builder.appendLine("            factoryFqn = \"${escape(meta.factoryFqn)}\",")
            builder.appendLine("            eventClassName = \"${escape(meta.eventClassName)}\",")
            builder.appendLine("            eventClassFqn = \"${escape(meta.eventClassFqn)}\",")
            builder.appendLine("            testPackage = \"${escape(meta.testPackage)}\",")
            builder.appendLine("            testClassName = \"${escape(meta.testClassName)}\",")
            builder.appendLine("            eventPrefix = \"${escape(meta.eventPrefix)}\",")
            builder.appendLine("            stateMachineFactory = \"${escape(meta.stateMachineFactory)}\",")
            builder.appendLine("            additionalImports = ${formatStringList(meta.additionalImports)},")
            builder.appendLine("            targets = ${formatStringList(meta.targets)},")
            builder.appendLine("        ),")
        }

        builder.appendLine("    )")
        builder.appendLine("}")

        file.writeText(builder.toString())
        writeServiceFile(deps, classFqn)
    }

    private fun buildCallExpression(fn: KSFunctionDeclaration): String? {
        val parent = fn.parentDeclaration
        return when (parent) {
            null -> fn.simpleName.asString()
            is KSClassDeclaration -> {
                val parentName = parent.simpleName.asString()
                if (parent.classKind == ClassKind.OBJECT) {
                    if (parent.isCompanionObject) {
                        val outer = parent.parentDeclaration as? KSClassDeclaration
                        val outerName = outer?.simpleName?.asString() ?: return null
                        "$outerName.Companion.${fn.simpleName.asString()}"
                    } else {
                        "$parentName.${fn.simpleName.asString()}"
                    }
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun deriveBaseName(name: String, fallback: String): String {
        val raw = if (name.isNotBlank()) name else fallback
        val trimmed = raw.removePrefix("get").removePrefix("create").removePrefix("build")
        val base = trimmed.replaceFirstChar { it.uppercase() }
        return if (base.endsWith("StateMachine")) base else "${base}StateMachine"
    }

    private fun sanitizeBaseName(value: String): String {
        val parts = value.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
        val camel = parts.joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        if (camel.isBlank()) return "StateMachine"
        return if (camel.endsWith("StateMachine")) camel else "${camel}StateMachine"
    }

    private fun renderRawType(type: com.google.devtools.ksp.symbol.KSType): String {
        return type.declaration.qualifiedName?.asString() ?: type.declaration.simpleName.asString()
    }

    private fun renderType(type: com.google.devtools.ksp.symbol.KSType): String {
        val decl = type.declaration.qualifiedName?.asString() ?: type.declaration.simpleName.asString()
        val args = type.arguments
        val renderedArgs = args.map { arg ->
            arg.type?.resolve()?.let { renderType(it) } ?: "*"
        }
        val base = if (renderedArgs.isNotEmpty()) {
            "$decl<${renderedArgs.joinToString(", ")}>"
        } else {
            decl
        }
        return if (type.nullability == Nullability.NULLABLE) "$base?" else base
    }

    private fun formatStringList(values: List<String>): String {
        if (values.isEmpty()) return "emptyList()"
        return values.joinToString(prefix = "listOf(", postfix = ")") { "\"${escape(it)}\"" }
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    private fun writeServiceFile(deps: Dependencies, registryClassFqn: String) {
        val path = "META-INF/services/io.stateproof.registry.StateMachineRegistry"
        val method = codeGenerator.javaClass.methods.firstOrNull { it.name == "createNewFileByPath" }
        if (method == null) {
            logger.error("KSP CodeGenerator.createNewFileByPath is not available. Please upgrade KSP.")
            return
        }

        val output: OutputStream = try {
            when (method.parameterTypes.size) {
                3 -> method.invoke(codeGenerator, deps, path, "") as OutputStream
                4 -> method.invoke(codeGenerator, deps, path, "", "") as OutputStream
                else -> method.invoke(codeGenerator, deps, path) as OutputStream
            }
        } catch (e: Exception) {
            logger.error("Failed to create service file: ${e.message}")
            return
        }

        output.write((registryClassFqn + "\n").toByteArray())
        output.flush()
        output.close()
    }

    private data class ParamInfo(
        val name: String,
        val type: String,
        val rawType: String,
    )

    private data class StateMachineMeta(
        val name: String,
        val baseName: String,
        val packageName: String,
        val returnType: String,
        val factoryFunctionName: String,
        val factoryFileName: String,
        val factoryFqn: String,
        val callExpression: String,
        val parameters: List<ParamInfo>,
        val eventClassName: String,
        val eventClassFqn: String,
        val testPackage: String,
        val testClassName: String,
        val eventPrefix: String,
        val stateMachineFactory: String,
        val additionalImports: List<String>,
        val targets: List<String>,
        val containingFile: com.google.devtools.ksp.symbol.KSFile?,
    )

    private fun OutputStream.writeText(value: String) {
        write(value.toByteArray())
        flush()
        close()
    }

    private companion object {
        const val ANNOTATION_FQN = "io.stateproof.annotations.StateProofStateMachine"
        const val STATE_MACHINE_FQN = "io.stateproof.StateMachine"
    }
}
