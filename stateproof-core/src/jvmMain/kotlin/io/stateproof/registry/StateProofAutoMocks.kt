package io.stateproof.registry

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.reflect.KClass

/**
 * Provides simple default instances for KSP-generated introspection factories.
 *
 * Uses sensible defaults for primitives and common Kotlin types, then falls back
 * to relaxed MockK mocks when MockK is available on the classpath.
 */
object StateProofAutoMocks {

    inline fun <reified T : Any> provide(): T = provide(T::class) as T

    fun provide(kclass: KClass<*>): Any {
        defaultForPrimitive(kclass)?.let { return it }

        if (isKotlinFunction(kclass.java)) {
            return emptyFunction(kclass.java)
        }

        if (CoroutineDispatcher::class.java.isAssignableFrom(kclass.java)) {
            return Dispatchers.Unconfined
        }
        if (CoroutineScope::class.java.isAssignableFrom(kclass.java)) {
            return CoroutineScope(Dispatchers.Unconfined)
        }

        if (List::class.java.isAssignableFrom(kclass.java)) return emptyList<Any>()
        if (Set::class.java.isAssignableFrom(kclass.java)) return emptySet<Any>()
        if (Map::class.java.isAssignableFrom(kclass.java)) return emptyMap<Any, Any>()

        if (kclass.java.isEnum) {
            val constants = kclass.java.enumConstants
            if (constants != null && constants.isNotEmpty()) {
                return constants[0] as Any
            }
        }

        kclass.objectInstance?.let { return it }

        try {
            return kclass.java.getDeclaredConstructor().newInstance()
        } catch (_: Exception) {
            // Fall through to MockK
        }

        tryMockk(kclass)?.let { return it }

        tryDefaultConstructorWithMarker(kclass)?.let { return it }

        if (kclass.java.isInterface) {
            return interfaceProxy(kclass.java)
        }

        tryUnsafeAllocate(kclass)?.let { return it }

        throw IllegalStateException(
            "StateProofAutoMocks could not create instance for ${kclass.qualifiedName}. " +
                "Add a no-arg constructor or include MockK on the test runtime classpath."
        )
    }

    private fun defaultForPrimitive(kclass: KClass<*>): Any? = when (kclass) {
        Boolean::class -> false
        Byte::class -> 0.toByte()
        Short::class -> 0.toShort()
        Int::class -> 0
        Long::class -> 0L
        Float::class -> 0f
        Double::class -> 0.0
        Char::class -> '\u0000'
        String::class -> ""
        else -> null
    }

    private fun tryMockk(kclass: KClass<*>): Any? {
        val mockkKt = try {
            Class.forName("io.mockk.MockKKt")
        } catch (_: Exception) {
            return null
        }

        val candidates = mockkKt.methods.filter { it.name == "mockkClass" || it.name == "mockk" }
        for (method in candidates) {
            val paramTypes = method.parameterTypes
            if (paramTypes.isEmpty()) continue

            val first = paramTypes[0].name
            if (first != "kotlin.reflect.KClass" && first != "java.lang.Class") continue

            val args = paramTypes.map { type ->
                when (type.name) {
                    "kotlin.reflect.KClass" -> kclass
                    "java.lang.Class" -> kclass.java
                    "java.lang.String" -> null
                    "boolean", "java.lang.Boolean" -> true
                    else -> when {
                        type.isArray && type.componentType.name == "kotlin.reflect.KClass" -> {
                            java.lang.reflect.Array.newInstance(type.componentType, 0)
                        }
                        type.isArray && type.componentType.name == "java.lang.Class" -> {
                            java.lang.reflect.Array.newInstance(type.componentType, 0)
                        }
                        type.name.startsWith("kotlin.jvm.functions.Function") -> emptyFunction(type)
                        else -> null
                    }
                }
            }.toTypedArray()

            try {
                return method.invoke(null, *args)
            } catch (_: Exception) {
                // Try next overload
            }
        }

        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun emptyFunction(type: Class<*>): Any = when (type.name) {
        "kotlin.jvm.functions.Function0" -> ({} as Function0<Unit>)
        "kotlin.jvm.functions.Function1" -> ({ _: Any? -> Unit } as Function1<Any?, Unit>)
        "kotlin.jvm.functions.Function2" -> ({ _: Any?, _: Any? -> Unit } as Function2<Any?, Any?, Unit>)
        "kotlin.jvm.functions.Function3" -> ({ _: Any?, _: Any?, _: Any? -> Unit } as Function3<Any?, Any?, Any?, Unit>)
        "kotlin.jvm.functions.Function4" -> ({ _: Any?, _: Any?, _: Any?, _: Any? -> Unit } as Function4<Any?, Any?, Any?, Any?, Unit>)
        "kotlin.jvm.functions.Function5" -> ({ _: Any?, _: Any?, _: Any?, _: Any?, _: Any? -> Unit } as Function5<Any?, Any?, Any?, Any?, Any?, Unit>)
        else -> ({} as Function0<Unit>)
    }

    private fun isKotlinFunction(type: Class<*>): Boolean =
        type.name.startsWith("kotlin.jvm.functions.Function")

    private fun interfaceProxy(type: Class<*>): Any {
        val loader = type.classLoader
        return java.lang.reflect.Proxy.newProxyInstance(loader, arrayOf(type)) { _, method, _ ->
            defaultReturnValue(method.returnType)
        }
    }

    private fun defaultReturnValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        java.lang.Void.TYPE -> null
        java.lang.String::class.java -> ""
        kotlin.Unit::class.java -> kotlin.Unit
        else -> null
    }

    private fun tryDefaultConstructorWithMarker(kclass: KClass<*>): Any? {
        val ctor = kclass.java.declaredConstructors.firstOrNull { constructor ->
            val params = constructor.parameterTypes
            params.isNotEmpty() && params.last().name == "kotlin.jvm.internal.DefaultConstructorMarker"
        } ?: return null

        val paramTypes = ctor.parameterTypes
        var maskCount = 0
        var index = paramTypes.size - 2
        while (index >= 0 && paramTypes[index] == Integer.TYPE) {
            maskCount += 1
            index -= 1
        }

        val originalCount = paramTypes.size - maskCount - 1
        if (originalCount < 0) return null

        val args = arrayOfNulls<Any>(paramTypes.size)
        for (i in 0 until originalCount) {
            args[i] = defaultReturnValue(paramTypes[i])
        }
        for (i in 0 until maskCount) {
            args[originalCount + i] = -1
        }
        args[paramTypes.size - 1] = null

        return try {
            ctor.isAccessible = true
            ctor.newInstance(*args)
        } catch (_: Exception) {
            null
        }
    }

    private fun tryUnsafeAllocate(kclass: KClass<*>): Any? {
        return try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val field = unsafeClass.getDeclaredField("theUnsafe")
            field.isAccessible = true
            val unsafe = field.get(null)
            val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
            allocate.invoke(unsafe, kclass.java)
        } catch (_: Exception) {
            null
        }
    }
}
