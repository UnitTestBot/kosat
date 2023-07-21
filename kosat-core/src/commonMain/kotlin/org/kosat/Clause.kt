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

    fun toDimacs(): List<Int> {
        return lits.map { it.toDimacs() }
    }

    companion object {
        fun fromDimacs(clause: List<Int>): Clause {
            val lits = clause.map { Lit.fromDimacs(it) }.toMutableList()
            return Clause(lits)
        }
    }

    override fun toString(): String {
        val flags = sequenceOf(
            if (learnt) "L" else ".",
            if (deleted) "D" else "."
        ).joinToString("")

        return "Cl(${lits.joinToString(" ")} | flg=$flags act=${activity.round(2)} lbd=$lbd)"
    }
}
