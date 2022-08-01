package org.kosat

typealias Lit = Int

class Clause(val lits: MutableList<Lit>): Collection<Lit> {

    constructor(): this(mutableListOf<Lit>())

    var locked = false

    var deleted = false

    var lbd = 0

    override operator fun iterator() = lits.iterator()

    operator fun get(index: Int) = lits[index]

    operator fun set(index: Int, lit: Lit) {
        lits[index] = lit
    }

    override val size: Int
        get() = lits.size

    override fun containsAll(elements: Collection<Lit>): Boolean = lits.containsAll(elements)

    override fun contains(element: Lit): Boolean = lits.contains(element)

    override fun isEmpty(): Boolean = lits.isEmpty()

    fun add(lit: Int) {
        lits.add(lit)
    }
}
