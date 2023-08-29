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
        // val path = "src/jvmTest/resources/testCover/cover/cover0015.cnf".toPath()
        val path = "../data/satcomp-2017/g2-mizh-md5-48-5.cnf".toPath()
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
