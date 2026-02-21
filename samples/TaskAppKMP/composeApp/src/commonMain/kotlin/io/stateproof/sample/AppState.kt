package io.stateproof.sample

sealed interface AppState {
    data object Splash : AppState
    data object Login : AppState
    data object Authenticating : AppState
    data object AuthError : AppState
    data object LoadingTasks : AppState
    data object TaskList : AppState
    data object TaskDetail : AppState
    data object CreateTask : AppState
    data object SavingTask : AppState
    data object Settings : AppState
}
