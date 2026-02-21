package io.stateproof.sample

sealed interface TaskAppViewEvent

sealed interface LocalUiEvent : TaskAppViewEvent {
    data object OnClearAuthError : LocalUiEvent
    data object OnClearTaskSelection : LocalUiEvent
}
