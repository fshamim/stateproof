package io.stateproof

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import arrow.core.right
import io.stateproof.dsl.SMGraphBuilder
import io.stateproof.graph.SMGraph
import io.stateproof.graph.StateInfo
import io.stateproof.graph.Transition
import io.stateproof.logging.Logger
import io.stateproof.logging.NoOpLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

/**
 * StateProof - A Kotlin Multiplatform state machine with exhaustive test generation.
 *
 * The state graph IS your test suite. StateProof's DFS traversal algorithm enumerates
 * every valid path through your state machine, and each path becomes a test case.
 *
 * @param STATE The sealed class/interface representing all possible states
 * @param EVENT The sealed class/interface representing all possible events
 * @param graph The state machine graph definition
 * @param dispatcher The coroutine dispatcher for state machine processing
 * @param ioDispatcher The dispatcher for side effects (typically IO-bound)
 * @param logger Logger implementation for debugging
 */
class StateMachine<STATE : Any, EVENT : Any>(
    private val graph: SMGraph<STATE, EVENT>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val logger: Logger = NoOpLogger,
) : Logger by logger {

    private val coroutineScope = CoroutineScope(dispatcher + Job())

    /**
     * Errors that can occur during state machine operation.
     */
    sealed class SMErrors {
        data class NoTransitionForCurrentState<STATE, EVENT>(
            val state: STATE,
            val event: EVENT
        ) : SMErrors()
    }

    // Event queue with mutex for thread-safe access
    private val eventQueueMutex = Mutex()
    private val eventQueue = ArrayDeque<EVENT>()

    // Channel for processing events sequentially
    private val eventChannel = Channel<EVENT>(capacity = Channel.UNLIMITED)

    // KMP-compatible idle detection using CompletableDeferred
    private var idleDeferred = CompletableDeferred<Unit>()

    // State as StateFlow (KMP-compatible replacement for Compose State)
    private val _state = MutableStateFlow(graph.initialState)

    /**
     * The current state as a StateFlow.
     * Collect this to observe state changes.
     */
    val state: StateFlow<STATE> = _state.asStateFlow()

    /**
     * The current state value.
     */
    val currentState: STATE
        get() = _state.value

    init {
        // Start the event processor
        coroutineScope.launch {
            processEventQueue()
        }
    }

    private fun STATE.getDefinition() = graph.stateDefinition
        .filter { it.key.matches(this) }
        .map { it.value }
        .firstOrNull() ?: error("Invalid State Definition: $this")

    private fun STATE.getTransition(event: EVENT): Either<Unit, Transition<STATE, EVENT>> {
        for ((eventMatcher, createTransitionTo) in getDefinition().transitions) {
            if (eventMatcher.matches(event)) {
                val (toState, sideEffect) = createTransitionTo(this, event)
                return Transition(event, toState, sideEffect).right()
            }
        }
        return Unit.left()
    }

    private suspend fun processEventQueue() {
        for (event in eventChannel) {
            val eventName = event::class.simpleName
            "_START : pendingEvent: $eventName".log("QProcessor")

            val eventToProcess = eventQueueMutex.withLock {
                eventQueue.removeFirstOrNull()
            }

            if (eventToProcess != null) {
                "PROCESSING EVENT : ${eventToProcess::class.simpleName}".log("QProcessor")
                processEvent(eventToProcess).fold(
                    { "ERROR: $it ".log("QProcessor") },
                    { }
                )
                "_END : EVENT PROCESSED: $event".log("QProcessor")
            }

            // Check if queue is empty and signal idle
            val isEmpty = eventQueueMutex.withLock { eventQueue.isEmpty() }
            if (isEmpty && !idleDeferred.isCompleted) {
                idleDeferred.complete(Unit)
            }
        }
    }

    private suspend fun addInFrontOfEventQueue(event: EVENT) {
        eventQueueMutex.withLock {
            eventQueue.addFirst(event)
        }
        eventChannel.send(event)
    }

    private suspend fun <S : STATE, E : EVENT> processTransition(transition: Transition<S, E>) {
        val tag = "TProcessor"
        val eventName = transition.event::class.simpleName
        val targetStateName = transition.targetState::class.simpleName
        val sideEffectName = transition.sideEffect.toString()
        "_START: processTransition($eventName -> $targetStateName ".log(tag)

        val currentState = _state.value
        val stateName = currentState::class.simpleName
        "From State: $stateName on $eventName".log(tag)

        if (_state.value != transition.targetState) {
            _state.value = transition.targetState
        } else {
            "Didn't transition due to same targetState: $stateName == $targetStateName".log(tag)
        }

        val newStateName = _state.value::class.simpleName
        val stateEffect = transition.sideEffect
        "Executing sideEffect ${sideEffectName::class.simpleName}".log(tag)

        val postSideEffectEvent = withContext(ioDispatcher) {
            stateEffect?.invoke(transition.targetState, transition.event)
        }

        "POST SideEffect returned Event: $postSideEffectEvent".log(tag)
        postSideEffectEvent?.let { addInFrontOfEventQueue(it) }
        "_END: processTransition in currentState: $newStateName".log(tag)
        logTransition(stateName, eventName, newStateName)
    }

    private suspend fun processEvent(event: EVENT): Either<SMErrors, Unit> =
        either {
            val tag = "QProcessor"
            val eventName = event::class.simpleName
            "_START : onProcessEvent($eventName)".log(tag)

            val currentState = _state.value
            val stateName = currentState::class.simpleName
            "Validating Event $stateName -> $eventName".log(tag)

            val stateTransition = currentState.getTransition(event).getOrNull()
            ensureNotNull(stateTransition) {
                SMErrors.NoTransitionForCurrentState(currentState, event)
            }

            val targetStateName = stateTransition.targetState::class.simpleName
            "Transition FOUND: from $stateName to $targetStateName on $eventName".log(tag)

            processTransition(stateTransition)
            "_END : onProcessEvent -> Event PROCESSED $eventName".log(tag)
        }

    /**
     * Sends an event to the state machine for processing.
     *
     * Events are processed sequentially in the order they are received.
     * Side effects may generate follow-up events that are processed immediately.
     *
     * @param event The event to process
     */
    fun onEvent(event: EVENT) {
        "_START onEvent(${event::class.simpleName})".log()

        // Reset idle deferred for new event processing
        if (idleDeferred.isCompleted) {
            idleDeferred = CompletableDeferred()
        }

        coroutineScope.launch {
            eventQueueMutex.withLock {
                eventQueue.add(event)
            }
            eventChannel.send(event)
        }

        "_END onEvent(${event::class.simpleName})".log()
    }

    /**
     * Suspends until the state machine has processed all pending events.
     *
     * This is the KMP-compatible replacement for CountDownLatch.await().
     * Useful in tests to wait for async event processing to complete.
     */
    suspend fun awaitIdle() {
        idleDeferred.await()
    }

    /**
     * Closes the state machine and cancels all coroutines.
     *
     * After calling close(), the state machine cannot process any more events.
     */
    fun close() {
        eventChannel.close()
        coroutineScope.cancel()
    }

    // region Transition logging
    private val transitionLog = mutableListOf<String>()

    private fun logTransition(currentState: String?, eventName: String?, targetState: String?) {
        transitionLog.add("${currentState}_${eventName}_${targetState}")
    }

    /**
     * Returns the log of all transitions that have occurred.
     *
     * Each entry is formatted as "FromState_Event_ToState".
     * Useful for testing and debugging.
     */
    fun getTransitionLog(): List<String> = transitionLog.toList()

    /**
     * Clears the transition log.
     */
    fun clearTransitionLog() {
        transitionLog.clear()
    }
    // endregion

    // region Test Generation

    /**
     * Returns the underlying state machine graph for introspection.
     */
    fun getGraph(): SMGraph<STATE, EVENT> = graph

    // endregion

    companion object {
        /**
         * Creates a state machine using the DSL builder.
         *
         * Example:
         * ```kotlin
         * val sm = StateMachine<MyState, MyEvent>(
         *     dispatcher = Dispatchers.Default,
         *     logger = PrintLogger()
         * ) {
         *     initialState(MyState.Initial)
         *
         *     state<MyState.Initial> {
         *         on<MyEvent.Start> { transitionTo(MyState.Running) }
         *     }
         * }
         * ```
         */
        operator fun <STATE : Any, EVENT : Any> invoke(
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
            ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
            logger: Logger = NoOpLogger,
            init: SMGraphBuilder<STATE, EVENT>.() -> Unit,
        ): StateMachine<STATE, EVENT> {
            val graph = SMGraphBuilder<STATE, EVENT>(null).apply(init).build()
            return StateMachine(graph, dispatcher, ioDispatcher, logger)
        }

        /**
         * Generates a PlantUML diagram from state machine info.
         *
         * @param stateMachineInfo Map of state names to their info
         * @return PlantUML diagram source code
         */
        fun generatePlantUML(stateMachineInfo: Map<String, StateInfo>): String {
            val builder = StringBuilder()
            builder.append("@startuml\n")

            stateMachineInfo.onEachIndexed { index, (_, stateInfo) ->
                if (index == 0) {
                    builder.append("[*] --> ${stateInfo.stateName} \n")
                }
                builder.append("state ${stateInfo.stateName} : ${stateInfo.sideEffect}\n")
                stateInfo.transitions.forEach { (event, toState) ->
                    builder.append("${stateInfo.stateName} --> $toState : $event\n")
                }
            }

            builder.append("@enduml")
            return builder.toString()
        }

        /**
         * Generates Kotlin state machine DSL code from state machine info.
         *
         * @param stateMachineInfo Map of state names to their info
         * @param stateSealedClassName Name of the sealed class for states
         * @param eventSealedClassName Name of the sealed class for events
         * @return Kotlin DSL code string
         */
        fun generateKotlinSM(
            stateMachineInfo: Map<String, StateInfo>,
            stateSealedClassName: String,
            eventSealedClassName: String,
        ): String {
            val builder = StringBuilder()

            stateMachineInfo.onEachIndexed { index, (state, stateInfo) ->
                if (index == 0) {
                    builder.append("initialState($state)\n")
                }
                builder.append("state<$stateSealedClassName.$state>{\n")
                stateInfo.transitions.forEach { (event, toState) ->
                    builder.append("\ton<$eventSealedClassName.$event>{\n")
                    builder.append("\t\ttransitionTo($stateSealedClassName.$toState)")
                    if (stateInfo.sideEffect.isNotEmpty()) {
                        builder.append("{\n")
                        builder.append("\t\t\t${stateInfo.sideEffect}\n")
                        builder.append("\t\t}\n")
                    } else {
                        builder.append("\n")
                    }
                    builder.append("\t}\n")
                }
                builder.append("}\n")
            }
            return builder.toString()
        }
    }
}
