package org.kosat

import okio.Path.Companion.toPath
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.kosat.cnf.CNF
import org.kosat.cnf.from

class BudgetTest {
    // This is a hard problem that takes a lot of conflicts to solve.
    private val hardCnf = CNF.from("src/jvmTest/resources/benchmarks/s57-100.cnf".toPath())

    @Test
    fun testConflictBudget() {
        val cfg = Configuration(
            shouldTerminate = { stats ->
                stats.conflicts.thisSolve < 100
            }
        )

        val solver = CDCL(cfg, hardCnf)
        val result = solver.solve()

        Assertions.assertEquals(SolveResult.UNKNOWN, result)
        Assertions.assertTrue(solver.statistics.conflicts.thisSolve >= 100)

        // Kind of adhoc, but this can be guaranteed because there
        // aren't many variables in the CNF.
        Assertions.assertTrue(solver.statistics.conflicts.thisSolve < 200)

        val resultSecondSolve = solver.solve()

        Assertions.assertEquals(SolveResult.UNKNOWN, resultSecondSolve)

        Assertions.assertTrue(solver.statistics.conflicts.overall >= 200)
        Assertions.assertTrue(solver.statistics.conflicts.overall < 400)
        Assertions.assertTrue(solver.statistics.conflicts.thisSolve >= 100)
        Assertions.assertTrue(solver.statistics.conflicts.thisSolve < 200)
    }

    @Test
    fun testPropagateBudget() {
        val cfg = Configuration(
            shouldTerminate = { stats ->
                stats.propagations.thisSolve < 1000
            }
        )

        val solver = CDCL(cfg, hardCnf)
        val result = solver.solve()

        Assertions.assertEquals(SolveResult.UNKNOWN, result)
    }

    @Test
    fun testRestartBudget() {
        val cfg = Configuration(
            shouldTerminate = { stats ->
                stats.restarts.thisSolve < 10
            }
        )

        val solver = CDCL(cfg, hardCnf)
        val result = solver.solve()

        Assertions.assertEquals(SolveResult.UNKNOWN, result)
    }
}
