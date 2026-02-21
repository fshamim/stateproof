package io.stateproof.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import io.stateproof.StateMachine

@Composable
expect fun <STATE : Any, EVENT : Any> StateMachine<STATE, EVENT>.collectAsComposeState(): State<STATE>
