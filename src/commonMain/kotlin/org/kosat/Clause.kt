package org.kosat

typealias Lit = Int

class Clause(val lits: MutableList<Lit>): Collection<Lit> by lits {

    constructor(): this(mutableListOf<Lit>())

    var locked = false

    var deleted = false

    var lbd = 0

    operator fun get(index: Int) = lits[index]

    operator fun set(index: Int, lit: Lit) {
        lits[index] = lit
    }

    fun add(lit: Int) {
        lits.add(lit)
    }

    val lastIndex: Int
        get() = lits.lastIndex
}
