package org.kosat

import kotlin.math.max
import kotlin.math.min

/**
 * A vector of literals. It is more efficient than a plain `List<Lit>` because
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
class LitVec private constructor(
    var raw: IntArray,
    var size: Int = raw.size,
) {
    private val capacity get() = raw.size
    val lastIndex get() = size - 1

    constructor() : this(IntArray(0))
    constructor(lits: Iterable<Lit>) : this(lits.map { it.inner }.toIntArray())

    operator fun get(index: Int): Lit {
        // require(index < size)
        return Lit(raw[index])
    }

    operator fun set(index: Int, value: Lit) {
        // require(index < size)
        raw[index] = value.inner
    }

    fun isEmpty(): Boolean {
        return size == 0
    }

    fun indexOf(element: Lit): Int {
        for (i in 0 until size) {
            if (raw[i] == element.inner) return i
        }
        return -1
    }

    fun add(element: Lit) {
        if (size == capacity) grow()
        raw[size++] = element.inner
    }

    private fun grow() {
        raw = raw.copyOf(max(raw.size * 2, 8))
    }

    operator fun contains(element: Lit): Boolean {
        return indexOf(element) != -1
    }

    operator fun component1(): Lit {
        return Lit(raw[0])
    }

    operator fun component2(): Lit {
        return Lit(raw[1])
    }

    fun swap(i: Int, j: Int) {
        raw.swap(i, j)
    }

    fun copy(): LitVec {
        return LitVec(raw.copyOf(size), size)
    }

    fun sort() {
        // The only thing we care about for small arrays, is to not reallocate,
        // so reimplementing a sorting algorithm here is ok.
        if (size <= 32) {
            for (i in 1 until size) {
                val key = raw[i]
                var j = i - 1
                while (j >= 0 && raw[j] > key) {
                    raw[j + 1] = raw[j]
                    j--
                }
                raw[j + 1] = key
            }
        } else {
            if (raw.size != size) {
                raw = raw.copyOf(size)
            }

            raw.sort()
        }
    }

    fun retainFirst(count: Int) {
        if (count < size) {
            size = count
        }
    }

    inline fun removeAll(predicate: (Lit) -> Boolean) {
        var i = 0
        var j = 0
        while (i < size) {
            if (!predicate(Lit(raw[i]))) {
                raw[j++] = raw[i]
            }
            i++
        }
        size = j
    }

    fun removeLast(): Lit {
        return Lit(raw[--size])
    }

    fun remove(lit: Lit) {
        val index = indexOf(lit)
        if (index != -1) {
            removeAt(index)
        }
    }

    fun removeAt(index: Int): Lit {
        val prev = Lit(raw[index])
        for (i in index until size - 1) {
            raw[i] = raw[i + 1]
        }
        size--
        return prev
    }

    fun first(): Lit {
        return Lit(raw[0])
    }

    fun last(): Lit {
        return Lit(raw[size - 1])
    }

    fun toList(): List<Lit> {
        val result = ArrayList<Lit>(size)
        for (i in 0 until size) {
            result.add(Lit(raw[i]))
        }
        return result
    }

    fun toMutableList(): MutableList<Lit> {
        val result = ArrayList<Lit>(size)
        for (i in 0 until size) {
            result.add(Lit(raw[i]))
        }
        return result
    }

    inner class LitVecIter {
        private var index: Int = 0

        operator fun hasNext(): Boolean {
            return index < size
        }

        operator fun next(): Lit {
            return Lit(raw[index++])
        }
    }

    operator fun iterator(): LitVecIter {
        return LitVecIter()
    }

    inline fun any(predicate: (Lit) -> Boolean): Boolean {
        for (i in 0 until size) {
            if (predicate(Lit(raw[i]))) {
                return true
            }
        }
        return false
    }

    inline fun all(predicate: (Lit) -> Boolean): Boolean {
        for (i in 0 until size) {
            if (!predicate(Lit(raw[i]))) {
                return false
            }
        }
        return true
    }

    inline fun <T : Comparable<T>> minBy(selector: (Lit) -> T): Lit {
        var min = Lit(raw[0])
        var minVal = selector(min)
        for (i in 1 until size) {
            val lit = Lit(raw[i])
            val value = selector(lit)
            if (value < minVal) {
                min = lit
                minVal = value
            }
        }
        return min
    }

    inline fun <T> map(transform: (Lit) -> T): List<T> {
        val result = ArrayList<T>(size)
        for (i in 0 until size) {
            result.add(transform(Lit(raw[i])))
        }
        return result
    }

    inline fun <T : Comparable<T>> maxOfOrNull(selector: (Lit) -> T): T? {
        if (size == 0) return null
        var max = selector(Lit(raw[0]))
        for (i in 1 until size) {
            val value = selector(Lit(raw[i]))
            if (value > max) {
                max = value
            }
        }
        return max
    }

    inline fun forEach(action: (Lit) -> Unit) {
        for (i in 0 until size) {
            action(Lit(raw[i]))
        }
    }

    inline fun firstOrNull(predicate: (Lit) -> Boolean): Lit? {
        for (i in 0 until size) {
            val lit = Lit(raw[i])
            if (predicate(lit)) {
                return lit
            }
        }
        return null
    }

    inline fun count(predicate: (Lit) -> Boolean): Int {
        var count = 0
        for (i in 0 until size) {
            if (predicate(Lit(raw[i]))) {
                count++
            }
        }
        return count
    }

    /**
     * This is a slow, one time operation, which should be rewritten for
     * performance sensitive code.
     */
    inline fun <T : Comparable<T>> sortByDescending(crossinline selector: (Lit) -> T) {
        raw = raw.copyOf(size).sortedByDescending { selector(Lit(it)) }.toIntArray()
    }

    inline fun first(fn: (Lit) -> Boolean): Lit {
        for (i in 0 until size) {
            val lit = Lit(raw[i])
            if (fn(lit)) {
                return lit
            }
        }
        throw NoSuchElementException()
    }

    fun take(count: Int): LitVec {
        val realCount = min(count, size)
        return LitVec(raw.copyOf(realCount), realCount)
    }

    fun joinToString(
        separator: String = ", ",
        prefix: String = "",
        postfix: String = "",
        limit: Int = -1,
        truncated: String = "...",
        transform: ((Lit) -> CharSequence)? = null,
    ): String {
        return toList().joinToString(separator, prefix, postfix, limit, truncated, transform)
    }

    inline fun maxBy(selector: (Lit) -> Int): Lit {
        var max = Lit(raw[0])
        var maxVal = selector(max)
        for (i in 1 until size) {
            val lit = Lit(raw[i])
            val value = selector(lit)
            if (value > maxVal) {
                max = lit
                maxVal = value
            }
        }
        return max
    }

    fun getOrNull(index: Int): Lit? {
        if (index < 0 || index >= size) return null
        return Lit(raw[index])
    }

    companion object {
        fun of(a: Lit): LitVec = LitVec(intArrayOf(a.inner))
        fun of(a: Lit, b: Lit): LitVec = LitVec(intArrayOf(a.inner, b.inner))

        fun emptyOfCapacity(capacity: Int): LitVec = LitVec(IntArray(capacity), 0)
    }
}
