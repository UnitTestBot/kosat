import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.Lit
import org.kosat.SolveResult
import kotlin.math.abs

/**
 * Wrapper for the KoSAT solver.
 *
 * All the literals and variables are in DIMACS format (positive for variables,
 * negative for negated variables, 1-based indexing).
 *
 * *This class is not thread-safe.*
 */
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

    /**
     * Resets the solver to its initial state, removing all variables and
     * clauses.
     */
    fun reset() {
        numberOfVariables = 0
        numberOfClauses = 0
        solver = CDCL()
        invalidateResult()
    }

    /**
     * Return the number of variables in the solver.
     */
    fun numberOfVariables(): Int {
        return numberOfVariables
    }

    /**
     * Return the number of clauses in the solver.
     */
    fun numberOfClauses(): Int {
        return numberOfClauses
    }

    /**
     * Allocate a new variable in the solver.
     */
    fun newVariable() {
        invalidateResult()
        numberOfVariables++
        solver.newVariable()
    }

    /**
     * Add a new clause to the solver. The literals must be in DIMACS format
     * (positive for variables, negative for negated variables, 1-based
     * indexing). All variables must be defined with [newVariable] before adding
     * clauses.
     */
    fun newClause(clause: Iterable<Int>) {
        checkLiterals(clause)
        invalidateResult()
        numberOfClauses++
        solver.newClause(Clause.fromDimacs(clause))
    }

    /**
     * Add a new clause to the solver. The literals must be in DIMACS format
     * (positive for variables, negative for negated variables, 1-based
     * indexing). All variables must be defined with [newVariable] before adding
     * clauses.
     */
    fun newClause(vararg literals: Int) {
        newClause(literals.asIterable())
    }

    /**
     * Solve the SAT problem with the given assumptions, if any. Assumptions are
     * literals in DIMACS format.
     */
    fun solve(vararg assumptions: Int): Boolean {
        return solve(assumptions.asIterable())
    }

    /**
     * Solve the SAT problem with the given assumptions
     */
    fun solve(assumptions: Iterable<Int>): Boolean {
        checkLiterals(assumptions)
        result = solver.solve(assumptions.map { Lit.fromDimacs(it) })
        return result == SolveResult.SAT
    }

    /**
     * If the problem is SAT, return the model as a list of booleans. [solve]
     * must be called before calling this method. The model is cached after the
     * first call and reset when clause or variable is incrementally is added.
     */
    fun getModel(): List<Boolean> {
        if (result == null) {
            throw IllegalStateException("Model is not available before solving")
        } else if (result != SolveResult.SAT) {
            throw IllegalStateException("Model is not available if result is not SAT")
        }

        return solver.getModel().also { model = it }
    }

    /**
     * If the problem is SAT, return the value of the given literal in the
     * model. [solve] must be called before calling this method.
     */
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
}
