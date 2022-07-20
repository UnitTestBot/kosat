package org.kosat

import kotlin.math.abs

class Kosat: Solver {
    override val numberOfVariables: Int
        get() = vars.size
    override val numberOfClauses: Int
        get() = clauses.size

    private val vars = mutableListOf<Lit>()
    private val clauses = mutableListOf<MutableList<Lit>>()
    private var model: List<Lit>? = null

    override fun addClause(literals: List<Lit>) {
        clauses.add(literals.toMutableList())
    }
    override fun addClause(lit: Lit) = addClause(listOf(lit))
    override fun addClause(lit1: Lit, lit2: Lit) = addClause(listOf(lit1, lit2))
    override fun addClause(lit1: Lit, lit2: Lit, lit3: Lit) = addClause(listOf(lit1, lit2, lit3))
    override fun addClause(literals: Iterable<Lit>) = addClause(literals.toList())

    override fun interrupt() {
        TODO("Not yet implemented")
    }

    override fun solve(): Boolean {
        model = CDCL(clauses, numberOfVariables).solve()
        return model != null
    }

    override fun solve(assumptions: List<Lit>): Boolean {
        TODO("Not yet implemented")
    }

    override fun solve(assumptions: Iterable<Lit>): Boolean = solve(assumptions.toList())

    override fun getModel(): List<Lit> = model ?: listOf()

    override fun getValue(lit: Lit): Boolean {
        return model?.get(abs(lit) - 1) == lit
    }
}


