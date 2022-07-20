package org.kosat

import kotlin.math.abs

class Clause(val lit: MutableList<Int>)

class CnfRequest(val vars: Int, val clauses: List<Clause>)

/**
 * Reads [CnfRequest]'s assuming [s] is formatted according [Simplified DIMACS](http://www.satcompetition.org/2004/format-solvers2004.html)
 */
class Scanner(val s: String) {
    private var pos = 0
    fun hasNext(): Boolean {
        while (pos < s.length && s[pos].isWhitespace())
            pos++
        return pos < s.length
    }

    fun next(): String {
        while (pos < s.length && s[pos].isWhitespace())
            pos++

        val start = pos
        while (pos < s.length && !s[pos].isWhitespace())
            pos++

        return s.substring(start, pos)
    }

    fun nextLine() {
        while (pos < s.length)
            if (s[pos++] == '\n') break
    }

    fun nextInt(): Int = next().toInt()
}


fun readCnfRequests(dimacs: String) = sequence {
    val scanner = Scanner(dimacs)

    while (scanner.hasNext()) {
        val token = scanner.next()

        if (token == "c") {
            scanner.nextLine()
            continue
        } //skip comment

        if (token == "%") {
            break
        }

        if (token != "p")
            error ("Illegal token $token. Only 'c' and 'p' command are supported")

        val cnf = scanner.next()
        if (cnf != "cnf")
            error ("Illegal request $cnf. Only 'cnf' supported")

        val vars = scanner.nextInt() //don't need this variable
        val clauses = List(scanner.nextInt()) { mutableListOf<Int>()}
        for (i in clauses.indices) {
            while (true) {
                val nxt = scanner.nextInt()

                if (nxt == 0) break
                else clauses[i].add(nxt)
            }
        }

        yield(CnfRequest(vars, clauses.map { Clause(it) }))
    }
}


fun processCnfRequests(requests: Sequence<CnfRequest>) = buildString {
    for (cnf in requests) {
        appendLine("v Start processing CNF request with ${cnf.vars} variables and ${cnf.clauses.size} clauses")

        val model: List<Int>? = solveCnf(cnf)

        if (model == null) {
            appendLine("s UNSATISFIABLE")
            continue
        }

        appendLine("s SATISFIABLE")
        if (model.isEmpty())
            appendLine("c Done: formula is tautology. Any solution satisfies it.")
        else {
            appendLine("v " + model.joinToString(" "))
            appendLine("c Done")
        }
    }
}


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


