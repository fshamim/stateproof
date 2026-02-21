package io.stateproof.sample

interface AuthRepository {
    suspend fun login(username: String, password: String): AuthResult
}

sealed interface AuthResult {
    data class Success(val token: String) : AuthResult
    data class Failure(val reason: String) : AuthResult
}

interface TaskRepository {
    suspend fun loadTasks(token: String): TasksLoadResult
    suspend fun createTask(token: String, title: String, description: String): TaskSaveResult
    suspend fun toggleTask(token: String, id: String): TaskToggleResult
    suspend fun deleteTask(token: String, id: String): TaskDeleteResult
}

sealed interface TasksLoadResult {
    data class Success(val tasks: List<TaskItem>) : TasksLoadResult
    data class Failure(val reason: String) : TasksLoadResult
}

sealed interface TaskSaveResult {
    data class Success(val task: TaskItem) : TaskSaveResult
    data class Failure(val reason: String) : TaskSaveResult
}

sealed interface TaskToggleResult {
    data class Success(val id: String, val completed: Boolean) : TaskToggleResult
    data class Failure(val reason: String) : TaskToggleResult
}

sealed interface TaskDeleteResult {
    data class Success(val id: String) : TaskDeleteResult
    data class Failure(val reason: String) : TaskDeleteResult
}
