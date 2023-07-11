import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.kosat.CDCL
import org.kosat.SolveResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class SmokeTest {
    @Test
    fun `test one variable`() {
        val solver = CDCL()
        solver.addVariable()
        val result = solver.solve()
        assertEquals(SolveResult.SAT, result)
        assertTrue(solver.getModel().isNotEmpty())
    }
}
