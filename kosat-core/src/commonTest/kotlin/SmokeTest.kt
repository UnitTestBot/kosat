import org.kosat.CDCL
import org.kosat.Configuration
import org.kosat.SolveResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SmokeTest {
    @Test
    fun testOneVariable() {
        val solver = CDCL(Configuration())
        solver.newVariable()
        val result = solver.solve()
        assertEquals(SolveResult.SAT, result)
        assertTrue(solver.getModel().isNotEmpty())
    }
}
