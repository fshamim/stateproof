package io.stateproof.matcher

import kotlin.reflect.KClass

/**
 * A type-safe matcher for state and event matching in the state machine DSL.
 *
 * Supports matching by type and optional predicates for more specific matching.
 *
 * @param T The base type being matched against
 * @param R The specific subtype to match (must extend T)
 */
class Matcher<T : Any, out R : T> private constructor(private val kClass: KClass<R>) {

    /**
     * The KClass that this matcher matches against.
     * Used for introspection and graph analysis.
     */
    val matchedClass: KClass<out T> get() = kClass

    private val predicates = mutableListOf<(T) -> Boolean>({ kClass.isInstance(it) })

    /**
     * Adds an additional predicate to narrow the match.
     *
     * @param predicate A function that returns true if the value matches
     * @return This matcher for chaining
     */
    fun where(predicate: R.() -> Boolean): Matcher<T, R> = apply {
        predicates.add {
            @Suppress("UNCHECKED_CAST")
            (it as R).predicate()
        }
    }

    /**
     * Tests if a value matches this matcher.
     *
     * @param value The value to test
     * @return true if all predicates pass
     */
    fun matches(value: T): Boolean = predicates.all { it(value) }

    companion object {
        /**
         * Creates a matcher for the given KClass.
         */
        fun <T : Any, R : T> any(kClass: KClass<R>): Matcher<T, R> = Matcher(kClass)

        /**
         * Creates a matcher using reified type inference.
         */
        inline fun <T : Any, reified R : T> any(): Matcher<T, R> = any(R::class)

        /**
         * Creates a matcher that matches a specific value by equality.
         */
        inline fun <T : Any, reified R : T> eq(value: R): Matcher<T, R> =
            any<T, R>().where { this == value }
    }
}
