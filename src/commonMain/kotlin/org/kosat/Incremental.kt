package org.kosat

import kotlin.math.abs

class Incremental(initClauses: MutableList<MutableList<Int>>, initNumber: Int = 0) : CDCL(initClauses, initNumber) {
    // assumptions for incremental sat-solver
    private var assumptions: List<Int> = emptyList()

    fun solveWithAssumptions(currentAssumptions: List<Int> = emptyList()): List<Int>? {
        assumptions = currentAssumptions
        val result = solve()
        assumptions.forEach {
            if (getStatus(it) == VarStatus.FALSE) {
                assumptions = emptyList()
                return null
            }
        }
        assumptions = emptyList()
        return result
    }

    override fun getNextVariable(level: Int): Int {
        return if (level > assumptions.size) {
            vsids()
        } else {
            return assumptions[level - 1]
        }
    }


    override fun solve(): List<Int>? {
        removeUselessClauses()

        // extreme cases
        if (clauses.isEmpty()) return emptyList()
        if (clauses.any { it.size == 0 }) return null
        if (clauses.any { it.all { lit -> getStatus(lit) == VarStatus.FALSE } }) return null

        countScore()

        buildWatchers()

        // main loop
        while (true) {
            val conflictClause = propagate()
            if (conflictClause != -1) {
                // in case there is a conflict in CNF and trail is already in 0 state
                if (level == 0) return null
                addLemma(conflictClause)
                continue
            }

            // If (the problem is already) SAT, return the current assignment
            if (satisfiable()) {
                val model = variableValues()
                clearTrail(0)
                return model
            }

            // try to guess variable
            level++
            val nextVariable = getNextVariable(level)

            // Check that assumption we want to make isn't controversial
            if (wrongAssumption(nextVariable)) {
                clearTrail(0)
                return null
            }
            addVariable(-1, nextVariable)
        }
    }

    // remove clauses which contain x and -x
    private fun removeUselessClauses() {
        clauses.removeAll { clause -> clause.any { -it in clause } }
    }

    private fun wrongAssumption(lit: Int) = getStatus(lit) == VarStatus.FALSE

    fun newClause(clause: MutableList<Int>) {
        addClause(clause)
        val maxVar = clause.maxOf { abs(it) }
        while (newVar() < maxVar) {
        }
    }

}
