package io.stateproof.sample

import io.stateproof.StateMachine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

fun createTaskProofStateMachineForIntrospection(): StateMachine<AppState, AppEvent> {
    return createTaskProofRuntimeForIntrospection().stateMachine
}

fun createTaskProofRuntimeForIntrospection(): TaskProofRuntime {
    val seededTasks = defaultSeedTasks()
    val stateData = MutableTaskAppStateData(
        _authToken = MutableStateFlow("taskproof-token"),
        _authErrorReason = MutableStateFlow(null),
        _tasks = MutableStateFlow(seededTasks),
        _selectedTaskId = MutableStateFlow(seededTasks.firstOrNull()?.id),
    )
    val authRepository = FakeAuthRepository()
    val taskRepository = FakeTaskRepository(seedTasks = seededTasks)

    return createTaskProofRuntime(
        authRepository = authRepository,
        taskRepository = taskRepository,
        stateData = stateData,
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
        sideEffectsEnabled = false,
    )
}
