package org.kosat

import kotlin.math.abs

fun solveWithAssumptions(cnf: CnfRequest): List<Int>? {
    val clauses = (cnf.clauses.map { it.lit }).toMutableList()
    val solver = KoSat(clauses, cnf.vars)
    return if (solver.solve()) solver.getModel() else null
}

fun solveCnf(cnf: CnfRequest): List<Int>? {
    val clauses = (cnf.clauses.map { it.lit }).toMutableList()
    return NonIncremental(clauses, cnf.vars).solve()
}

class KoSat(clauses: MutableList<MutableList<Lit>>, vars: Int = 0): Solver {
    override val numberOfVariables get() = solver.varsNumber
    override val numberOfClauses get() = solver.clauses.size

    private var model: List<Lit>? = null
    private val solver = Incremental(clauses, vars)

    override fun addVariable(): Int {
        return solver.newVar()
    }

    override fun addClause(literals: List<Lit>) {
        solver.newClause(literals.toMutableList())
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
        model = solver.solveWithAssumptions(assumptions)
        return model != null
    }

    override fun solve(assumptions: Iterable<Lit>): Boolean = solve(assumptions.toList())

    override fun getModel(): List<Lit> = model ?: listOf()

    override fun getValue(lit: Lit): Boolean {
        return model?.get(abs(lit) - 1) == lit
    }
}