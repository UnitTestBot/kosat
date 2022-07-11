package org.kosat

import kotlin.math.abs

//CDCL
fun solveCnf(cnf: CnfRequest): List<Int>? {
    val clauses = ArrayList(cnf.clauses.map { ArrayList(it.lit.toList()) })
    return CDCL(clauses, cnf.vars).solve()
}


class CDCL(private var clauses: ArrayList<ArrayList<Int>>, private val varsNumber: Int) {
    enum class VarStatus {
        TRUE, FALSE, UNDEFINED;

        operator fun not(): VarStatus {
            return when {
                this == TRUE -> FALSE
                this == FALSE -> TRUE
                else -> UNDEFINED
            }
        }
    }

    private fun getStatus(lit: Int): VarStatus {
        if (vars[abs(lit)].status == VarStatus.UNDEFINED) return VarStatus.UNDEFINED
        if (lit < 0) return !vars[-lit].status
        return vars[lit].status
    }
    private fun setStatus(lit: Int, status: VarStatus) {
        if (lit < 0) vars[-lit].status = !status
        else vars[lit].status = status
    }

    data class VarState(var status: VarStatus, var clause: Int, var level: Int)

    // converting values to a possible satisfying result: if a variable less than 0 it's FALSE, otherwise it's TRUE
    private fun variableValues() = vars
        .mapIndexed { index, v ->
            when (v.status) {
                VarStatus.TRUE -> index
                VarStatus.FALSE -> -index
                else -> index
            }
        }.sortedBy { abs(it) }.filter { abs(it) > 0 }
    // values of variables
    private val vars: MutableList<VarState> = MutableList(varsNumber + 1) { VarState(VarStatus.UNDEFINED, -1, -1) }
    // all decisions and consequences
    private val trail: ArrayList<Int> = ArrayList()
    // decision level
    private var level: Int = 0

    fun solve(): List<Int>? {
        if (clauses.isEmpty()) return emptyList()

        while (true) {
            val conflictClause = propagate()
            if (conflictClause != -1) {
                if (level == 0) return null //in case there is a conflict in CNF
                val lemma = analyzeConflict(clauses[conflictClause]) //looks for conflict lemma
                addClause(lemma)
                backjump(lemma)
                continue
            }

            if (satisfiable()) {
                return variableValues()
            }

            level++
            addVariable(-1, vars.firstUndefined())
        }
    }

    // checks is clause satisfied or not
    private fun satisfiable() = clauses.all { clause -> clause.any { lit -> getStatus(lit) == VarStatus.TRUE } }
    // checks if all clauses are satisfied and return answer
    private fun MutableList<VarState>.firstUndefined() = this
        .drop(1)
        .indexOfFirst { it.status == VarStatus.UNDEFINED } + 1


    // add a variable to the trail
    private fun addVariable(clause: Int, lit: Int): Boolean {
        if (getStatus(lit) != VarStatus.UNDEFINED) return false

        setStatus(lit, VarStatus.TRUE)
        val v = abs(lit)
        vars[v].clause = clause
        vars[v].level = level
        trail.add(v)
        return true
    }
    // del a variable from the trail
    private fun delVariable(v: Int) {
        setStatus(v, VarStatus.UNDEFINED)
        vars[v].clause = -1
        vars[v].level = -1
    }

    // propogates; returns index of conflict clause, or -1 if there is no conflict clause
    private fun propagate(): Int { //TODO: watch literals
        clauses.indexOfFirst { it.all { lit -> getStatus(lit) == VarStatus.FALSE } }.let {
            if (it != -1) return it
        }
        clauses.forEachIndexed { ind, clause ->
            if (clause.isUnit() && addVariable(ind, clause.forced())) {
                return propagate()
            }
        }
        return -1
    }

    //return is clause a unit or not
    private fun ArrayList<Int>.isUnit() = (size - 1 == this.count { getStatus(it) == VarStatus.FALSE })
    //return unfalse variable in unit
    private fun ArrayList<Int>.forced() = this.first { getStatus(it) != VarStatus.FALSE }

    //change level, undefine variables and so on
    private fun backjump(clause: ArrayList<Int>) {
        level = clause.map { vars[abs(it)].level }.sortedDescending().firstOrNull { it != level } ?: 0

        while (trail.size > 0 && vars[trail.last()].level > level) {
            delVariable(trail.removeLast())
        }
    }
    // add clause and change structures for it
    private fun addClause(clause: ArrayList<Int>) {
        clauses.add(ArrayList(clause.map { it }))
    }

    // add a lit to lemma if it hasn't been added yet
    private fun updateLemma(lemma: ArrayList<Int>, lit: Int) {
        if (lemma.find { it == lit } == null) {
            lemma.add(lit)
        }
    }

    // analyze conflict and return new clause
    private fun analyzeConflict(conflict: ArrayList<Int>): ArrayList<Int> {

        val active = MutableList(varsNumber + 1) { false }
        val lemma = ArrayList<Int>()

        conflict.forEach { lit ->
            if (vars[abs(lit)].level == level) active[abs(lit)] = true
            else updateLemma(lemma, lit)
        }
        var ind = trail.size - 1
        while (active.count { it } > 1) {

            val v = trail[ind--]
            if (!active[v]) continue

            clauses[vars[v].clause].forEach { u ->
                val current = abs(u)
                if (vars[current].level != level) updateLemma(lemma, u)
                else if (current != v) active[current] = true
            }
            active[v] = false
        }
        active.indexOfFirst { it }.let { v ->
            if (v != -1) updateLemma(lemma, if (getStatus(v) == VarStatus.TRUE) -v else v)
        }
        return lemma
    }
}