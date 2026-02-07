package io.stateproof.registry

import java.util.ServiceLoader

/**
 * Descriptor for a discoverable state machine.
 */
data class StateMachineDescriptor(
    val name: String,
    val baseName: String,
    val packageName: String,
    val factoryFqn: String,
    val eventClassName: String,
    val eventClassFqn: String,
    val testPackage: String = "",
    val testClassName: String = "",
    val eventPrefix: String = "",
    val stateMachineFactory: String = "",
    val additionalImports: List<String> = emptyList(),
    val targets: List<String> = listOf("jvm", "android"),
)

/**
 * Service interface for KSP-generated state machine registries.
 */
interface StateMachineRegistry {
    fun getStateMachines(): List<StateMachineDescriptor>
}

/**
 * Loads all registries using ServiceLoader.
 */
object StateMachineRegistryLoader {
    fun loadAll(classLoader: ClassLoader = Thread.currentThread().contextClassLoader): List<StateMachineDescriptor> {
        val loader = ServiceLoader.load(StateMachineRegistry::class.java, classLoader)
        val descriptors = mutableListOf<StateMachineDescriptor>()
        for (registry in loader) {
            descriptors.addAll(registry.getStateMachines())
        }
        return descriptors
    }
}
