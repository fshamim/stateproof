package io.stateproof.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry

/**
 * Pre-configured animation presets for StateProof navigation.
 *
 * Example:
 * ```kotlin
 * StateProofNavHost(...) {
 *     defaultAnimations(
 *         enter = StateProofAnimations.slideInFromRight,
 *         exit = StateProofAnimations.slideOutToLeft,
 *         popEnter = StateProofAnimations.slideInFromLeft,
 *         popExit = StateProofAnimations.slideOutToRight,
 *     )
 * }
 * ```
 */
object StateProofAnimations {

    private const val DEFAULT_DURATION_MS = 300

    // region Slide Horizontal

    /**
     * Slide in from the right side of the screen.
     */
    val slideInFromRight: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(DEFAULT_DURATION_MS)
        )
    }

    /**
     * Slide in from the left side of the screen.
     */
    val slideInFromLeft: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(DEFAULT_DURATION_MS)
        )
    }

    /**
     * Slide out to the left side of the screen.
     */
    val slideOutToLeft: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(DEFAULT_DURATION_MS)
        )
    }

    /**
     * Slide out to the right side of the screen.
     */
    val slideOutToRight: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(DEFAULT_DURATION_MS)
        )
    }

    // endregion

    // region Slide Vertical

    /**
     * Slide in from the bottom of the screen.
     */
    val slideInFromBottom: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Up,
            animationSpec = tween(DEFAULT_DURATION_MS)
        )
    }

    /**
     * Slide in from the top of the screen.
     */
    val slideInFromTop: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Down,
            animationSpec = tween(DEFAULT_DURATION_MS)
        )
    }

    /**
     * Slide out to the bottom of the screen.
     */
    val slideOutToBottom: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Down,
            animationSpec = tween(DEFAULT_DURATION_MS)
        )
    }

    /**
     * Slide out to the top of the screen.
     */
    val slideOutToTop: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Up,
            animationSpec = tween(DEFAULT_DURATION_MS)
        )
    }

    // endregion

    // region Fade

    /**
     * Fade in.
     */
    val fadeInTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(DEFAULT_DURATION_MS))
    }

    /**
     * Fade out.
     */
    val fadeOutTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(DEFAULT_DURATION_MS))
    }

    // endregion

    // region No Animation

    /**
     * No enter animation.
     */
    val none: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        EnterTransition.None
    }

    /**
     * No exit animation.
     */
    val noneExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        ExitTransition.None
    }

    // endregion

    // region Presets

    /**
     * Standard horizontal navigation preset (like iOS push/pop).
     */
    object HorizontalSlide {
        val enter = slideInFromRight
        val exit = slideOutToLeft
        val popEnter = slideInFromLeft
        val popExit = slideOutToRight
    }

    /**
     * Modal presentation preset (slide up from bottom).
     */
    object Modal {
        val enter = slideInFromBottom
        val exit = fadeOutTransition
        val popEnter = fadeInTransition
        val popExit = slideOutToBottom
    }

    /**
     * Fade transition preset.
     */
    object Fade {
        val enter = fadeInTransition
        val exit = fadeOutTransition
        val popEnter = fadeInTransition
        val popExit = fadeOutTransition
    }

    // endregion
}
