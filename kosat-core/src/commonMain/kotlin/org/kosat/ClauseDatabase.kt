package org.kosat

enum class ReduceStrategy {
    ACTIVITY, LBD
}

class ClauseDatabase(private val solver: CDCL) {
    val clauses: MutableList<Clause> = mutableListOf()
    val learnts: MutableList<Clause> = mutableListOf()

    private val clauseDecay: Double = 0.999
    private var clauseInc: Double = 1.0

    private val reduceStrategy = ReduceStrategy.LBD

    fun addClause(clause: Clause) {
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

    /**
     * Is the clause locked, because there is a literal on the trail,
     * with a reason being that clause (propagated because of it)?
     */
    fun isClauseLocked(clause: Clause): Boolean {
        return solver.assignment.reason(clause[0].variable) === clause
    }

    /**
     * Remove clauses marked as deleted.
     */
    fun removeDeleted() {
        clauses.removeAll { it.deleted }
        learnts.removeAll { it.deleted }

        for (watched in solver.watchers) {
            watched.removeAll { it.deleted }
        }
    }

    /**
     * Remove clauses, satisfied at level 0, and falsified literals at level 0
     * in the remaining clauses.
     */
    fun simplify() {
        outer@for (clause in clauses + learnts) {
            if (clause.deleted) continue

            for (lit in clause.lits) {
                if (solver.assignment.fixed(lit) == LBool.TRUE) {
                    clause.deleted = true
                    continue@outer
                }
            }

            clause.lits.removeAll {
                solver.assignment.fixed(it) == LBool.FALSE
            }

            check(clause.lits.size >= 2)
        }
    }

    /**
     * Remove the least active learned clauses.
     */
    private fun reduceBasedOnActivity() {
        learnts.sortedBy { if (it.lits.size == 2) Double.POSITIVE_INFINITY else it.activity }

        val countLimit = learnts.size / 2
        val activityLimit = clauseInc / learnts.size.toDouble()

        for (i in 0 until countLimit) {
            val learnt = learnts[i]

            if (learnt.lits.size == 2) break
            if (learnt.activity >= activityLimit) break

            // Do not delete clauses if they are used in the trail
            if (solver.assignment.reason(learnt[0].variable) === learnt) continue

            learnt.deleted = true
        }
    }

    /**
     * Remove learned clauses with the highest LBD.
     */
    private fun reduceBasedOnLBD() {
        learnts.sortByDescending { it.lbd }

        val countLimit = learnts.size / 2

        for (i in 0 until countLimit) {
            learnts[i].deleted = true
        }
    }

    // TODO: Move to solver parameters
    private var reduceMaxLearnts = 6000
    private val reduceMaxLearntsIncrement = 500

    /**
     * Run the configured reduce if the number of learnt clauses is too high.
     */
    fun reduceIfNeeded() {
        if (learnts.size > reduceMaxLearnts + solver.assignment.trail.size) {
            reduceMaxLearnts += reduceMaxLearntsIncrement

            simplify()
            removeDeleted()

            when (reduceStrategy) {
                ReduceStrategy.ACTIVITY -> reduceBasedOnActivity()
                ReduceStrategy.LBD -> reduceBasedOnLBD()
            }

            removeDeleted()
        }
    }
}
