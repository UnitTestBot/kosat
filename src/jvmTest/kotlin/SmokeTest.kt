import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.kosat.CDCL
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class SmokeTest {
    @Test
    fun `test one variable`() {
        val solver = CDCL()
        solver.addVariable()
        assertTrue { solver.solve().values?.isNotEmpty()!! }
    }
}