import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.Lit
import org.kosat.SolveResult
import kotlin.math.abs

class Kosat {
    private var numberOfVariables: Int = 0
    private var numberOfClauses: Int = 0

    private var solver: CDCL = CDCL()

    private var result: SolveResult? = null
    private var model: List<Boolean>? = null

    private fun invalidateResult() {
        result = null
        model = null
    }

    private fun checkLiterals(literals: Iterable<Int>) {
        for (lit in literals) {
            if (lit == 0) {
                throw IllegalArgumentException("Clause must not contain 0")
            } else {
                val varIndex = abs(lit) - 1
                if (varIndex >= numberOfVariables) {
                    throw IllegalArgumentException(
                        "Variable of $lit is not defined (total variables: ${numberOfVariables})"
                    )
                }
            }
        }
    }

    fun reset() {
        numberOfVariables = 0
        numberOfClauses = 0
        solver = CDCL()
        invalidateResult()
    }

    fun newVariable() {
        invalidateResult()
        numberOfVariables++
        solver.newVariable()
    }

    fun newClause(clause: Iterable<Int>) {
        checkLiterals(clause)
        invalidateResult()
        numberOfClauses++
        solver.newClause(Clause.fromDimacs(clause))
    }

    fun newClause(vararg literals: Int) {
        newClause(literals.asIterable())
    }

    fun solve(vararg assumptions: Int): Boolean {
        return solve(assumptions.asIterable())
    }

    fun solve(assumptions: Iterable<Int>): Boolean {
        checkLiterals(assumptions)
        result = solver.solve(assumptions.map { Lit.fromDimacs(it) })
        return result == SolveResult.SAT
    }

    fun getModel(): List<Boolean> {
        if (result == null) {
            throw IllegalStateException("Model is not available before solving")
        } else if (result != SolveResult.SAT) {
            throw IllegalStateException("Model is not available if result is not SAT")
        }

        return solver.getModel().also { model = it }
    }

    fun value(literal: Int): Boolean {
        if (literal == 0) {
            throw IllegalArgumentException("Literal must not be 0")
        }

        val varIndex = abs(literal) - 1

        if (varIndex >= numberOfVariables) {
            throw IllegalArgumentException(
                "Variable of $literal is not defined (total variables: ${numberOfVariables})"
            )
        }

        if (model == null) {
            model = getModel()
        }

        return model!![varIndex] xor (literal < 0)
    }

    fun numberOfVariables(): Int {
        return numberOfVariables
    }

    fun numberOfClauses(): Int {
        return numberOfClauses
    }
}
