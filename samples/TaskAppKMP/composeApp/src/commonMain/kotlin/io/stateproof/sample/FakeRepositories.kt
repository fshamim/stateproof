package io.stateproof.sample

class FakeAuthRepository(
    private val validUsername: String = "demo",
    private val validPassword: String = "demo",
) : AuthRepository {
    override suspend fun login(username: String, password: String): AuthResult {
        return if (username == validUsername && password == validPassword) {
            AuthResult.Success(token = "taskproof-token")
        } else {
            AuthResult.Failure(reason = "Invalid username or password")
        }
    }
}

class FakeTaskRepository(
    seedTasks: List<TaskItem> = defaultSeedTasks(),
) : TaskRepository {
    private val tasks = seedTasks.toMutableList()
    private var nextId = seedTasks.size + 1

    override suspend fun loadTasks(token: String): TasksLoadResult {
        return if (token.isBlank()) {
            TasksLoadResult.Failure("Auth token is missing")
        } else {
            TasksLoadResult.Success(tasks.toList())
        }
    }

    override suspend fun createTask(token: String, title: String, description: String): TaskSaveResult {
        if (token.isBlank()) return TaskSaveResult.Failure("Auth token is missing")
        if (title.isBlank()) return TaskSaveResult.Failure("Title cannot be empty")

        val created = TaskItem(
            id = "task-$nextId",
            title = title,
            description = description,
            completed = false,
        )
        nextId += 1
        tasks += created
        return TaskSaveResult.Success(created)
    }

    override suspend fun toggleTask(token: String, id: String): TaskToggleResult {
        if (token.isBlank()) return TaskToggleResult.Failure("Auth token is missing")

        val index = tasks.indexOfFirst { it.id == id }
        if (index == -1) return TaskToggleResult.Failure("Task '$id' was not found")

        val updated = tasks[index].copy(completed = !tasks[index].completed)
        tasks[index] = updated
        return TaskToggleResult.Success(id = updated.id, completed = updated.completed)
    }

    override suspend fun deleteTask(token: String, id: String): TaskDeleteResult {
        if (token.isBlank()) return TaskDeleteResult.Failure("Auth token is missing")

        val removed = tasks.removeAll { it.id == id }
        return if (removed) {
            TaskDeleteResult.Success(id)
        } else {
            TaskDeleteResult.Failure("Task '$id' was not found")
        }
    }
}

fun defaultSeedTasks(): List<TaskItem> = listOf(
    TaskItem(id = "task-1", title = "Review PR", description = "Validate state transitions", completed = false),
    TaskItem(id = "task-2", title = "Update docs", description = "Add viewer workflow notes", completed = true),
)
