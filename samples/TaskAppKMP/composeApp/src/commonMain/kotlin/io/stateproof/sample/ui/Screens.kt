package io.stateproof.sample.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.stateproof.sample.TaskItem

@Composable
fun SplashScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TaskProof", style = MaterialTheme.typography.headlineMedium)
        Text("StateProof KMP sample", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onStart, modifier = Modifier.padding(top = 16.dp)) {
            Text("Start")
        }
    }
}

@Composable
fun LoginScreen(
    onSubmit: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var username by remember { mutableStateOf("demo") }
    var password by remember { mutableStateOf("demo") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Login", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSubmit(username, password) }) {
                Text("Login")
            }
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun ErrorScreen(
    reason: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Error", style = MaterialTheme.typography.headlineSmall)
        Text(reason)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) { Text("Retry") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
fun TaskListScreen(
    tasks: List<TaskItem>,
    onSelectTask: (String) -> Unit,
    onCreateTask: () -> Unit,
    onToggleTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tasks", style = MaterialTheme.typography.headlineSmall)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onCreateTask) { Text("Create") }
            Button(onClick = onSettings) { Text("Settings") }
            Button(onClick = onLogout) { Text("Logout") }
            Button(onClick = onBack) { Text("Back") }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tasks, key = { it.id }) { task ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTask(task.id) }
                        .padding(vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.title, fontWeight = FontWeight.SemiBold)
                            Text(task.description, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Checkbox(
                                checked = task.completed,
                                onCheckedChange = { onToggleTask(task.id) },
                            )
                            Button(onClick = { onDeleteTask(task.id) }) {
                                Text("Delete")
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
fun TaskDetailScreen(
    task: TaskItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Task detail", style = MaterialTheme.typography.headlineSmall)
        Text("ID: ${task.id}")
        Text("Title: ${task.title}")
        Text("Description: ${task.description}")
        Text("Completed: ${task.completed}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onToggle) { Text("Toggle") }
            Button(onClick = onDelete) { Text("Delete") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
fun CreateTaskScreen(
    onSave: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Create task", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(title, description) }) { Text("Save") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("This screen demonstrates MAIN screen mapping in StateProofNavHost.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onLogout) { Text("Logout") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}
