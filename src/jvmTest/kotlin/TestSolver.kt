import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.kosat.Kosat
import org.kosat.solveCnf

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TestSolver {
    @Test
    fun `test example 1`() {

        val solver = Kosat(listOf(
            listOf(1, 2, -3), listOf(-1), listOf(-3)
        ))

        println(solver.solve())
        println(solver.getModel().toString())
    }

    @Test
    fun `test example 2`() {

        val solution = solveCnf(listOf(
            listOf(1, 2, -3, -4), listOf(-1), listOf(-3), listOf(3, -4)
        ))

        println(solution)
    }
}