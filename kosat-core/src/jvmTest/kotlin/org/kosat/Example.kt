package org.kosat

import org.junit.jupiter.api.Assertions
import kotlin.test.Test

internal class Example {
    @Test
    fun testExample() {
        // Create the instance of KoSAT solver:
        val solver = CDCL()

        // Encode the TIE-SHIRT problem:
        solver.newClause(-1, 2)
        solver.newClause(1, 2)
        solver.newClause(-1, -2)
        // solver.newClause(1, -2) // UNSAT with this clause

        // Solve the SAT problem:
        val result = solver.solve()
        println("result = $result") // SAT

        // Get the model:
        val model = solver.getModel()
        println("model = $model") // [false, true]

        Assertions.assertEquals(SolveResult.SAT, result)
        Assertions.assertEquals(listOf(false, true), model)
    }
}