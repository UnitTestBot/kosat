package org.kosat

class ClauseDatabase {
    val clauses: MutableList<Clause> = mutableListOf()
    val learnts: MutableList<Clause> = mutableListOf()
    private val clause_decay: Double = 0.999
    private var clause_inc: Double = 1.0

    fun addClause(clause: Clause) {
        if (clause.learnt) {
            learnts.add(clause)
        } else {
            clauses.add(clause)
        }
    }

    fun clauseDecayActivity() {
        clause_inc *= 1.0 / clause_decay
    }

    fun clauseBumpActivity(clause: Clause) {
        if (!clause.learnt) return

        // Bump clause activity:
        clause.activity += clause_inc

        // Rescale:
        if (clause.activity > 1e20) {
            // Decrease the increment value:
            clause_inc *= 1e-20

            // Decrease all activities:
            for (learnt in learnts) {
                learnt.activity *= 1e-20
            }
        }
    }
}
