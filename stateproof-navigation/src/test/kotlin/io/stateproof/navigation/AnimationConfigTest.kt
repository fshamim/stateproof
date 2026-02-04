package io.stateproof.navigation

import org.junit.Assert.assertNotNull
import org.junit.Test

class AnimationConfigTest {

    @Test
    fun `slideInFromRight is defined`() {
        assertNotNull(StateProofAnimations.slideInFromRight)
    }

    @Test
    fun `slideInFromLeft is defined`() {
        assertNotNull(StateProofAnimations.slideInFromLeft)
    }

    @Test
    fun `slideOutToLeft is defined`() {
        assertNotNull(StateProofAnimations.slideOutToLeft)
    }

    @Test
    fun `slideOutToRight is defined`() {
        assertNotNull(StateProofAnimations.slideOutToRight)
    }

    @Test
    fun `slideInFromBottom is defined`() {
        assertNotNull(StateProofAnimations.slideInFromBottom)
    }

    @Test
    fun `slideInFromTop is defined`() {
        assertNotNull(StateProofAnimations.slideInFromTop)
    }

    @Test
    fun `slideOutToBottom is defined`() {
        assertNotNull(StateProofAnimations.slideOutToBottom)
    }

    @Test
    fun `slideOutToTop is defined`() {
        assertNotNull(StateProofAnimations.slideOutToTop)
    }

    @Test
    fun `fadeInTransition is defined`() {
        assertNotNull(StateProofAnimations.fadeInTransition)
    }

    @Test
    fun `fadeOutTransition is defined`() {
        assertNotNull(StateProofAnimations.fadeOutTransition)
    }

    @Test
    fun `none transition is defined`() {
        assertNotNull(StateProofAnimations.none)
    }

    @Test
    fun `noneExit transition is defined`() {
        assertNotNull(StateProofAnimations.noneExit)
    }

    @Test
    fun `HorizontalSlide preset has all transitions`() {
        assertNotNull(StateProofAnimations.HorizontalSlide.enter)
        assertNotNull(StateProofAnimations.HorizontalSlide.exit)
        assertNotNull(StateProofAnimations.HorizontalSlide.popEnter)
        assertNotNull(StateProofAnimations.HorizontalSlide.popExit)
    }

    @Test
    fun `Modal preset has all transitions`() {
        assertNotNull(StateProofAnimations.Modal.enter)
        assertNotNull(StateProofAnimations.Modal.exit)
        assertNotNull(StateProofAnimations.Modal.popEnter)
        assertNotNull(StateProofAnimations.Modal.popExit)
    }

    @Test
    fun `Fade preset has all transitions`() {
        assertNotNull(StateProofAnimations.Fade.enter)
        assertNotNull(StateProofAnimations.Fade.exit)
        assertNotNull(StateProofAnimations.Fade.popEnter)
        assertNotNull(StateProofAnimations.Fade.popExit)
    }
}
