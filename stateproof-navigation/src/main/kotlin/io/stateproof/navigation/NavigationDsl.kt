package io.stateproof.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import kotlin.reflect.KClass

/**
 * Defines the type of screen for navigation and animation purposes.
 *
 * StateProof uses an opinionated navigation model with hierarchical screen depths:
 *
 * ```
 * Depth -1: SPLASH    (transitional, not in backstack)
 * Depth 0:  HOME      (root, always at bottom)
 * Depth 1:  MAIN      (side menu content, swaps with other MAIN)
 * Depth 2+: DETAIL    (drill deeper, stacks normally)
 * ```
 *
 * ## Navigation Behavior
 *
 * - **SPLASH** screens are transitional and don't participate in backstack
 * - **HOME** is the root - back from HOME triggers exit handling
 * - **MAIN** screens swap with each other (only one MAIN at a time above HOME)
 * - **DETAIL** screens stack normally on top of HOME/MAIN
 *
 * ## Animation Behavior
 *
 * - **SPLASH**: No animations (instant)
 * - **HOME**: Slides from LEFT
 * - **MAIN**: Slides from LEFT (swaps with other MAIN)
 * - **DETAIL**: Slides from RIGHT
 * - **Back**: Current slides RIGHT, previous slides from LEFT
 */
enum class ScreenType {
    /**
     * Splash/loading screens - transitional, not in backstack.
     * These screens are replaced when state changes, never stacked.
     * Examples: Initial, CheckProjects, Importing, Exporting
     */
    SPLASH,

    /**
     * The home/root screen - always at the bottom of the backstack.
     * Back from HOME triggers the exit callback (app should handle exit).
     * There should be exactly ONE home screen.
     * Example: AppStarted (Project Overview)
     */
    HOME,

    /**
     * Main content screens accessible from side/bottom menu.
     * These screens swap with each other - only one MAIN at a time above HOME.
     * Back from MAIN returns to HOME (not previous MAIN).
     * Examples: Settings, Profile (if accessed from menu)
     */
    MAIN,

    /**
     * Detail screens that drill deeper into content.
     * These stack normally on top of whatever is below.
     * Back pops to previous screen.
     * Examples: LoadPoint, Measurement, Diagnostics, legal pages
     */
    DETAIL,
}

/**
 * Configuration for a screen in the StateProof navigation graph.
 */
data class ScreenConfig<STATE : Any>(
    val stateClass: KClass<out STATE>,
    val route: String,
    val screenType: ScreenType,
    val content: @Composable (STATE) -> Unit,
    val enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    val exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    val popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    val popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
)

/**
 * Builder for configuring StateProof navigation with opinionated backstack semantics.
 *
 * ## Navigation Model
 *
 * StateProof enforces a hierarchical navigation model:
 *
 * ```
 * [HOME]                           <- root, back = exit
 * [HOME, MAIN]                     <- main content, back = home
 * [HOME, DETAIL]                   <- detail from home, back = home
 * [HOME, MAIN, DETAIL]             <- detail from main, back = main
 * [HOME, MAIN, DETAIL, DETAIL]     <- nested detail, back = detail
 * ```
 *
 * ## Screen Registration
 *
 * - `splashScreen<S>` - Transitional screens (not in backstack)
 * - `homeScreen<S>` - The root screen (exactly one required)
 * - `mainScreen<S>` - Side menu content (swaps with other main screens)
 * - `detailScreen<S>` - Detail screens (stacks normally)
 *
 * ## Example
 *
 * ```kotlin
 * StateProofNavHost(
 *     stateFlow = stateFlow,
 *     navController = navController,
 * ) {
 *     // Splash screens - transitional, no backstack
 *     splashScreen<States.Initial> { SplashScreen() }
 *     splashScreen<States.CheckProjects> { SplashScreen() }
 *
 *     // Home screen - root of backstack
 *     homeScreen<States.AppStarted> { HomeScreen() }
 *
 *     // Main content - swaps, accessible from side menu
 *     mainScreen<States.Settings> { SettingsScreen() }
 *
 *     // Detail screens - stacks on top
 *     detailScreen<States.LoadPoint> { LoadPointScreen() }
 *     detailScreen<States.Measurement> { MeasurementScreen() }
 *
 *     onBack { Events.OnBack }
 * }
 * ```
 */
class StateProofNavGraphBuilder<STATE : Any, EVENT : Any> {

    @PublishedApi
    internal val screens = mutableListOf<ScreenConfig<STATE>>()

    @PublishedApi
    internal var onBackEvent: (() -> EVENT)? = null

    @PublishedApi
    internal var homeRoute: String? = null

    /**
     * Register a splash/loading screen - transitional, not in backstack.
     *
     * Splash screens are replaced when state changes. They don't participate
     * in navigation history or animations.
     */
    inline fun <reified S : STATE> splashScreen(
        route: String = S::class.simpleName ?: "unknown",
        noinline content: @Composable (S) -> Unit,
    ) {
        addScreen(
            stateClass = S::class,
            route = route,
            screenType = ScreenType.SPLASH,
            content = content,
        )
    }

