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
        // val path = "src/jvmTest/resources/testCover/small/prime4.cnf".toPath()
        // val path = "../data/satcomp-2017/mp1-squ_ali_s10x10_c39_bail_SAT.cnf".toPath()
        // val path = "../data/satcomp-2017/mp1-9_1.cnf".toPath()
        val path = "../data/suite/mp1-ps_5000_21250_3_0_0.8_0_1.50_0.cnf".toPath()
        val cnf = CNF.from(path)
        val clauses = cnf.clauses.map { lits ->
            Clause(LitVec(lits.map { Lit.fromDimacs(it) }))
        }
        val solver = CDCL(clauses, cnf.numVars)
        // solver.dratBuilder = DratBuilder(System.err.sink().buffer())
        println("Solving...")
        val (result, timeSolve) = measureTimeWithResult {
            solver.solve()
        }
        println("result = $result")
        println("All done in $timeSolve")
    }
}
