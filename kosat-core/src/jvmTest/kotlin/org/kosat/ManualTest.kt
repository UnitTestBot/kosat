package org.kosat

import okio.Path.Companion.toPath
import org.kosat.cnf.CNF
import org.kosat.cnf.from
import kotlin.test.Test

internal class ManualTest {
    @Test
    fun testManual() {
        val path = "src/jvmTest/resources/benchmarks/sat-grid-pbl-0070.sat05-1334.reshuffled-07.cnf".toPath()
        val cnf = CNF.from(path)
        val clauses = cnf.clauses.map { lits ->
            Clause(lits.map { Lit.fromDimacs(it) }.toMutableList())
        }
        val solver = CDCL(clauses, cnf.numVars)
        val model = solver.solve()
        println("model = $model")
    }
}
