package org.kosat

data class Clause(
    val lits: LitVec,
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

    fun toDimacs(): List<Int> {
        return lits.map { it.toDimacs() }
    }

    companion object {
        fun fromDimacs(dimacsLits: Iterable<Int>): Clause {
            val lits = LitVec(dimacsLits.map { Lit.fromDimacs(it) })
            return Clause(lits)
        }
    }
}
