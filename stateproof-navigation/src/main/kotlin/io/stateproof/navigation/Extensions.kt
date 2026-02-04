package io.stateproof.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.stateproof.StateMachine
import kotlinx.coroutines.flow.StateFlow

/**
 * Collects the state machine's state as a Compose State with lifecycle awareness.
 *
 * Example:
 * ```kotlin
 * @Composable
 * fun MyScreen(stateMachine: StateMachine<MyState, MyEvent>) {
 *     val state by stateMachine.collectAsState()
 *
 *     when (state) {
 *         is MyState.Loading -> LoadingIndicator()
 *         is MyState.Loaded -> Content(state.data)
 *     }
 * }
 * ```
 */
@Composable
fun <STATE : Any, EVENT : Any> StateMachine<STATE, EVENT>.collectAsState(): State<STATE> {
    return state.collectAsStateWithLifecycle()
}

/**
 * Gets the current route for a state using the default route mapper.
 */
fun <STATE : Any> STATE.toRoute(): String {
    return this::class.simpleName ?: "unknown"
}

/**
 * Extension to simplify event dispatching in Compose callbacks.
 *
 * Example:
 * ```kotlin
 * Button(onClick = stateMachine.dispatchEvent { Events.OnClick }) {
 *     Text("Click me")
 * }
 * ```
 */
fun <STATE : Any, EVENT : Any> StateMachine<STATE, EVENT>.dispatchEvent(
    eventProvider: () -> EVENT
): () -> Unit = {
    onEvent(eventProvider())
}

/**
 * Extension to simplify event dispatching with a parameter.
 *
 * Example:
 * ```kotlin
 * LazyColumn {
 *     items(items) { item ->
 *         ItemRow(
 *             item = item,
 *             onClick = stateMachine.dispatchEvent { Events.OnItemClick(item.id) }
 *         )
 *     }
 * }
 * ```
 */
fun <STATE : Any, EVENT : Any, T> StateMachine<STATE, EVENT>.dispatchEventWith(
    eventProvider: (T) -> EVENT
): (T) -> Unit = { param ->
    onEvent(eventProvider(param))
}
