package org.kosat

import kotlin.math.max

// used for restarts between searches (luby restarts are used now)
class Restarter(private val solver: CDCL) {

    private var restartNumber = 0
    var numberOfConflictsAfterRestart = 0

    // 1, 1, 2, 1, 1, 2, 4, 1, 1, 2, 1, 1, 2, 4, 8, ...
    // return i'th element of luby sequence
    private fun luby(i: Int, initialDeg: Int = 1): Int {
        if (i == 2) return 1
        var deg = initialDeg
        while (deg <= i) {
            deg *= 2
        }
        while (deg / 2 > i) {
            deg /= 2
        }
        if (deg - 1 == i) {
            return deg / 2
        }
        return luby(i - deg / 2 + 1, deg / 2)
    }

    private var lubyPosition = 1

    fun restartIfNeeded() {
        if (!solver.config.restarts) return

        val lubyConstant = solver.config.restarterLubyConstant

        restartNumber = max(restartNumber, lubyConstant)

        if (numberOfConflictsAfterRestart >= restartNumber) {
            restartNumber = lubyConstant * luby(lubyPosition++)
            solver.backtrack(0)

            numberOfConflictsAfterRestart = 0
        }
    }
}
