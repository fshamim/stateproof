package io.stateproof.matcher

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatcherTest {

    sealed class TestType {
        data object TypeA : TestType()
        data class TypeB(val value: Int) : TestType()
        data class TypeC(val name: String) : TestType()
    }

    @Test
    fun any_shouldMatchCorrectType() {
        val matcher = Matcher.any<TestType, TestType.TypeA>()

        assertTrue(matcher.matches(TestType.TypeA))
        assertFalse(matcher.matches(TestType.TypeB(1)))
        assertFalse(matcher.matches(TestType.TypeC("test")))
    }

    @Test
    fun any_withKClass_shouldMatchCorrectType() {
        val matcher = Matcher.any<TestType, TestType.TypeB>(TestType.TypeB::class)

        assertFalse(matcher.matches(TestType.TypeA))
        assertTrue(matcher.matches(TestType.TypeB(1)))
        assertTrue(matcher.matches(TestType.TypeB(999)))
        assertFalse(matcher.matches(TestType.TypeC("test")))
    }

    @Test
    fun where_shouldAddPredicate() {
        val matcher = Matcher.any<TestType, TestType.TypeB>()
            .where { value > 10 }

        assertFalse(matcher.matches(TestType.TypeA))
        assertFalse(matcher.matches(TestType.TypeB(5)))
        assertTrue(matcher.matches(TestType.TypeB(15)))
        assertTrue(matcher.matches(TestType.TypeB(100)))
    }

    @Test
    fun where_multiplePredicates_shouldAllApply() {
        val matcher = Matcher.any<TestType, TestType.TypeB>()
            .where { value > 10 }
            .where { value < 100 }

        assertFalse(matcher.matches(TestType.TypeB(5)))   // fails first predicate
        assertTrue(matcher.matches(TestType.TypeB(50)))   // passes both
        assertFalse(matcher.matches(TestType.TypeB(150))) // fails second predicate
    }

    @Test
    fun eq_shouldMatchExactValue() {
        val matcher = Matcher.eq<TestType, TestType.TypeB>(TestType.TypeB(42))

        assertFalse(matcher.matches(TestType.TypeA))
        assertFalse(matcher.matches(TestType.TypeB(1)))
        assertTrue(matcher.matches(TestType.TypeB(42)))
        assertFalse(matcher.matches(TestType.TypeB(43)))
    }

    @Test
    fun eq_withDataClass_shouldUseEquals() {
        val matcher = Matcher.eq<TestType, TestType.TypeC>(TestType.TypeC("hello"))

        assertFalse(matcher.matches(TestType.TypeC("world")))
        assertTrue(matcher.matches(TestType.TypeC("hello")))
    }
}
