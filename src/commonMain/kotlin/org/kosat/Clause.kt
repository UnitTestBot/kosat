package org.kosat

class Clause(val lits: MutableList<Lit>): Iterable<Lit> {
    override operator fun iterator() = lits.iterator()
    operator fun get(index: Int) = lits[index]
}
