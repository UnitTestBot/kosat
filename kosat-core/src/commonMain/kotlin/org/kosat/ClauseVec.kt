package org.kosat

import kotlin.math.max

/**
 * A vector of clauseerals. It is more efficient than a plain `List<Clause>` because
 * it is backed by an array and does not use boxing.
 *
 * Unfortunately, making it iterable will force [iterator] method to return an
 * instance of `Iterable<T>`, which will cause virtual calls due to type
 * erasure every time it is used (two calls every `for` loop iteration:
 * `Iterator.hasNext` and `Iterator.next`). We use a custom iterator instead,
 * making it a bit more efficient. [iterator] function is marked as `operator`.
 *
 * Because of this, we cannot use kotlin extension functions on it, like `map`.
 * So instead some of these functions being used are reimplemented here. This
 * also makes it more efficient, because we can use primitive types instead of
 * boxed ones, and have every function inlined.
 */
class ClauseVec private constructor(var raw: Array<Clause>, var size: Int) {
    private val capacity get() = raw.size
    val lastIndex get() = size - 1

    constructor(clauses: List<Clause>) : this(clauses.toTypedArray(), clauses.size)
    constructor() : this(emptyArray, 0)

    operator fun get(index: Int): Clause {
        return raw[index]
    }


    fun isEmpty(): Boolean {
        return size == 0
    }


    fun add(element: Clause) {
        if (size == capacity) grow()
        raw[size++] = element
    }

    private fun grow() {
        @Suppress("UNCHECKED_CAST")
        raw = raw.copyOf(max(raw.size * 2, 8)) as Array<Clause>
    }

    inline fun removeAll(p: (Clause) -> Boolean) {
        var j = 0
        for (i in 0 until size) {
            if (!p(raw[i])) {
                raw[j++] = raw[i]
            }
        }
        size = j

    }

    inline fun count(p: (Clause) -> Boolean): Int {
        var count = 0
        for (i in 0 until size) {
            if (p(raw[i])) {
                count++
            }
        }
        return count
    }

    inner class ClauseVecIter(private var index: Int = 0) {
        operator fun hasNext(): Boolean {
            return index < size
        }

        operator fun next(): Clause {
            return raw[index++]
        }
    }

    operator fun iterator(): ClauseVecIter {
        return ClauseVecIter()
    }

    fun removeLast() {
        size--
    }

    fun clear() {
        size = 0
    }

    inline fun any(p: (Clause) -> Boolean): Boolean {
        for (i in 0 until size) {
            if (p(raw[i])) {
                return true
            }
        }
        return false
    }

    companion object {
        private val emptyArray = emptyArray<Clause>()
        val emptyClauseVec  get() = ClauseVec(emptyArray, 0)
        private val emptyClause = Clause(LitVec())
    }
}
