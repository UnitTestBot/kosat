package org.kosat

import kotlin.math.max

data class Clause(
    val lits: DenseLitVec,
    val learnt: Boolean = false,
) {
    val size: Int get() = lits.size
    var deleted: Boolean = false
    var fromInput: Boolean = false
    var activity: Double = 0.0
    var lbd: Int = 0

    operator fun get(i: Int): Lit {
        return lits[i]
    }

    operator fun set(i: Int, x: Lit) {
        lits[i] = x
    }

    fun toDimacs(): List<Int> {
        return lits.map { it.toDimacs() }
    }

    companion object {
        fun fromDimacs(clause: List<Int>): Clause {
            val lits = DenseLitVec(clause.map { Lit.fromDimacs(it) })
            return Clause(lits)
        }
    }
}

class DenseLitVec(var raw: IntArray, size: Int) : List<Lit> {
    override var size = size

    val capacity get() = raw.size

    class DenseLitIter(val vec: DenseLitVec, var index: Int) : ListIterator<Lit> {
        override fun hasNext(): Boolean {
            return index < vec.size
        }

        override fun hasPrevious(): Boolean {
            return index > 0
        }

        override fun next(): Lit {
            return vec[index++]
        }

        override fun nextIndex(): Int {
            return index
        }

        override fun previous(): Lit {
            return vec[--index]
        }

        override fun previousIndex(): Int {
            return index - 1
        }
    }

    constructor(lits: List<Lit>) : this(lits.map { it.inner }.toIntArray(), lits.size)

    override operator fun get(index: Int): Lit {
        return Lit(raw[index])
    }

    override fun isEmpty(): Boolean {
        return size == 0
    }

    override fun iterator(): Iterator<Lit> {
        return DenseLitIter(this, 0)
    }

    override fun listIterator(): ListIterator<Lit> {
        return DenseLitIter(this, 0)
    }

    override fun listIterator(index: Int): ListIterator<Lit> {
        return DenseLitIter(this, index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<Lit> {
        throw UnsupportedOperationException()
    }

    override fun lastIndexOf(element: Lit): Int {
        for (i in size - 1 downTo 0) {
            if (raw[i] == element.inner) return i
        }
        return -1
    }

    override fun indexOf(element: Lit): Int {
        for (i in 0 until size) {
            if (raw[i] == element.inner) return i
        }
        return -1
    }

    operator fun set(index: Int, element: Lit): Lit {
        val prev = Lit(raw[index])
        raw[index] = element.inner
        return prev
    }

    fun add(element: Lit): Boolean {
        require(size < raw.size)
        raw[size++] = element.inner
        return true
    }

    fun clear() {
        size = 0
    }

    override fun containsAll(elements: Collection<Lit>): Boolean {
        for (e in elements) {
            if (!contains(e)) return false
        }
        return true
    }

    override fun contains(element: Lit): Boolean {
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

    fun copy(): DenseLitVec {
        return DenseLitVec(raw.copyOf(), size)
    }

    companion object {
        val empty: DenseLitVec get() = DenseLitVec(intArrayOf(), 0)

        fun of(a: Lit) = DenseLitVec(intArrayOf(a.inner), 1)
        fun of(a: Lit, b: Lit) = DenseLitVec(intArrayOf(a.inner, b.inner), 2)

        fun emptyOfCapacity(capacity: Int): DenseLitVec {
            return DenseLitVec(IntArray(capacity), 0)
        }
    }

    fun grow() {
        raw = raw.copyOf(max(raw.size * 2, 16))
    }

    fun sort() {
        if (size < 16) {
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

    inline fun removeAll(crossinline fn: (Lit) -> Boolean) {
        var i = 0
        var j = 0
        while (i < size) {
            if (!fn(Lit(raw[i]))) {
                raw[j++] = raw[i]
            }
            i++
        }
        size = j
    }

    fun removeLast(): Lit {
        return Lit(raw[--size])
    }
}
