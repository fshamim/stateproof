package io.stateproof.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import io.stateproof.StateMachine
import io.stateproof.compose.collectAsState

@Composable
actual fun <STATE : Any, EVENT : Any> StateMachine<STATE, EVENT>.collectAsComposeState(): State<STATE> {
    return collectAsState()
}
