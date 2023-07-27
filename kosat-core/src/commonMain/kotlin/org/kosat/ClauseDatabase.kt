package org.kosat

class ClauseDatabase(private val solver: CDCL) {
    val clauses: MutableList<Clause> = mutableListOf()
    val learnts: MutableList<Clause> = mutableListOf()

    private val strategy get() = solver.cfg.clauseDbStrategy

    private var clauseInc: Double = 1.0
    private var maxLearntsBeforeReduce: Int = strategy.maxLearntsBeforeReduceInitial

    fun add(clause: Clause) {
        if (clause.learnt) {
            learnts.add(clause)
        } else {
            clauses.add(clause)
        }
    }

    fun clauseDecayActivity() {
        val activityCfg = strategy as? Configuration.ClauseDbStrategy.Activity ?: return
        clauseInc *= 1.0 / activityCfg.decay
    }

    fun clauseBumpActivity(clause: Clause) {
        if (strategy !is Configuration.ClauseDbStrategy.Activity) return
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
        outer@ for (clause in clauses + learnts) {
            if (clause.deleted) continue

            for (lit in clause.lits) {
                if (solver.assignment.fixed(lit) == LBool.TRUE) {
                    solver.statistics.satisfiedClauses++
                    solver.markDeleted(clause)
                    continue@outer
                }
            }

            val needsShrink = clause.lits.any {
                solver.assignment.fixed(it) == LBool.FALSE
            }

            if (!needsShrink) continue

            val clauseBefore = Clause(clause.lits.toMutableList())

            clause.lits.removeAll {
                solver.assignment.fixed(it) == LBool.FALSE
            }

            solver.statistics.shrunkClauses++

            if (clause.learnt) {
                solver.dratBuilder.addClause(clause)
                solver.dratBuilder.deleteClause(clauseBefore)
            }

            check(clause.size >= 2)
        }
    }

    /**
     * Remove the least active learned clauses.
     *
     * @see [Clause.activity]
     */
    private fun reduceBasedOnActivity() {
        // Putting the least active clauses at the start of the list,
        // keeping binary clauses and deleted clauses at the end
        learnts.sortedBy {
            if (it.deleted || it.lits.size == 2) {
                Double.POSITIVE_INFINITY
            } else {
                it.activity
            }
        }

        val countLimit = learnts.size / 2
        val activityLimit = clauseInc / learnts.size.toDouble()

        // Remove the least active learned clauses, at most countLimit
        for (i in 0 until countLimit) {
            val learnt = learnts[i]

            if (learnt.lits.size == 2) break
            if (learnt.deleted) break
            if (learnt.activity >= activityLimit) break

            // Do not remove clauses if they are used in the trail
            // technically, this is not needed, but might be used later
            if (isClauseLocked(learnt)) continue

            solver.markDeleted(learnt)
        }
    }

    /**
     * Remove learned clauses with the highest Literal Block Distance.
     *
     * @see Clause.lbd
     */
    private fun reduceBasedOnLBD() {
        // Similar to reduceBasedOnActivity, but sorting by LBD
        // the clauses with the highest LBD are at the start of the list
        // the already deleted clauses are at the end
        learnts.sortByDescending {
            if (it.deleted) {
                0
            } else {
                it.lbd
            }
        }

        val countLimit = learnts.size / 2

        for (i in 0 until countLimit) {
            val learnt = learnts[i]

            if (learnt.deleted) break
            if (isClauseLocked(learnt)) continue

            solver.markDeleted(learnt)
        }
    }

    /**
     * Run the configured reduce if the number of learnt clauses is too high.
     */
    fun reduceIfNeeded() {
        if (learnts.size > maxLearntsBeforeReduce + solver.assignment.trail.size) {
            maxLearntsBeforeReduce += strategy.maxLearntsBeforeReduceIncrement

            solver.statistics.dbReduces++

            simplify()

            when (strategy) {
                is Configuration.ClauseDbStrategy.Activity -> reduceBasedOnActivity()
                is Configuration.ClauseDbStrategy.LBD -> reduceBasedOnLBD()
            }

            removeDeleted()
        }
    }
}
