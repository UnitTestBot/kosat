package org.kosat

import okio.Path.Companion.toPath
import org.kosat.cnf.CNF
import org.kosat.cnf.from
import kotlin.test.Test

internal class ManualTest {
    @Test
    fun testManual() {
        val path = "src/jvmTest/resources/testCover/cover/cover0033.cnf".toPath()
        val cnf = CNF.from(path)
        val clauses = cnf.clauses.map { lits ->
            Clause(lits.map { Lit.fromDimacs(it) }.toMutableList())
        }
        val solver = CDCL(Configuration(), clauses, cnf.numVars)
        val model = solver.solve()
        println("${solver.getModel()}")
        println("model = $model")
        val model2 = solver.solve(listOf(Lit.fromDimacs(1)))
        val model3 = solver.solve(listOf(Lit.fromDimacs(2), Lit.fromDimacs(-1)))
        println("model = $model2")
        println("model = $model3")
    }
}
