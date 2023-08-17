package org.kosat

data class Clause(
    val lits: LitStore,
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
            val lits = LitStore(clause.map { Lit.fromDimacs(it) })
            return Clause(lits)
        }
    }
}

class LitStore private constructor(raw: IntArray) : MutableList<Lit> {
    var lits: IntArray = raw

    constructor(lits: List<Lit>) : this(lits.map { it.inner }.toIntArray())

    class LitStoreIterator(private val store: LitStore, var pos: Int) : MutableListIterator<Lit> {
        var lastIndex = -1

        override fun add(element: Lit) {
            store.add(pos, element)
            pos++
        }

        override fun hasNext(): Boolean {
            return pos < store.size
        }

        override fun hasPrevious(): Boolean {
            return pos > 0
        }

        override fun next(): Lit {
            lastIndex = pos++
            return store[lastIndex]
        }

        override fun nextIndex(): Int {
            return pos
        }

        override fun previous(): Lit {
            lastIndex = --pos
            return store[lastIndex]
        }

        override fun previousIndex(): Int {
            return pos - 1
        }

        override fun remove() {
            store.removeAt(lastIndex)
            pos = lastIndex
        }

        override fun set(element: Lit) {
            store[lastIndex] = element
        }
    }

    override operator fun get(i: Int): Lit {
        return Lit(lits[i])
    }

    override fun isEmpty(): Boolean {
        return lits.isEmpty()
    }

    override fun iterator(): MutableIterator<Lit> {
        return LitStoreIterator(this, 0)
    }

    override fun listIterator(): MutableListIterator<Lit> {
        return LitStoreIterator(this, 0)
    }

    override fun listIterator(index: Int): MutableListIterator<Lit> {
        return LitStoreIterator(this, index)
    }

    override fun removeAll(elements: Collection<Lit>): Boolean {
        var j = 0
        for (i in lits.indices) {
            if (Lit(lits[i]) !in elements) {
                lits[j] = lits[i]
                j++
            }
        }
        val newLits = IntArray(j) { lits[it] }
        val result = lits.size != newLits.size
        lits = newLits
        return result
    }

    override fun removeAt(index: Int): Lit {
        val newLits = IntArray(lits.size - 1)
        for (i in 0 until index) newLits[i] = lits[i]
        for (i in index + 1 until lits.size) newLits[i - 1] = lits[i]
        val prev = Lit(lits[index])
        lits = newLits
        return prev
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<Lit> {
        val newLits = IntArray(toIndex - fromIndex)
        for (i in fromIndex until toIndex) newLits[i - fromIndex] = lits[i]
        return LitStore(newLits)
    }

    override fun retainAll(elements: Collection<Lit>): Boolean {
        var j = 0
        for (i in lits.indices) {
            if (Lit(lits[i]) in elements) {
                lits[j] = lits[i]
                j++
            }
        }
        val newLits = IntArray(j) { lits[it] }
        val result = lits.size != newLits.size
        lits = newLits
        return result
    }

    override fun remove(element: Lit): Boolean {
        val newLits = IntArray(lits.size - 1)
        var j = 0
        for (i in lits.indices) {
            if (Lit(lits[i]) != element) {
                newLits[j] = lits[i]
                j++
            }
        }
        lits = newLits
        return true
    }

    override fun lastIndexOf(element: Lit): Int {
        return lits.lastIndexOf(element.inner)
    }

    override fun indexOf(element: Lit): Int {
        return lits.indexOf(element.inner)
    }

    override operator fun set(index: Int, element: Lit): Lit {
        val prev = Lit(lits[index])
        lits[index] = element.inner
        return prev
    }

    override val size: Int get() = lits.size

    override fun clear() {
        lits = IntArray(0)
    }

    override fun addAll(elements: Collection<Lit>): Boolean {
        if (elements.isEmpty()) return false
        val newLits = IntArray(lits.size + elements.size)
        for (i in lits.indices) newLits[i] = lits[i]
        for (i in lits.size until newLits.size) newLits[i] = elements.elementAt(i - lits.size).inner
        lits = newLits
        return true
    }

    override fun addAll(index: Int, elements: Collection<Lit>): Boolean {
        if (elements.isEmpty()) return false
        val newLits = IntArray(lits.size + elements.size)
        for (i in 0 until index) newLits[i] = lits[i]
        for (i in index until index + elements.size) newLits[i] = elements.elementAt(i - index).inner
        for (i in index + elements.size until newLits.size) newLits[i] = lits[i - elements.size]
        lits = newLits
        return true
    }

    override fun add(index: Int, element: Lit) {
        val newLits = IntArray(lits.size + 1)
        for (i in 0 until index) newLits[i] = lits[i]
        newLits[index] = element.inner
        for (i in index + 1 until newLits.size) newLits[i] = lits[i - 1]
        lits = newLits
    }

    override fun add(element: Lit): Boolean {
        val newLits = IntArray(lits.size + 1)
        for (i in lits.indices) newLits[i] = lits[i]
        newLits[lits.size] = element.inner
        lits = newLits
        return true
    }

    override fun containsAll(elements: Collection<Lit>): Boolean {
        return elements.all { it in this }
    }

    override fun contains(element: Lit): Boolean {
        return element.inner in lits
    }

    fun swap(i: Int, j: Int) {
        lits.swap(i, j)
    }

    fun copy(): LitStore {
        return LitStore(lits.copyOf())
    }

    companion object {
        val empty: LitStore = LitStore(intArrayOf())

        fun of(a: Lit) = LitStore(listOf(a))
        fun of(a: Lit, b: Lit) = LitStore(listOf(a, b))
    }
}
