package org.kosat

class Clause(val lits: MutableList<Lit>): Iterable<Lit>, Collection<Lit> {

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
}
