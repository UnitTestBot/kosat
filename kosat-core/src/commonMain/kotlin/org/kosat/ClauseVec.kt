package org.kosat

import kotlin.math.max

/**
 * A vector of clause literals. It is more efficient than a plain `List<Clause>` because
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
class ClauseVec private constructor(
    var raw: Array<Clause>,
    var size: Int = raw.size,
) {
    private val capacity get() = raw.size
    val lastIndex get() = size - 1

    constructor() : this(emptyArray<Clause>())
    constructor(clauses: Collection<Clause>) : this(clauses.toTypedArray())

    operator fun get(index: Int): Clause {
        // require(index < size)
        return raw[index]
    }

    operator fun set(index: Int, value: Clause) {
        // require(index < size)
        raw[index] = value
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

    inline fun removeAll(predicate: (Clause) -> Boolean) {
        var j = 0
        for (i in 0 until size) {
            if (!predicate(raw[i])) {
                raw[j++] = raw[i]
            }
        }
        size = j

    }

    inline fun count(predicate: (Clause) -> Boolean): Int {
        var count = 0
        for (i in 0 until size) {
            if (predicate(raw[i])) {
                count++
            }
        }
        return count
    }

    inline fun any(predicate: (Clause) -> Boolean): Boolean {
        for (i in 0 until size) {
            if (predicate(raw[i])) {
                return true
            }
        }
        return false
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
}
