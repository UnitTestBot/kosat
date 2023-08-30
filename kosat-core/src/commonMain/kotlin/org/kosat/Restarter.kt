package org.kosat

import kotlin.math.max
import kotlin.math.pow

// used for restarts between searches (luby restarts are used now)
class Restarter(private val solver: CDCL) {

    private var restartNumber = 0
    var numberOfConflictsAfterRestart = 0

    /**
     * Returns i-th element of the Luby sequence with parameter p.
     * ```
     * 1, 1, p, 1, 1, p, p^2, 1, 1, p, 1, 1, p, p^2, p^3, 1, ...
     * ```
     */
    private fun luby(p: Double, i: Int): Double {
        var k = 1
        var size = 1
        while (size < i + 1) {
            k++
            size = 2 * size + 1
        }
        var cur = i
        while (size - 1 != cur) {
            size = (size - 1) / 2
            k--
            cur %= size
        }
        return p.pow(k - 1)
    }

    private var lubyPosition = 1

    fun restartIfNeeded() {
        if (!solver.config.restarts) return

        val lubyConstant = solver.config.restarterLubyConstant

        restartNumber = max(restartNumber, lubyConstant)

        if (numberOfConflictsAfterRestart >= restartNumber) {
            if (restartNumber >= 1000) {
                solver.reporter.report(
                    "Big Restart ($numberOfConflictsAfterRestart conflicts)",
                    solver.stats
                )
            }

            restartNumber = (lubyConstant * luby(solver.config.restarterLubyBase, lubyPosition++)).toInt()
            solver.backtrack(0)
            solver.stats.restarts++

            numberOfConflictsAfterRestart = 0
        }
    }
}
