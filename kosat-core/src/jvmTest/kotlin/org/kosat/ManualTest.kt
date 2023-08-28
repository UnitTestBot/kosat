package org.kosat

import korlibs.time.measureTimeWithResult
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Timeout
import org.kosat.cnf.CNF
import org.kosat.cnf.from
import kotlin.test.Test

@Timeout(1000_000_000)
internal class ManualTest {
    @Test
    fun testManual() {
        val path = "src/jvmTest/resources/testCover/small/prime4.cnf".toPath()
        val cnf = CNF.from(path)
        val clauses = cnf.clauses.map { lits ->
            Clause(LitVec(lits.map { Lit.fromDimacs(it) }))
        }
        val solver = CDCL(clauses, cnf.numVars)
        // solver.dratBuilder = DratBuilder(System.err.sink().buffer())
        val (result, time) = measureTimeWithResult {
            solver.solve()
        }
        println("result = $result")
        println("time = $time")
    }
}
