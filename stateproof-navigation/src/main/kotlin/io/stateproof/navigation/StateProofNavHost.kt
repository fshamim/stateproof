package io.stateproof.navigation

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.stateproof.StateMachine
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "StateProofNav"
private const val ANIM_DURATION = 300

/**
 * StateProof-powered navigation host with opinionated backstack semantics.
 *
 * ## Navigation Model
 *
 * ```
 * [HOME]                           <- root, back = exit
 * [HOME, MAIN]                     <- main content, back = home
 * [HOME, DETAIL]                   <- detail from home, back = home
 * [HOME, MAIN, DETAIL]             <- detail from main, back = main
 * ```
 *
 * ## Screen Types
 *
 * - **SPLASH**: Transitional screens (not in backstack)
 * - **HOME**: Root screen, always at backstack bottom
 * - **MAIN**: Side menu content, swaps with other MAIN screens
 * - **DETAIL**: Stacks normally on top
 */
@Composable
fun <STATE : Any, EVENT : Any> StateProofNavHost(
    stateMachine: StateMachine<STATE, EVENT>,
    navController: NavHostController,
    onEvent: (EVENT) -> Unit = { stateMachine.onEvent(it) },
    builder: StateProofNavGraphBuilder<STATE, EVENT>.() -> Unit,
) {
    val graphBuilder = remember { StateProofNavGraphBuilder<STATE, EVENT>().apply(builder) }
    val routeMapper =
        remember { DefaultStateRouteMapper(graphBuilder.screens, graphBuilder.homeRoute) }
    val currentState by stateMachine.state.collectAsStateWithLifecycle()

    StateProofNavHostImpl(
        currentState = currentState,
        routeMapper = routeMapper,
        graphBuilder = graphBuilder,
        navController = navController,
        onBackEvent = graphBuilder.onBackEvent?.let { provider -> { onEvent(provider()) } },
    )
}

/**
 * Simplified version that takes a StateFlow directly.
 */
@Composable
fun <STATE : Any, EVENT : Any> StateProofNavHost(
    stateFlow: StateFlow<STATE>,
    navController: NavHostController,
    onBackEvent: (() -> Unit)? = null,
    builder: StateProofNavGraphBuilder<STATE, EVENT>.() -> Unit,
) {
    val graphBuilder = remember { StateProofNavGraphBuilder<STATE, EVENT>().apply(builder) }
    val routeMapper =
        remember { DefaultStateRouteMapper(graphBuilder.screens, graphBuilder.homeRoute) }
    val currentState by stateFlow.collectAsStateWithLifecycle()

    StateProofNavHostImpl(
        currentState = currentState,
        routeMapper = routeMapper,
        graphBuilder = graphBuilder,
        navController = navController,
        onBackEvent = onBackEvent,
    )
}

@Composable
private fun <STATE : Any, EVENT : Any> StateProofNavHostImpl(
    currentState: STATE,
    routeMapper: DefaultStateRouteMapper<STATE>,
    graphBuilder: StateProofNavGraphBuilder<STATE, EVENT>,
    @Suppress("UNUSED_PARAMETER") navController: NavHostController,
    onBackEvent: (() -> Unit)?,
) {
    // Track previous route for animation direction decisions
    var previousRoute by remember { mutableStateOf(routeMapper.getRoute(currentState)) }
    val backstack = remember { mutableListOf<String>() }

    val currentRoute = routeMapper.getRoute(currentState)
    val currentScreenType = routeMapper.getScreenType(currentRoute) ?: ScreenType.DETAIL

    Log.d(TAG, "State: $currentState -> route: $currentRoute (type: $currentScreenType)")

    // Determine if this is back navigation (going to a screen already in backstack)
    val isBackNavigation =
        backstack.contains(currentRoute) && backstack.lastOrNull() != currentRoute

    // Update backstack
    if (currentScreenType != ScreenType.SPLASH) {
        if (isBackNavigation) {
            // Pop backstack to the destination
            while (backstack.isNotEmpty() && backstack.last() != currentRoute) {
                backstack.removeLast()
            }
        } else if (!backstack.contains(currentRoute)) {
            // Forward navigation - handle MAIN swap
            if (currentScreenType == ScreenType.MAIN) {
                // Remove everything above HOME for MAIN swap
                while (backstack.size > 1) {
                    backstack.removeLast()
                }
            }
            backstack.add(currentRoute)
        }
    }

    Log.d(TAG, "Backstack: $backstack, isBack: $isBackNavigation")

    // Handle SPLASH screens: render directly without animation
    if (currentScreenType == ScreenType.SPLASH) {
        val splashConfig = graphBuilder.screens.find { it.route == currentRoute }
        if (splashConfig != null && splashConfig.stateClass.isInstance(currentState)) {
            splashConfig.content(currentState)
        }
        return
    }

    // Use AnimatedContent for smooth horizontal transitions
    AnimatedContent(
        targetState = currentState,
        transitionSpec = {
            val targetRoute = routeMapper.getRoute(targetState)
            val previousType = routeMapper.getScreenType(previousRoute) ?: ScreenType.DETAIL
            val targetType = routeMapper.getScreenType(targetRoute) ?: ScreenType.DETAIL
            Log.d(
                TAG,
                "AnimatedContent: ${routeMapper.getRoute(initialState)} -> $targetRoute, isBack=$isBackNavigation, currentScreenType=$currentScreenType, previousType=$previousType"
            )

            if (isBackNavigation) {
                // Back navigation: new screen slides in from left, old slides out to right
                if (currentScreenType == ScreenType.HOME && previousType == ScreenType.MAIN) {
                    // MAIN to Home swaps out from left.
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(ANIM_DURATION)
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(ANIM_DURATION)
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(ANIM_DURATION)
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(ANIM_DURATION)
                    )
                }
            } else {
                // Forward navigation: depends on screen type
                if (targetType == ScreenType.HOME || targetType == ScreenType.MAIN) {
                    // HOME/MAIN slides in from left
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(ANIM_DURATION)
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(ANIM_DURATION)
                    )
                } else {
                    // DETAIL slides in from right current slides out to left
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(ANIM_DURATION)
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(ANIM_DURATION)
                    )
                }
            }
        },
        label = "StateProofNavigation"
    ) { state ->
        // Find and render the matching screen
        val route = routeMapper.getRoute(state)
        val screenConfig = graphBuilder.screens.find { it.route == route }

        if (screenConfig != null && screenConfig.stateClass.isInstance(state)) {
            screenConfig.content(state)
        }
    }

    previousRoute = currentRoute

    // BackHandler for non-splash screens
    BackHandler(enabled = true) {
        Log.d(TAG, "BackHandler triggered, currentState: $currentState")
        onBackEvent?.invoke()
    }
}

