package io.stateproof.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import io.stateproof.StateMachine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Collects the state machine's state as Compose State.
 *
 * This is a convenience extension that collects the underlying StateFlow
 * and converts it to Compose State for use in @Composable functions.
 *
 * Example:
 * ```kotlin
 * @Composable
 * fun MyScreen(stateMachine: StateMachine<MyState, MyEvent>) {
 *     val state by stateMachine.collectAsState()
 *
 *     when (state) {
 *         is MyState.Loading -> LoadingScreen()
 *         is MyState.Content -> ContentScreen(state.data)
 *         is MyState.Error -> ErrorScreen(state.message)
 *     }
 * }
 * ```
 *
 * @param context The coroutine context to use for collection.
 *                Defaults to EmptyCoroutineContext.
 * @return A Compose State that updates when the state machine's state changes.
 */
@Composable
fun <STATE : Any, EVENT : Any> StateMachine<STATE, EVENT>.collectAsState(
    context: CoroutineContext = EmptyCoroutineContext
): State<STATE> = state.collectAsState(context = context)

/**
 * Collects the state machine's state as Compose State with an initial value.
 *
 * This overload is useful when you want to provide an explicit initial value
 * rather than using the state machine's current state.
 *
 * @param initial The initial value to use before the first state is collected.
 * @param context The coroutine context to use for collection.
 * @return A Compose State that updates when the state machine's state changes.
 */
@Composable
fun <STATE : Any, EVENT : Any> StateMachine<STATE, EVENT>.collectAsState(
    initial: STATE,
    context: CoroutineContext = EmptyCoroutineContext
): State<STATE> = state.collectAsState(initial = initial, context = context)
