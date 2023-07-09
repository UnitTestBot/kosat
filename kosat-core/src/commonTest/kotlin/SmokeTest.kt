import org.kosat.CDCL
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class SmokeTest {
    @Test
    fun testOneVariable() {
        val solver = CDCL()
        solver.addVariable()
        val model = solver.solve()
        assertNotNull(model.values)
        assertTrue(model.values!!.isNotEmpty())
    }
}
