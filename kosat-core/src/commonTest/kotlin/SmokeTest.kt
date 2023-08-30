import org.kosat.CDCL
import org.kosat.SolveResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SmokeTest {
    @Test
    fun testOneVariable() {
        val solver = CDCL()
        solver.newVariable()
        val result = solver.solve()
        assertEquals(SolveResult.SAT, result)
        assertTrue(solver.getModel().isNotEmpty())
    }

    @Test
    fun testTieShirt() {
        // Create the KoSAT solver:
        val solver = CDCL()

        // Allocate two variables:
        solver.newVariable()
        solver.newVariable()

        // Encode TIE-SHIRT problem:
        solver.newClause(-1, 2)
        solver.newClause(1, 2)
        solver.newClause(-1, -2)
        // solver.newClause(1, -2) // UNSAT with this clause

        // Solve the SAT problem:
        val result = solver.solve()
        println("result = $result") // "SAT"
        assertEquals(SolveResult.SAT, result)

        // Get the model:
        val model = solver.getModel()
        println("model = $model") // {1=false, 2=true}
        assertEquals(listOf(false, true), model)
    }
}
