package org.kosat

import okio.Path.Companion.toPath
import okio.buffer
import okio.sink
import org.junit.jupiter.api.Timeout
import org.kosat.cnf.CNF
import org.kosat.cnf.from
import kotlin.test.Test

@Timeout(1000_000_000)
internal class ManualTest {
    @Test
    fun testManual() {
        val path = "src/jvmTest/resources/benchmarks/hsat_vc12016.cnf".toPath()
        val cnf = CNF.from(path)
        val clauses = cnf.clauses.map { lits ->
            Clause(lits.map { Lit.fromDimacs(it) }.toMutableList())
        }
        val solver = CDCL(clauses, cnf.numVars)
        // solver.dratBuilder = DratBuilder(System.err.sink().buffer())
        val model = solver.solve()
        // println("${solver.getModel()}")
        // println("model = $model")
        // val model2 = solver.solve(listOf(Lit.fromDimacs(1)))
        // println("model = $model2")
        // solver.solve(listOf(Lit.fromDimacs(2), Lit.fromDimacs(-1)))
        // println("${solver.getModel()}")
    }
}
