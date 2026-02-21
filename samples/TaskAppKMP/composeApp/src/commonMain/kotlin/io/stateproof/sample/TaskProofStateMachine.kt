package io.stateproof.sample

import io.stateproof.StateMachine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

fun createTaskProofRuntime(
    authRepository: AuthRepository = FakeAuthRepository(),
    taskRepository: TaskRepository = FakeTaskRepository(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    stateData: MutableTaskAppStateData = MutableTaskAppStateData(),
    sideEffectsEnabled: Boolean = true,
): TaskProofRuntime {
    val stateMachine = buildTaskProofStateMachine(
        authRepository = authRepository,
        taskRepository = taskRepository,
        stateData = stateData,
        dispatcher = dispatcher,
        ioDispatcher = ioDispatcher,
        sideEffectsEnabled = sideEffectsEnabled,
    )
    return TaskProofRuntime(stateMachine = stateMachine, stateData = stateData)
}

fun createTaskProofStateMachine(
    authRepository: AuthRepository = FakeAuthRepository(),
    taskRepository: TaskRepository = FakeTaskRepository(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    stateData: MutableTaskAppStateData = MutableTaskAppStateData(),
): StateMachine<AppState, AppEvent> {
    return createTaskProofRuntime(
        authRepository = authRepository,
        taskRepository = taskRepository,
        dispatcher = dispatcher,
        ioDispatcher = ioDispatcher,
        stateData = stateData,
        sideEffectsEnabled = true,
    ).stateMachine
}

internal fun buildTaskProofStateMachine(
    authRepository: AuthRepository,
    taskRepository: TaskRepository,
    stateData: MutableTaskAppStateData,
    dispatcher: CoroutineDispatcher,
    ioDispatcher: CoroutineDispatcher,
    sideEffectsEnabled: Boolean,
): StateMachine<AppState, AppEvent> {
    return StateMachine(dispatcher = dispatcher, ioDispatcher = ioDispatcher) {
        initialState(AppState.Splash)

        state<AppState.Splash> {
            on<AppEvent.OnAppStart> { transitionTo(AppState.Login) }
            on<AppEvent.OnBack> { doNotTransition() }
        }

        state<AppState.Login> {
            on<AppEvent.OnLoginSubmit> {
                condition("credentials non-empty") { _, event ->
                    event.username.isNotBlank() && event.password.isNotBlank()
                } then {
                    transitionTo(AppState.Authenticating)
                    sideEffect { event ->
                        stateData._authErrorReason.value = null
                        if (!sideEffectsEnabled) return@sideEffect null
                        when (val result = authRepository.login(event.username, event.password)) {
                            is AuthResult.Success -> AppEvent.OnAuthSuccess(result.token)
                            is AuthResult.Failure -> AppEvent.OnAuthFailed(result.reason)
                        }
                    }
                    sideEffectEmits(
                        "auth_success" to AppEvent.OnAuthSuccess::class,
                        "auth_failed" to AppEvent.OnAuthFailed::class,
                    )
                }
                otherwise { doNotTransition() }
            }
            on<AppEvent.OnBack> { doNotTransition() }
        }

        state<AppState.Authenticating> {
            on<AppEvent.OnAuthSuccess> {
                transitionTo(AppState.LoadingTasks)
                sideEffect { event ->
                    stateData._authToken.value = event.token
                    stateData._authErrorReason.value = null
                    if (!sideEffectsEnabled) return@sideEffect null
                    when (val result = taskRepository.loadTasks(event.token)) {
                        is TasksLoadResult.Success -> AppEvent.OnTasksLoaded(result.tasks)
                        is TasksLoadResult.Failure -> AppEvent.OnTasksLoadFailed(result.reason)
                    }
                }
                sideEffectEmits(
                    "tasks_loaded" to AppEvent.OnTasksLoaded::class,
                    "tasks_load_failed" to AppEvent.OnTasksLoadFailed::class,
                )
            }
            on<AppEvent.OnAuthFailed> {
                transitionTo(AppState.AuthError)
                sideEffect { event ->
                    stateData._authErrorReason.value = event.reason
                    null
                }
            }
            on<AppEvent.OnBack> {
                transitionTo(AppState.Login)
                sideEffect {
                    stateData.clearSession()
                    null
                }
            }
        }

        state<AppState.LoadingTasks> {
            on<AppEvent.OnTasksLoaded> {
                transitionTo(AppState.TaskList)
                sideEffect { event ->
                    stateData.setTasks(event.tasks)
                    null
                }
            }
            on<AppEvent.OnTasksLoadFailed> {
                transitionTo(AppState.AuthError)
                sideEffect { event ->
                    stateData._authErrorReason.value = event.reason
                    null
                }
            }
            on<AppEvent.OnBack> {
                transitionTo(AppState.Login)
                sideEffect {
                    stateData.clearSession()
                    null
                }
            }
        }

        state<AppState.TaskList> {
            on<AppEvent.OnSelectTask> {
                condition("task exists") { _, event ->
                    stateData._tasks.value.any { it.id == event.id }
                } then {
                    transitionTo(AppState.TaskDetail)
                    sideEffect { event ->
                        stateData._selectedTaskId.value = event.id
                        null
                    }
                }
                otherwise { doNotTransition() }
            }

            on<AppEvent.OnCreateTaskTap> {
                transitionTo(AppState.CreateTask)
                sideEffect {
                    stateData._selectedTaskId.value = null
                    null
                }
            }

            on<AppEvent.OnToggleTask> {
                doNotTransition()
                sideEffect { event ->
                    if (!sideEffectsEnabled) return@sideEffect null
                    val token = stateData._authToken.value
                    when (val result = taskRepository.toggleTask(token, event.id)) {
                        is TaskToggleResult.Success -> AppEvent.OnTaskToggled(result.id, result.completed)
                        is TaskToggleResult.Failure -> AppEvent.OnTaskSaveFailed(result.reason)
                    }
                }
                sideEffectEmits(
                    "task_toggled" to AppEvent.OnTaskToggled::class,
                    "task_toggle_failed" to AppEvent.OnTaskSaveFailed::class,
                )
            }

            on<AppEvent.OnTaskToggled> {
                doNotTransition()
                sideEffect { event ->
                    stateData.setTasks(
                        stateData._tasks.value.map { task ->
                            if (task.id == event.id) task.copy(completed = event.completed) else task
                        }
                    )
                    null
                }
            }

            on<AppEvent.OnDeleteTask> {
                doNotTransition()
                sideEffect { event ->
                    if (!sideEffectsEnabled) return@sideEffect null
                    val token = stateData._authToken.value
                    when (val result = taskRepository.deleteTask(token, event.id)) {
                        is TaskDeleteResult.Success -> AppEvent.OnTaskDeleted(result.id)
                        is TaskDeleteResult.Failure -> AppEvent.OnTaskSaveFailed(result.reason)
                    }
                }
                sideEffectEmits(
                    "task_deleted" to AppEvent.OnTaskDeleted::class,
                    "task_delete_failed" to AppEvent.OnTaskSaveFailed::class,
                )
            }

            on<AppEvent.OnTaskDeleted> {
                doNotTransition()
                sideEffect { event ->
                    val filtered = stateData._tasks.value.filterNot { task -> task.id == event.id }
                    stateData.setTasks(filtered)
                    if (stateData._selectedTaskId.value == event.id) {
                        stateData._selectedTaskId.value = null
                    }
                    null
                }
            }

            on<AppEvent.OnTaskSaveFailed> {
                transitionTo(AppState.AuthError)
                sideEffect { event ->
                    stateData._authErrorReason.value = event.reason
                    null
                }
            }

            on<AppEvent.OnSettingsTap> { transitionTo(AppState.Settings) }
            on<AppEvent.OnLogout> {
                transitionTo(AppState.Login)
                sideEffect {
                    stateData.clearSession()
                    null
                }
            }
            on<AppEvent.OnBack> { doNotTransition() }
        }

        state<AppState.TaskDetail> {
            on<AppEvent.OnToggleTask> {
                doNotTransition()
                sideEffect { event ->
                    if (!sideEffectsEnabled) return@sideEffect null
                    val token = stateData._authToken.value
                    when (val result = taskRepository.toggleTask(token, event.id)) {
                        is TaskToggleResult.Success -> AppEvent.OnTaskToggled(result.id, result.completed)
                        is TaskToggleResult.Failure -> AppEvent.OnTaskSaveFailed(result.reason)
                    }
                }
                sideEffectEmits(
                    "task_toggled" to AppEvent.OnTaskToggled::class,
                    "task_toggle_failed" to AppEvent.OnTaskSaveFailed::class,
                )
            }

            on<AppEvent.OnTaskToggled> {
                doNotTransition()
                sideEffect { event ->
                    stateData.setTasks(
                        stateData._tasks.value.map { task ->
                            if (task.id == event.id) task.copy(completed = event.completed) else task
                        }
                    )
                    null
                }
            }

            on<AppEvent.OnDeleteTask> {
                doNotTransition()
                sideEffect { event ->
                    if (!sideEffectsEnabled) return@sideEffect null
                    val token = stateData._authToken.value
                    when (val result = taskRepository.deleteTask(token, event.id)) {
                        is TaskDeleteResult.Success -> AppEvent.OnTaskDeleted(result.id)
                        is TaskDeleteResult.Failure -> AppEvent.OnTaskSaveFailed(result.reason)
                    }
                }
                sideEffectEmits(
                    "task_deleted" to AppEvent.OnTaskDeleted::class,
                    "task_delete_failed" to AppEvent.OnTaskSaveFailed::class,
                )
            }

            on<AppEvent.OnTaskDeleted> {
                transitionTo(AppState.TaskList)
                sideEffect { event ->
                    stateData.setTasks(stateData._tasks.value.filterNot { task -> task.id == event.id })
                    stateData._selectedTaskId.value = null
                    null
                }
            }

            on<AppEvent.OnTaskSaveFailed> {
                transitionTo(AppState.AuthError)
                sideEffect { event ->
                    stateData._authErrorReason.value = event.reason
                    null
                }
            }

            on<AppEvent.OnBack> {
                transitionTo(AppState.TaskList)
                sideEffect {
                    stateData._selectedTaskId.value = null
                    null
                }
            }
        }

        state<AppState.CreateTask> {
            on<AppEvent.OnSaveTask> {
                condition("title non-empty") { _, event -> event.title.isNotBlank() } then {
                    transitionTo(AppState.SavingTask)
                    sideEffect { event ->
                        if (!sideEffectsEnabled) return@sideEffect null
                        val token = stateData._authToken.value
                        when (val result = taskRepository.createTask(token, event.title, event.description)) {
                            is TaskSaveResult.Success -> AppEvent.OnTaskSaved(result.task)
                            is TaskSaveResult.Failure -> AppEvent.OnTaskSaveFailed(result.reason)
                        }
                    }
                    sideEffectEmits(
                        "task_saved" to AppEvent.OnTaskSaved::class,
                        "task_save_failed" to AppEvent.OnTaskSaveFailed::class,
                    )
                }
                otherwise { doNotTransition() }
            }

            on<AppEvent.OnBack> { transitionTo(AppState.TaskList) }
        }

        state<AppState.SavingTask> {
            on<AppEvent.OnTaskSaved> {
                transitionTo(AppState.TaskList)
                sideEffect { event ->
                    val withoutPrevious = stateData._tasks.value.filterNot { it.id == event.task.id }
                    stateData.setTasks(withoutPrevious + event.task)
                    null
                }
            }

            on<AppEvent.OnTaskSaveFailed> {
                transitionTo(AppState.AuthError)
                sideEffect { event ->
                    stateData._authErrorReason.value = event.reason
                    null
                }
            }

            on<AppEvent.OnBack> { doNotTransition() }
        }

        state<AppState.Settings> {
            on<AppEvent.OnLogout> {
                transitionTo(AppState.Login)
                sideEffect {
                    stateData.clearSession()
                    null
                }
            }
            on<AppEvent.OnBack> { transitionTo(AppState.TaskList) }
        }

        state<AppState.AuthError> {
            on<AppEvent.OnRetry> {
                transitionTo(AppState.Login)
                sideEffect {
                    stateData.clearSession()
                    null
                }
            }
            on<AppEvent.OnBack> {
                transitionTo(AppState.Login)
                sideEffect {
                    stateData.clearSession()
                    null
                }
            }
        }
    }
}
