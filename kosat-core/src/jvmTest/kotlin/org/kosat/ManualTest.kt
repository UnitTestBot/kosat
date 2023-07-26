package org.kosat

import okio.Path.Companion.toPath
import okio.buffer
import okio.sink
import org.kosat.cnf.CNF
import org.kosat.cnf.from
import kotlin.test.Test

internal class ManualTest {
    @Test
    fun testManual() {
        val path = "src/jvmTest/resources/testCover/cover/cover0025.cnf".toPath()
        val cnf = CNF.from(path)
        val clauses = cnf.clauses.map { lits ->
            Clause(lits.map { Lit.fromDimacs(it) }.toMutableList())
        }
        val solver = CDCL(clauses, cnf.numVars)
        solver.dratBuilder = DratBuilder(System.err.sink().buffer())
        val model = solver.solve()
        println("${solver.getModel()}")
        println("model = $model")
        val model2 = solver.solve(listOf(Lit.fromDimacs(22)))
        println("model = $model2")
        // println("${solver.getModel()}")
    }
}
