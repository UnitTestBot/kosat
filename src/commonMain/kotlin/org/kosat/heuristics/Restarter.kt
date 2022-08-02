package org.kosat.heuristics

import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.Incremental
import kotlin.math.abs

class Restarter(private val solver: CDCL): Incremental {

    private val u = 50.0

    private var restartNumber = u
    private val restartCoeff = 1.1

    private var numberOfConflictsAfterRestart = 0
    private var numberOfRestarts = 0

    private val lubySeq: MutableList<Int> = mutableListOf(1)

    private var curr = 1

    init {
        var pw = 1
        while (lubySeq.size < 1e5) {
            pw *= 2
            lubySeq.addAll(lubySeq)
            lubySeq.add(pw)
        }
    }


    // making restart to remove useless clauses
    fun restart() {
        numberOfRestarts++
        // restartNumber *= restartCoeff
        restartNumber = u * lubySeq[curr++]
        solver.level = 0

        solver.units.clear()

        solver.clearTrail(0)
    }


    fun update() {
        numberOfConflictsAfterRestart++
        // restarting after some number of conflicts
        if (numberOfConflictsAfterRestart >= restartNumber) {
            numberOfConflictsAfterRestart = 0
            restart()
        }
    }

    override fun addVariable() {
        TODO("Not yet implemented")
    }

}
