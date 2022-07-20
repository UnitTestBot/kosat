package org.kosat

import kotlin.math.abs

class Kosat(clauses: MutableList<MutableList<Lit>>): Solver {
    override val numberOfVariables get() = solver.varsNumber
    override val numberOfClauses get() = solver.clauses.size

    private var model: List<Lit>? = null
    private val solver = CDCL(clauses)

    override fun addVariable(): Int {
        return solver.newVar()
    }

    override fun addClause(literals: List<Lit>) {
        solver.addClause(literals.toMutableList())
    }

    override fun addClause(lit: Lit) = addClause(listOf(lit))
    override fun addClause(lit1: Lit, lit2: Lit) = addClause(listOf(lit1, lit2))
    override fun addClause(lit1: Lit, lit2: Lit, lit3: Lit) = addClause(listOf(lit1, lit2, lit3))
    override fun addClause(literals: Iterable<Lit>) = addClause(literals.toList())

    override fun interrupt() {
        TODO("Not yet implemented")
    }

    override fun solve(): Boolean {
        model = solver.solve()
        return model != null
    }

    override fun solve(assumptions: List<Lit>): Boolean {
        model = solver.solve(assumptions)
        return model != null
    }

    override fun solve(assumptions: Iterable<Lit>): Boolean = solve(assumptions.toList())

    override fun getModel(): List<Lit> = model ?: listOf()

    override fun getValue(lit: Lit): Boolean {
        return model?.get(abs(lit) - 1) == lit
    }
}


