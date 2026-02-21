package io.stateproof.sample

sealed interface AppEvent : TaskAppViewEvent {
    data object OnAppStart : AppEvent
    data class OnLoginSubmit(val username: String, val password: String) : AppEvent
    data class OnSelectTask(val id: String) : AppEvent
    data object OnCreateTaskTap : AppEvent
    data class OnSaveTask(val title: String, val description: String) : AppEvent
    data class OnToggleTask(val id: String) : AppEvent
    data class OnDeleteTask(val id: String) : AppEvent
    data object OnSettingsTap : AppEvent
    data object OnLogout : AppEvent
    data object OnBack : AppEvent
    data object OnRetry : AppEvent

    data class OnAuthSuccess(val token: String) : AppEvent
    data class OnAuthFailed(val reason: String) : AppEvent
    data class OnTasksLoaded(val tasks: List<TaskItem>) : AppEvent
    data class OnTasksLoadFailed(val reason: String) : AppEvent
    data class OnTaskSaved(val task: TaskItem) : AppEvent
    data class OnTaskSaveFailed(val reason: String) : AppEvent
    data class OnTaskDeleted(val id: String) : AppEvent
    data class OnTaskToggled(val id: String, val completed: Boolean) : AppEvent
}
