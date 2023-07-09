import com.github.lipen.satlib.op.exactlyOne
import com.github.lipen.satlib.solver.MiniSatSolver
import com.github.lipen.satlib.solver.solve
import kotlin.test.Test
import kotlin.test.assertTrue

internal class TestKotlinSatlib {
    @Test
    fun runMiniSat() {
        with(MiniSatSolver()) {
            val x = newLiteral()
            val y = newLiteral()
            val z = newLiteral()

            println("Encoding exactlyOne(x, y, z)")
            exactlyOne(x, y, z)

            println("nVars = $numberOfVariables")
            println("nClauses = $numberOfClauses")

            println("Solving...")
            assertTrue(solve())
            println("model = ${getModel()}")

            println("Solving with assumption x=true...")
            assertTrue(solve(x))
            println("model = ${getModel()}")
            assertTrue(getValue(x))

            println("Solving with assumption y=true...")
            assertTrue(solve(y))
            println("model = ${getModel()}")
            assertTrue(getValue(y))

            println("Solving with assumption z=true...")
            assertTrue(solve(z))
            println("model = ${getModel()}")
            assertTrue(getValue(z))

            println("Everything OK.")
        }
    }
}
