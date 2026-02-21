package io.stateproof.cli

import io.stateproof.graph.StateInfo

internal data class EventInvocationResolution(
    val eventInvocationByName: Map<String, String>,
    val manualReviewReasonsByEvent: Map<String, List<String>>,
)

internal object EventInvocationResolver {

    fun resolve(
        providerFqn: String,
        eventClassPrefix: String,
        additionalImports: List<String>,
        stateInfoMap: Map<String, StateInfo>,
        classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
    ): EventInvocationResolution {
        val eventNames = collectEventNames(stateInfoMap)
        val reasonsByEvent = mutableMapOf<String, MutableSet<String>>()

        fun addReason(eventName: String, reason: String) {
            reasonsByEvent.getOrPut(eventName) { linkedSetOf() }.add(reason)
        }

        // Guarded or emitted-event transitions require runtime-aware harnessing.
        for (stateInfo in stateInfoMap.values) {
            for (detail in stateInfo.transitionDetails) {
                if (detail.guardLabel != null) {
                    addReason(
                        detail.eventName,
                        "guarded transition '${detail.guardLabel}' needs explicit test setup",
                    )
                }
                if (detail.emittedEvents.isNotEmpty()) {
                    addReason(
                        detail.eventName,
                        "side effects emit follow-up events; use runtime-aware helper",
                    )
                }
            }
        }

        val rootClassResult = resolveEventRootClass(
            providerFqn = providerFqn,
            eventClassPrefix = eventClassPrefix,
            additionalImports = additionalImports,
            classLoader = classLoader,
        )

        val eventInvocationByName = mutableMapOf<String, String>()
        if (rootClassResult.rootClass == null) {
            val fallbackReason = rootClassResult.failureReason
                ?: "unable to resolve event root class '$eventClassPrefix'"
            for (eventName in eventNames) {
                addReason(eventName, fallbackReason)
            }
        } else {
            for (eventName in eventNames) {
                val nestedEventClass = rootClassResult.rootClass.declaredClasses
                    .firstOrNull { it.simpleName == eventName }

                if (nestedEventClass == null) {
                    addReason(
                        eventName,
                        "event class '$eventName' not found under '${rootClassResult.rootClass.name}'",
                    )
                    continue
                }

                when {
                    isKotlinObject(nestedEventClass) -> {
                        eventInvocationByName[eventName] = "$eventClassPrefix.$eventName"
                    }

                    hasPublicNoArgConstructor(nestedEventClass) -> {
                        eventInvocationByName[eventName] = "$eventClassPrefix.$eventName()"
                    }

                    else -> {
                        addReason(eventName, "constructor requires arguments")
                    }
                }
            }
        }

        return EventInvocationResolution(
            eventInvocationByName = eventInvocationByName,
            manualReviewReasonsByEvent = reasonsByEvent.mapValues { it.value.toList() },
        )
    }

    private data class EventRootClassResult(
        val rootClass: Class<*>?,
        val failureReason: String?,
    )

    private fun resolveEventRootClass(
        providerFqn: String,
        eventClassPrefix: String,
        additionalImports: List<String>,
        classLoader: ClassLoader,
    ): EventRootClassResult {
        if (eventClassPrefix.isBlank()) {
            return EventRootClassResult(
                rootClass = null,
                failureReason = "event class prefix is blank",
            )
        }

        val candidates = linkedSetOf<String>()
        if (eventClassPrefix.contains('.')) {
            candidates += eventClassPrefix
        } else {
            candidates += eventClassPrefix
            for (importPath in additionalImports.map { it.trim() }.filter { it.isNotBlank() }) {
                if (importPath.endsWith(".$eventClassPrefix")) {
                    candidates += importPath
                } else if (importPath.endsWith(".*")) {
                    val pkg = importPath.removeSuffix(".*")
                    candidates += "$pkg.$eventClassPrefix"
                }
            }

            val providerClass = providerFqn.substringBefore('#')
            val providerPkg = providerClass.substringBeforeLast('.', "")
            if (providerPkg.isNotBlank()) {
                candidates += "$providerPkg.$eventClassPrefix"
            }
        }

        for (candidate in candidates) {
            val loaded = try {
                Class.forName(candidate, false, classLoader)
            } catch (_: ClassNotFoundException) {
                null
            }
            if (loaded != null) {
                return EventRootClassResult(rootClass = loaded, failureReason = null)
            }
        }

        val reason = "unable to load event root class '$eventClassPrefix' " +
            "(tried: ${candidates.joinToString(", ")})"
        return EventRootClassResult(rootClass = null, failureReason = reason)
    }

    private fun collectEventNames(stateInfoMap: Map<String, StateInfo>): Set<String> {
        val eventNames = linkedSetOf<String>()
        for (stateInfo in stateInfoMap.values) {
            eventNames += stateInfo.transitions.keys
            for (detail in stateInfo.transitionDetails) {
                eventNames += detail.eventName
            }
        }
        return eventNames
    }

    private fun isKotlinObject(clazz: Class<*>): Boolean {
        return try {
            val instanceField = clazz.getField("INSTANCE")
            instanceField.type == clazz
        } catch (_: Exception) {
            false
        }
    }

    private fun hasPublicNoArgConstructor(clazz: Class<*>): Boolean {
        return clazz.constructors.any { it.parameterCount == 0 }
    }
}
