package io.stateproof.sample

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MutableTaskAppStateData(
    val _authToken: MutableStateFlow<String> = MutableStateFlow(""),
    val _authErrorReason: MutableStateFlow<String?> = MutableStateFlow(null),
    val _tasks: MutableStateFlow<List<TaskItem>> = MutableStateFlow(emptyList()),
    val _selectedTaskId: MutableStateFlow<String?> = MutableStateFlow(null),
) {
    fun clearSession() {
        _authToken.value = ""
        _authErrorReason.value = null
        _tasks.value = emptyList()
        _selectedTaskId.value = null
    }

    fun setTasks(tasks: List<TaskItem>) {
        _tasks.value = tasks.toList()
        val selectedId = _selectedTaskId.value
        if (selectedId != null && tasks.none { it.id == selectedId }) {
            _selectedTaskId.value = null
        }
    }

    fun selectedTaskOrNull(): TaskItem? {
        val selectedId = _selectedTaskId.value ?: return null
        return _tasks.value.firstOrNull { it.id == selectedId }
    }
}

data class TaskAppViewModelState(
    val authToken: StateFlow<String>,
    val authErrorReason: StateFlow<String?>,
    val tasks: StateFlow<List<TaskItem>>,
    val selectedTaskId: StateFlow<String?>,
    val selectedTask: StateFlow<TaskItem?>,
)

data class TaskAppReadonlyStateData(
    val authToken: StateFlow<String>,
    val authErrorReason: StateFlow<String?>,
    val tasks: StateFlow<List<TaskItem>>,
    val selectedTaskId: StateFlow<String?>,
)

fun MutableTaskAppStateData.asReadonlyStateData(): TaskAppReadonlyStateData {
    return TaskAppReadonlyStateData(
        authToken = _authToken.asStateFlow(),
        authErrorReason = _authErrorReason.asStateFlow(),
        tasks = _tasks.asStateFlow(),
        selectedTaskId = _selectedTaskId.asStateFlow(),
    )
}
