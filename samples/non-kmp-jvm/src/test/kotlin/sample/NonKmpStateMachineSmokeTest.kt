package sample

import kotlin.test.Test
import kotlin.test.assertEquals

class NonKmpStateMachineSmokeTest {

    @Test
    fun `initial state is idle`() {
        val machine = createSampleStateMachine()
        assertEquals(SampleState.Idle, machine.currentState)
    }
}
