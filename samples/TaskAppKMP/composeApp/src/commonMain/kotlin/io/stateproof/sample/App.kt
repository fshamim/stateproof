package io.stateproof.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.stateproof.sample.ui.CreateTaskScreen
import io.stateproof.sample.ui.ErrorScreen
import io.stateproof.sample.ui.LoadingScreen
import io.stateproof.sample.ui.LoginScreen
import io.stateproof.sample.ui.SettingsScreen
import io.stateproof.sample.ui.SplashScreen
import io.stateproof.sample.ui.TaskDetailScreen
import io.stateproof.sample.ui.TaskListScreen

@Composable
fun App(viewModel: TaskAppViewModel) {
    val state by viewModel.state.collectAsState()
    val tasks by viewModel.stateData.tasks.collectAsState()
    val selectedTask by viewModel.stateData.selectedTask.collectAsState()
    val authErrorReason by viewModel.stateData.authErrorReason.collectAsState()

    MaterialTheme {
        Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
            when (state) {
                AppState.Splash -> {
                    LaunchedEffect(Unit) {
                        viewModel.onEvents(AppEvent.OnAppStart)
                    }
                    SplashScreen(onStart = { viewModel.onEvents(AppEvent.OnAppStart) })
                }

                AppState.Login -> {
                    LoginScreen(
                        onSubmit = { user, password ->
                            viewModel.onEvents(AppEvent.OnLoginSubmit(user, password))
                        },
                        onBack = { viewModel.onEvents(AppEvent.OnBack) },
                    )
                }

                AppState.Authenticating -> LoadingScreen("Authenticating")
                AppState.LoadingTasks -> LoadingScreen("Loading tasks")
                AppState.SavingTask -> LoadingScreen("Saving task")

                AppState.AuthError -> {
                    ErrorScreen(
                        reason = authErrorReason ?: "Something went wrong",
                        onRetry = {
                            viewModel.onEvents(LocalUiEvent.OnClearAuthError)
                            viewModel.onEvents(AppEvent.OnRetry)
                        },
                        onBack = {
                            viewModel.onEvents(LocalUiEvent.OnClearAuthError)
                            viewModel.onEvents(AppEvent.OnBack)
                        },
                    )
                }

                AppState.TaskList -> {
                    TaskListScreen(
                        tasks = tasks,
                        onSelectTask = { id -> viewModel.onEvents(AppEvent.OnSelectTask(id)) },
                        onCreateTask = { viewModel.onEvents(AppEvent.OnCreateTaskTap) },
                        onToggleTask = { id -> viewModel.onEvents(AppEvent.OnToggleTask(id)) },
                        onDeleteTask = { id -> viewModel.onEvents(AppEvent.OnDeleteTask(id)) },
                        onSettings = { viewModel.onEvents(AppEvent.OnSettingsTap) },
                        onLogout = { viewModel.onEvents(AppEvent.OnLogout) },
                        onBack = { viewModel.onEvents(AppEvent.OnBack) },
                    )
                }

                AppState.TaskDetail -> {
                    val task = selectedTask
                    if (task == null) {
                        LaunchedEffect(state) {
                            viewModel.onEvents(LocalUiEvent.OnClearTaskSelection)
                            viewModel.onEvents(AppEvent.OnBack)
                        }
                        LoadingScreen("Loading task")
                    } else {
                        TaskDetailScreen(
                            task = task,
                            onToggle = { viewModel.onEvents(AppEvent.OnToggleTask(task.id)) },
                            onDelete = { viewModel.onEvents(AppEvent.OnDeleteTask(task.id)) },
                            onBack = { viewModel.onEvents(AppEvent.OnBack) },
                        )
                    }
                }

                AppState.CreateTask -> {
                    CreateTaskScreen(
                        onSave = { title, description ->
                            viewModel.onEvents(AppEvent.OnSaveTask(title, description))
                        },
                        onBack = { viewModel.onEvents(AppEvent.OnBack) },
                    )
                }

                AppState.Settings -> {
                    SettingsScreen(
                        onLogout = { viewModel.onEvents(AppEvent.OnLogout) },
                        onBack = { viewModel.onEvents(AppEvent.OnBack) },
                    )
                }
            }
        }
    }
}
