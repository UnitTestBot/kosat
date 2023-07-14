package org.kosat

data class Clause(
    val lits: MutableList<Lit>,
    val learnt: Boolean = false,
) {
    val size: Int get() = lits.size
    var deleted: Boolean = false
    var activity: Double = 0.0
    var lbd: Int = 0

    operator fun get(i: Int): Lit {
        return lits[i]
    }

    operator fun set(i: Int, x: Lit) {
        lits[i] = x
    }
}
