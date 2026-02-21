package io.stateproof.sample

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class TaskAppViewModel internal constructor(
    private val runtime: TaskProofRuntime,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val readonlyStateData = runtime.stateData.asReadonlyStateData()

    val state: StateFlow<AppState> = runtime.stateMachine.state

    val stateData: TaskAppViewModelState by lazy {
        val selectedTask = combine(
            readonlyStateData.tasks,
            readonlyStateData.selectedTaskId,
        ) { tasks, selectedId ->
            if (selectedId == null) {
                null
            } else {
                tasks.firstOrNull { it.id == selectedId }
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = runtime.stateData.selectedTaskOrNull(),
        )

        TaskAppViewModelState(
            authToken = readonlyStateData.authToken,
            authErrorReason = readonlyStateData.authErrorReason,
            tasks = readonlyStateData.tasks,
            selectedTaskId = readonlyStateData.selectedTaskId,
            selectedTask = selectedTask,
        )
    }

    fun onEvents(event: TaskAppViewEvent) {
        when (event) {
            is LocalUiEvent -> handleLocalUiEvent(event)
            is AppEvent -> runtime.stateMachine.onEvent(event)
        }
    }

    suspend fun awaitIdle() {
        runtime.awaitIdle()
    }

    fun close() {
        scope.cancel()
        runtime.close()
    }

    private fun handleLocalUiEvent(event: LocalUiEvent) {
        when (event) {
            LocalUiEvent.OnClearAuthError -> runtime.stateData._authErrorReason.value = null
            LocalUiEvent.OnClearTaskSelection -> runtime.stateData._selectedTaskId.value = null
        }
    }
}

fun createTaskAppViewModel(
    authRepository: AuthRepository = FakeAuthRepository(),
    taskRepository: TaskRepository = FakeTaskRepository(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    stateData: MutableTaskAppStateData = MutableTaskAppStateData(),
    sideEffectsEnabled: Boolean = true,
): TaskAppViewModel {
    val runtime = createTaskProofRuntime(
        authRepository = authRepository,
        taskRepository = taskRepository,
        dispatcher = dispatcher,
        ioDispatcher = ioDispatcher,
        stateData = stateData,
        sideEffectsEnabled = sideEffectsEnabled,
    )
    return TaskAppViewModel(runtime = runtime, dispatcher = dispatcher)
}
