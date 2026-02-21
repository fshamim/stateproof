package io.stateproof.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import io.stateproof.navigation.StateProofAnimations
import io.stateproof.navigation.StateProofNavHost
import io.stateproof.sample.ui.CreateTaskScreen
import io.stateproof.sample.ui.ErrorScreen
import io.stateproof.sample.ui.LoadingScreen
import io.stateproof.sample.ui.LoginScreen
import io.stateproof.sample.ui.SettingsScreen
import io.stateproof.sample.ui.SplashScreen
import io.stateproof.sample.ui.TaskDetailScreen
import io.stateproof.sample.ui.TaskListScreen

@Composable
fun AndroidNavApp(viewModel: TaskAppViewModel) {
    val navController = rememberNavController()

    StateProofNavHost<AppState, AppEvent>(
        stateFlow = viewModel.state,
        navController = navController,
        onBackEvent = { viewModel.onEvents(AppEvent.OnBack) },
    ) {
        splashScreen<AppState.Splash> {
            SplashScreen(onStart = { viewModel.onEvents(AppEvent.OnAppStart) })
        }

        splashScreen<AppState.Authenticating> {
            LoadingScreen("Authenticating")
        }

        splashScreen<AppState.LoadingTasks> {
            LoadingScreen("Loading tasks")
        }

        splashScreen<AppState.SavingTask> {
            LoadingScreen("Saving task")
        }

        splashScreen<AppState.Login> {
            LoginScreen(
                onSubmit = { user, password ->
                    viewModel.onEvents(AppEvent.OnLoginSubmit(user, password))
                },
                onBack = { viewModel.onEvents(AppEvent.OnBack) },
            )
        }

        homeScreen<AppState.TaskList>(
            enterTransition = StateProofAnimations.slideInFromLeft,
            exitTransition = StateProofAnimations.slideOutToLeft,
            popEnterTransition = StateProofAnimations.slideInFromLeft,
            popExitTransition = StateProofAnimations.slideOutToRight,
        ) { _ ->
            val tasks by viewModel.stateData.tasks.collectAsState()
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

        mainScreen<AppState.Settings>(
            enterTransition = StateProofAnimations.slideInFromLeft,
            exitTransition = StateProofAnimations.slideOutToLeft,
            popEnterTransition = StateProofAnimations.slideInFromLeft,
            popExitTransition = StateProofAnimations.slideOutToRight,
        ) { _ ->
            SettingsScreen(
                onLogout = { viewModel.onEvents(AppEvent.OnLogout) },
                onBack = { viewModel.onEvents(AppEvent.OnBack) },
            )
        }

        detailScreen<AppState.TaskDetail>(
            enterTransition = StateProofAnimations.slideInFromRight,
            exitTransition = StateProofAnimations.slideOutToLeft,
            popEnterTransition = StateProofAnimations.slideInFromLeft,
            popExitTransition = StateProofAnimations.slideOutToRight,
        ) { _ ->
            val selectedTask by viewModel.stateData.selectedTask.collectAsState()
            val task = selectedTask
            if (task == null) {
                LaunchedEffect(Unit) {
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

        detailScreen<AppState.CreateTask>(
            enterTransition = StateProofAnimations.slideInFromRight,
            exitTransition = StateProofAnimations.slideOutToLeft,
            popEnterTransition = StateProofAnimations.slideInFromLeft,
            popExitTransition = StateProofAnimations.slideOutToRight,
        ) { _ ->
            CreateTaskScreen(
                onSave = { title, description ->
                    viewModel.onEvents(AppEvent.OnSaveTask(title, description))
                },
                onBack = { viewModel.onEvents(AppEvent.OnBack) },
            )
        }

        detailScreen<AppState.AuthError>(
            enterTransition = StateProofAnimations.fadeInTransition,
            exitTransition = StateProofAnimations.fadeOutTransition,
            popEnterTransition = StateProofAnimations.fadeInTransition,
            popExitTransition = StateProofAnimations.fadeOutTransition,
        ) { _ ->
            val reason by viewModel.stateData.authErrorReason.collectAsState()
            ErrorScreen(
                reason = reason ?: "Something went wrong",
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
    }
}
