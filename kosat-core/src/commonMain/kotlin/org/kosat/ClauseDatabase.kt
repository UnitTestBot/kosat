package org.kosat

class ClauseDatabase {
    val clauses: MutableList<Clause> = mutableListOf()
    val learnts: MutableList<Clause> = mutableListOf()

    private val clauseDecay: Double = 0.999
    private var clauseInc: Double = 1.0

    fun add(clause: Clause) {
        if (clause.learnt) {
            learnts.add(clause)
        } else {
            clauses.add(clause)
        }
    }

    fun clauseDecayActivity() {
        clauseInc *= 1.0 / clauseDecay
    }

    fun clauseBumpActivity(clause: Clause) {
        if (!clause.learnt) return

        // Bump clause activity:
        clause.activity += clauseInc

        // Rescale:
        if (clause.activity > 1e20) {
            // Decrease the increment value:
            clauseInc *= 1e-20

            // Decrease all activities:
            for (learnt in learnts) {
                learnt.activity *= 1e-20
            }
        }
    }
}
