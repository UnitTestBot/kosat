package org.kosat

typealias Lit = Int

interface Solver {
    /**
     * Number of variables added to the SAT solver.
     */
    val numberOfVariables: Int

    /**
     * Number of clauses added (via [addClause]) to the SAT solver.
     */
    val numberOfClauses: Int

    /**
     * add a clause to CNF as pure literals or list of literals
     */
    fun addClause(lit: Lit)
    fun addClause(lit1: Lit, lit2: Lit)
    fun addClause(lit1: Lit, lit2: Lit, lit3: Lit)
    fun addClause(literals: List<Lit>)
    fun addClause(literals: Iterable<Lit>)

    /**
     * Solve CNF without assumptions
     */
    fun solve(): Boolean

    /**
     *  Solve CNF with the passed `assumptions`
     */
    fun solve(assumptions: List<Lit>): Boolean
    fun solve(assumptions: Iterable<Lit>): Boolean

    /**
     * Interrupt the SAT solver.
     *
     * In general, after the SAT solver was interrupted, the call to [solve] returns `false`.
     * Note that the solving process may not stop immediately, since this operation is asynchronous.
     * Due to this, the call to [solve] may have time to return `true` if you happen
     * to call [interrupt] when the solver is about to actually solve the SAT problem.
     */
    fun interrupt()

    /**
     * Query the Boolean value of a literal.
     *
     * **Note:** the solver should be in the SAT state.
     * The result of [getValue] when the solver is not in the SAT state
     * depends on the backend implementation.
     */
    fun getValue(lit: Lit): Boolean

    /**
     * Query the satisfying assignment (model) for the SAT problem.
     *
     * In general, the Solver implementations construct the model on each call to [getModel].
     * The model could have the large size, so make sure to call this method only once.
     *
     * **Note:** the solver should be in the SAT state.
     * Solver return the latest model (cached)
     * even when the solver is already not in the SAT state (due to possibly new added clauses),
     * but it is advisable to query the model right after the call to [solve] which returned `true`.
     */
    fun getModel(): List<Lit>
}