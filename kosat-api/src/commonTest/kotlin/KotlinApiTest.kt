import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinApiTest {
    @Test
    fun tieShirtVarargs() {
        val solver = Kosat()

        assertFailsWith(IllegalArgumentException::class) { solver.newClause(1, 2) }

        solver.newVariable()
        solver.newVariable()

        solver.newClause(-1, 2)
        solver.newClause(1, 2)
        solver.newClause(-1, -2)

        assertFailsWith(IllegalStateException::class) { solver.getModel() }
        assertFailsWith(IllegalStateException::class) { solver.value(1) }
        assertFailsWith(IllegalArgumentException::class) { solver.value(0) }
        assertFailsWith(IllegalArgumentException::class) { solver.value(-5) }

        assertTrue(solver.solve())

        assertFalse(solver.value(1))
        assertTrue(solver.value(-1))
        assertTrue(solver.value(2))
        assertFalse(solver.value(-2))

        assertFailsWith(IllegalArgumentException::class) { solver.value(0) }
        assertFailsWith(IllegalArgumentException::class) { solver.value(-5) }

        assertEquals(listOf(false, true), solver.getModel())

        assertTrue(solver.solve(-1))
        assertFalse(solver.solve(1))
        assertTrue(solver.solve(-1, 2))
        assertFalse(solver.solve(-1, -2))

        solver.newClause(1, -2)
        assertFalse(solver.solve())

        assertFailsWith(IllegalStateException::class) { solver.getModel() }
    }

    @Test
    fun tieShirtIterables() {
        val solver = Kosat()

        solver.newVariable()
        solver.newVariable()

        solver.newClause(listOf(-1, 2))
        solver.newClause(setOf(1, 2))
        solver.newClause(listOf(-1, -2))

        assertTrue(solver.solve())

        assertEquals(listOf(false, true), solver.getModel())

        assertTrue(solver.solve(setOf(-1)))
        assertFalse(solver.solve(listOf(1)))
        assertTrue(solver.solve(listOf(-1, 2)))
        assertFalse(solver.solve(setOf(-1, -2)))

        solver.newClause(listOf(1, -2))
        assertFalse(solver.solve())
    }
}
