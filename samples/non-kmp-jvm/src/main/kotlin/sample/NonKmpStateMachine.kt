package sample

import io.stateproof.StateMachine
import kotlinx.coroutines.Dispatchers

sealed class SampleState {
    data object Idle : SampleState()
    data object Loading : SampleState()
    data object Success : SampleState()
    data object Failure : SampleState()
}

sealed class SampleEvent {
    data object Start : SampleEvent()
    data object OnLoaded : SampleEvent()
    data object OnFailed : SampleEvent()
    data object Retry : SampleEvent()
    data object Reset : SampleEvent()
}

fun createSampleStateMachineForIntrospection(): StateMachine<SampleState, SampleEvent> {
    return createSampleStateMachine()
}

fun createSampleStateMachine(): StateMachine<SampleState, SampleEvent> {
    return StateMachine(
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
    ) {
        initialState(SampleState.Idle)

        state<SampleState.Idle> {
            on<SampleEvent.Start> { transitionTo(SampleState.Loading) }
        }

        state<SampleState.Loading> {
            on<SampleEvent.OnLoaded> { transitionTo(SampleState.Success) }
            on<SampleEvent.OnFailed> { transitionTo(SampleState.Failure) }
        }

        state<SampleState.Success> {
            on<SampleEvent.Reset> { transitionTo(SampleState.Idle) }
        }

        state<SampleState.Failure> {
            on<SampleEvent.Retry> { transitionTo(SampleState.Loading) }
        }
    }
}
