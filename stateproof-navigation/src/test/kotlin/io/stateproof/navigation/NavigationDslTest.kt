package io.stateproof.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for Navigation DSL.
 *
 * Note: Tests involving @Composable lambdas are in androidTest since they
 * require the Compose runtime. These tests focus on non-Compose logic.
 */
class NavigationDslTest {

    // Test state hierarchy
    sealed class TestStates {
        object Initial : TestStates()
        object Home : TestStates()
        object Settings : TestStates()
        object Profile : TestStates()
        data class Detail(val id: String) : TestStates()
    }

    sealed class TestEvents {
        object OnBack : TestEvents()
        object OnNavigateHome : TestEvents()
        data class OnNavigateDetail(val id: String) : TestEvents()
    }

    @Test
    fun `onBack registers back event provider`() {
        val builder = StateProofNavGraphBuilder<TestStates, TestEvents>()

        builder.onBack { TestEvents.OnBack }

        assertNotNull(builder.onBackEvent)
        assertEquals(TestEvents.OnBack, builder.onBackEvent?.invoke())
    }

    @Test
    fun `ScreenType enum has correct values`() {
        assertEquals(4, ScreenType.values().size)
        assertNotNull(ScreenType.SPLASH)
        assertNotNull(ScreenType.HOME)
        assertNotNull(ScreenType.MAIN)
        assertNotNull(ScreenType.DETAIL)
    }
}

/**
 * Tests for route mapping logic.
 */
class RouteMapperLogicTest {

    sealed class States {
        object Home : States()
        object Settings : States()
        object Profile : States()
    }

    @Test
    fun `state toRoute returns simple class name for object`() {
        assertEquals("Home", States.Home.toRoute())
        assertEquals("Settings", States.Settings.toRoute())
        assertEquals("Profile", States.Profile.toRoute())
    }

    @Test
    fun `DefaultStateRouteMapper returns correct screen types`() {
        val screens = listOf(
            ScreenConfig<States>(
                stateClass = States.Home::class,
                route = "home",
                screenType = ScreenType.HOME,
                content = {},
            ),
            ScreenConfig<States>(
                stateClass = States.Settings::class,
                route = "settings",
                screenType = ScreenType.MAIN,
                content = {},
            ),
            ScreenConfig<States>(
                stateClass = States.Profile::class,
                route = "profile",
                screenType = ScreenType.DETAIL,
                content = {},
            ),
        )

        val mapper = DefaultStateRouteMapper(screens, "home")

        assertEquals(ScreenType.HOME, mapper.getScreenType("home"))
        assertEquals(ScreenType.MAIN, mapper.getScreenType("settings"))
        assertEquals(ScreenType.DETAIL, mapper.getScreenType("profile"))
        assertNull(mapper.getScreenType("unknown"))
        assertEquals("home", mapper.getHomeRoute())
    }
}

class StateRouteExtensionsTest {

    sealed class States {
        object Home : States()
        object Settings : States()
        data class Detail(val id: String) : States()
    }

    @Test
    fun `toRoute returns simple class name`() {
        assertEquals("Home", States.Home.toRoute())
        assertEquals("Settings", States.Settings.toRoute())
    }

    @Test
    fun `toRoute works with data class states`() {
        val detail = States.Detail("123")
        assertEquals("Detail", detail.toRoute())
    }
}

class ScreenTypeTest {

    @Test
    fun `SPLASH screen type exists`() {
        assertEquals("SPLASH", ScreenType.SPLASH.name)
    }

    @Test
    fun `HOME screen type exists`() {
        assertEquals("HOME", ScreenType.HOME.name)
    }

    @Test
    fun `MAIN screen type exists`() {
        assertEquals("MAIN", ScreenType.MAIN.name)
    }

    @Test
    fun `DETAIL screen type exists`() {
        assertEquals("DETAIL", ScreenType.DETAIL.name)
    }
}
