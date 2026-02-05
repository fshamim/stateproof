package io.stateproof.cli

import io.stateproof.StateMachine
import io.stateproof.graph.StateInfo
import io.stateproof.graph.toStateInfo

/**
 * Loads state machine info via reflection from user's compiled code.
 *
 * Supports two provider formats:
 *
 * 1. **Top-level Kotlin function**: `com.package.FileKt#functionName`
 *    - For a function `getMainStateMachineInfo()` in file `MainStateMachine.kt`
 *      in package `com.example.main`, use:
 *      `com.example.main.MainStateMachineKt#getMainStateMachineInfo`
 *
 * 2. **Class static/companion method**: `com.package.ClassName#methodName`
 *    - For a companion object method `getInfo()` in class `MyStateMachine`, use:
 *      `com.example.MyStateMachine#getInfo`
 *
 * The provider function must:
 * - Take no parameters
 * - Return `Map<String, StateInfo>`
 */
object StateInfoLoader {

    /**
     * Loads state machine info from the given provider specification.
     *
     * @param providerFqn Fully qualified provider in format "ClassName#methodName"
     * @param classLoader ClassLoader to use for loading classes (defaults to thread context)
     * @return The state machine info map
     * @throws IllegalArgumentException if provider format is invalid
     * @throws RuntimeException if loading fails
     */
    fun load(
        providerFqn: String,
        classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
    ): Map<String, StateInfo> {
        val (className, methodName) = parseProvider(providerFqn)

        // Try loading the class
        val clazz = try {
            Class.forName(className, true, classLoader)
        } catch (e: ClassNotFoundException) {
            // For Kotlin top-level functions, try appending "Kt" if not already present
            if (!className.endsWith("Kt")) {
                try {
                    Class.forName("${className}Kt", true, classLoader)
                } catch (e2: ClassNotFoundException) {
                    throw RuntimeException(
                        "Could not find class '$className' or '${className}Kt'. " +
                            "For top-level Kotlin functions in file MyFile.kt, use: " +
                            "com.package.MyFileKt#functionName",
                        e2
                    )
                }
            } else {
                throw RuntimeException(
                    "Could not find class '$className'. " +
                        "Make sure the class is compiled and on the classpath.",
                    e
                )
            }
        }

        // Find the method
        val method = try {
            clazz.getMethod(methodName)
        } catch (e: NoSuchMethodException) {
            // Try companion object
            try {
                val companion = clazz.getDeclaredField("Companion").get(null)
                companion.javaClass.getMethod(methodName)
            } catch (e2: Exception) {
                throw RuntimeException(
                    "Could not find method '$methodName' on class '${clazz.name}'. " +
                        "The method must be public, take no parameters, and return Map<String, StateInfo>.",
                    e
                )
            }
        }

        // Invoke the method
        val result = try {
            if (java.lang.reflect.Modifier.isStatic(method.modifiers)) {
                method.invoke(null)
            } else {
                // For companion object methods
                val companion = try {
                    clazz.getDeclaredField("Companion").get(null)
                } catch (e: Exception) {
                    clazz.getDeclaredConstructor().newInstance()
                }
                method.invoke(companion)
            }
        } catch (e: Exception) {
            throw RuntimeException(
                "Failed to invoke '$methodName' on '${clazz.name}': ${e.message}",
                e
            )
        }

        // Validate and cast the result
        @Suppress("UNCHECKED_CAST")
        return when (result) {
            is Map<*, *> -> {
                // Validate that it's Map<String, StateInfo>
                val validated = mutableMapOf<String, StateInfo>()
                for ((key, value) in result) {
                    if (key !is String) {
                        throw RuntimeException(
                            "Provider returned Map with non-String keys. Expected Map<String, StateInfo>."
                        )
                    }
                    if (value !is StateInfo) {
                        throw RuntimeException(
                            "Provider returned Map with non-StateInfo values. " +
                                "Expected Map<String, StateInfo>, got value type: ${value?.javaClass?.name}"
                        )
                    }
                    validated[key] = value
                }
                validated
            }
            else -> throw RuntimeException(
                "Provider returned ${result?.javaClass?.name} instead of Map<String, StateInfo>."
            )
        }
    }

    /**
     * Parses a provider FQN into (className, methodName).
     *
     * @param providerFqn Format: "com.package.ClassName#methodName"
     * @return Pair of (className, methodName)
     */
    fun parseProvider(providerFqn: String): Pair<String, String> {
        val parts = providerFqn.split("#")
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw IllegalArgumentException(
                "Invalid provider format: '$providerFqn'. " +
                    "Expected format: 'com.package.ClassName#methodName' " +
                    "(e.g., 'com.example.MainStateMachineKt#getMainStateMachineInfo')"
            )
        }
        return parts[0] to parts[1]
    }

    /**
     * Loads state machine info by calling a factory function that returns a StateMachine.
     *
     * This method calls the factory, gets the StateMachine instance, and uses
     * `toStateInfo()` to extract the graph info automatically.
     *
     * This is the **preferred approach** as it eliminates the need for separate
     * *Info() functions that duplicate the state machine structure.
     *
     * @param factoryFqn Fully qualified factory function in format "ClassName#methodName"
     * @param classLoader ClassLoader to use for loading classes (defaults to thread context)
     * @return The state machine info map
     * @throws RuntimeException if loading fails
     */
    fun loadFromFactory(
        factoryFqn: String,
        classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
    ): Map<String, StateInfo> {
        val (className, methodName) = parseProvider(factoryFqn)

        // Load the class
        val clazz = try {
            Class.forName(className, true, classLoader)
        } catch (e: ClassNotFoundException) {
            // For Kotlin top-level functions, try appending "Kt" if not already present
            if (!className.endsWith("Kt")) {
                try {
                    Class.forName("${className}Kt", true, classLoader)
                } catch (e2: ClassNotFoundException) {
                    throw RuntimeException(
                        "Could not find class '$className' or '${className}Kt'. " +
                            "For top-level Kotlin functions in file MyFile.kt, use: " +
                            "com.package.MyFileKt#functionName",
                        e2
                    )
                }
            } else {
                throw RuntimeException(
                    "Could not find class '$className'. " +
                        "Make sure the class is compiled and on the classpath.",
                    e
                )
            }
        }

        // Find the method
        val method = try {
            clazz.getMethod(methodName)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException(
                "Could not find method '$methodName' on class '${clazz.name}'. " +
                    "The method must be public, take no parameters, and return StateMachine<*, *>.",
                e
            )
        }

        // Invoke the method
        val result = try {
            method.invoke(null)
        } catch (e: Exception) {
            throw RuntimeException(
                "Failed to invoke '$methodName' on '${clazz.name}': ${e.message}",
                e
            )
        }

        // Validate result is a StateMachine and extract info
        return when (result) {
            is StateMachine<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (result as StateMachine<Any, Any>).toStateInfo()
            }
            else -> throw RuntimeException(
                "Factory returned ${result?.javaClass?.name} instead of StateMachine<*, *>. " +
                    "The factory function must return a StateMachine instance."
            )
        }
    }
}

