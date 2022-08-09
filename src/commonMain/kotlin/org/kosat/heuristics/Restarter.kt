package org.kosat.heuristics

import org.kosat.CDCL

// used for restarts between searches (luby restarts are used now)
class Restarter(private val solver: CDCL) {

    private val lubyMultiplierConstant = 50.0
    private var restartNumber = lubyMultiplierConstant
    private var numberOfConflictsAfterRestart = 0

    // 1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8, ...
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
        restartNumber = lubyMultiplierConstant * lubySeq[lubyPosition++]

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
}
