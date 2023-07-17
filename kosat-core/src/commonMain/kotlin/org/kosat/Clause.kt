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

    fun toDIMACS(): List<Int> {
        return lits.map { it.toDIMACS() }
    }

    companion object {
        fun fromDIMACS(clause: List<Int>): Clause {
            val lits = clause.map { Lit.fromDIMACS(it) }.toMutableList()
            return Clause(lits)
        }
    }
}