    /**
     * Register the home screen - the root of the backstack.
     *
     * There should be exactly ONE home screen. Back from home triggers
     * the exit callback (the app should handle showing exit dialog or finishing).
     *
     * The home screen is always at the bottom of the navigation stack.
     */
    inline fun <reified S : STATE> homeScreen(
        route: String = S::class.simpleName ?: "unknown",
        noinline enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
        noinline exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
        noinline popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
        noinline popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
        noinline content: @Composable (S) -> Unit,
    ) {
        homeRoute = route
        addScreen(
            stateClass = S::class,
            route = route,
            screenType = ScreenType.HOME,
            content = content,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition,
        )
    }

    /**
     * Register a main content screen accessible from side/bottom menu.
     *
     * Main screens "swap" with each other - only one MAIN at a time above HOME.
     * When navigating from one MAIN to another, the first is replaced, not stacked.
     * Back from MAIN returns to HOME.
     */
    inline fun <reified S : STATE> mainScreen(
        route: String = S::class.simpleName ?: "unknown",
        noinline enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
        noinline exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
        noinline popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
        noinline popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
        noinline content: @Composable (S) -> Unit,
    ) {
        addScreen(
            stateClass = S::class,
            route = route,
            screenType = ScreenType.MAIN,
            content = content,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition,
        )
    }

    /**
     * Register a detail screen that drills deeper from main content.
     *
     * Detail screens stack normally on top of whatever is below (HOME, MAIN, or DETAIL).
     * Back pops to the previous screen.
     */
    inline fun <reified S : STATE> detailScreen(
        route: String = S::class.simpleName ?: "unknown",
        noinline enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
        noinline exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
        noinline popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
        noinline popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
        noinline content: @Composable (S) -> Unit,
    ) {
        addScreen(
            stateClass = S::class,
            route = route,
            screenType = ScreenType.DETAIL,
            content = content,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition,
        )
    }

    /**
     * Register a screen with explicit screen type and optional custom animations.
     */
    inline fun <reified S : STATE> screen(
        route: String = S::class.simpleName ?: "unknown",
        screenType: ScreenType = ScreenType.DETAIL,
        noinline enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
        noinline exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
        noinline popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
        noinline popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
        noinline content: @Composable (S) -> Unit,
    ) {
        if (screenType == ScreenType.HOME) {
            homeRoute = route
        }
        addScreen(
            stateClass = S::class,
            route = route,
            screenType = screenType,
            content = content,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition,
        )
    }

    @PublishedApi
    internal inline fun <reified S : STATE> addScreen(
        stateClass: KClass<S>,
        route: String,
        screenType: ScreenType,
        noinline content: @Composable (S) -> Unit,
        noinline enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
        noinline exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
        noinline popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
        noinline popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    ) {
        val wrappedContent: @Composable (STATE) -> Unit = { state: STATE ->
            @Suppress("UNCHECKED_CAST")
            content(state as S)
        }
        screens.add(
            ScreenConfig(
                stateClass = stateClass,
                route = route,
                screenType = screenType,
                content = wrappedContent,
                enterTransition = enterTransition,
                exitTransition = exitTransition,
                popEnterTransition = popEnterTransition,
                popExitTransition = popExitTransition,
            )
        )
    }

    /**
     * Configure the back event to dispatch when the system back is pressed.
     */
    fun onBack(eventProvider: () -> EVENT) {
        onBackEvent = eventProvider
    }
}

/**
 * Mapping from state to route and screen type.
 */
interface StateRouteMapper<STATE : Any> {
    fun getRoute(state: STATE): String
    fun getStateClass(route: String): KClass<out STATE>?
    fun getScreenType(route: String): ScreenType?
    fun getHomeRoute(): String?
}

/**
 * Default implementation that uses state class simple name as route.
 */
class DefaultStateRouteMapper<STATE : Any>(
    private val screens: List<ScreenConfig<STATE>>,
    private val homeRoute: String?,
) : StateRouteMapper<STATE> {

    private val routeToStateClass: Map<String, KClass<out STATE>> =
        screens.associate { it.route to it.stateClass }

    private val stateClassToRoute: Map<KClass<out STATE>, String> =
        screens.associate { it.stateClass to it.route }

    private val routeToScreenType: Map<String, ScreenType> =
        screens.associate { it.route to it.screenType }

    override fun getRoute(state: STATE): String {
        return stateClassToRoute[state::class]
            ?: state::class.simpleName
            ?: "unknown"
    }

    override fun getStateClass(route: String): KClass<out STATE>? {
        return routeToStateClass[route]
    }

    override fun getScreenType(route: String): ScreenType? {
        return routeToScreenType[route]
    }

    override fun getHomeRoute(): String? = homeRoute
}
