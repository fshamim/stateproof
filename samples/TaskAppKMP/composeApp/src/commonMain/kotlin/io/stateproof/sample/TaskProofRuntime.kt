package io.stateproof.sample

import io.stateproof.StateMachine

data class TaskProofRuntime(
    val stateMachine: StateMachine<AppState, AppEvent>,
    val stateData: MutableTaskAppStateData,
) {
    suspend fun awaitIdle() {
        stateMachine.awaitIdle()
    }

    fun close() {
        stateMachine.close()
    }
}
