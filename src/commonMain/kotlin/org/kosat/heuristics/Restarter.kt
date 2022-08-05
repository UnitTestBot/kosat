package org.kosat.heuristics

import org.kosat.CDCL
import org.kosat.Incremental

class Restarter(private val solver: CDCL): Incremental {

    // ruby constant
    private val u = 50.0

    private var restartNumber = u
    private val restartCoeff = 1.1

    private var numberOfConflictsAfterRestart = 0
    private var numberOfRestarts = 0

    private val lubySeq: MutableList<Int> = mutableListOf(1)
    init {
        var pw = 1
        while (lubySeq.size < 1e5) {
            pw *= 2
            lubySeq.addAll(lubySeq)
            lubySeq.add(pw)
        }
    }

    private var lubyPosition = 1

    fun restart() {
        numberOfRestarts++
        // restartNumber *= restartCoeff
        restartNumber = u * lubySeq[lubyPosition++]

        solver.level = 0
        solver.units.clear()
        solver.clearTrail(0)
    }


    fun update() {
        numberOfConflictsAfterRestart++
        if (numberOfConflictsAfterRestart >= restartNumber) {
            numberOfConflictsAfterRestart = 0
            restart()
        }
    }

    // TODO why for...
    override fun addVariable() {
        TODO("Not yet implemented")
    }

}
