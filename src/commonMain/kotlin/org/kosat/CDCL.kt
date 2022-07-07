package org.kosat

import kotlin.math.abs

//CDCL
fun solveCnf(cnf: CnfRequest): List<Int>? {
    val clauses = ArrayList(cnf.clauses.map { ArrayList(it.lit.toList()) })
    return NCDCL(clauses, cnf.vars).solve()
}


class NCDCL(private var clauses: ArrayList<ArrayList<Int>>, private val varsNumber: Int) {
    enum class VarStatus { TRUE, FALSE, UNDEFINED;
        operator fun not(): NCDCL.VarStatus {
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
    // values of variables
    private val vars: MutableList<VarState> = MutableList(varsNumber + 1) { VarState(VarStatus.UNDEFINED, -1, -1) }

    // all decisions and consequences
    private val trail: ArrayList<Int> = ArrayList()

    // decision level
    private var level: Int = 0
    fun solve(): List<Int>? {
        if (clauses.isEmpty()) return emptyList()

        while (true) {
            val conflictClause = propagate() ?: return null

            if (conflictClause != -1) {
                val lemma = analyzeConflict(clauses[conflictClause])
                addClause(lemma)
                backjump(lemma)
            }

            if (clauses.all { clause -> clause.any { lit -> getStatus(lit) == VarStatus.TRUE} }) {
                return vars.mapIndexed { index, v -> when (v.status) {
                    VarStatus.TRUE -> index
                    VarStatus.FALSE -> -index
                    else -> 0
                } }.sortedBy { abs(it) }.filter { it > 0 }
            }

            vars[0].status = VarStatus.FALSE
            val undefined = vars.indexOfFirst { it.status == VarStatus.UNDEFINED }
            level++
            vars[undefined].status = VarStatus.TRUE
            vars[undefined].level = level
            vars[undefined].clause = -1
        }
    }

    private fun addVariable(clause: Int, lit: Int) {
        setStatus(lit, VarStatus.TRUE)
        val v = abs(lit)
        vars[v].clause = clause
        vars[v].level = level
        trail.add(v)
    }

    private fun delVariable(v: Int) {
        setStatus(v, VarStatus.UNDEFINED)
        vars[v].clause = -1
        vars[v].level = -1
    }

    //returns index of conflict clause, or -1 if there is no conflict clause
    private fun propagate(): Int? { //TODO: watch literals
        clauses.forEachIndexed { ind, clause ->
            if (clause.isEmpty()) return null
            if (clause.isUnit()) addVariable(ind, clause.forced())
            clauses.indexOfFirst { it.all { lit -> getStatus(lit) == VarStatus.FALSE } }.let {
                if (it != -1) return it
            }
        }
        return -1
    }

    //return is clause a Unit or not
    private fun ArrayList<Int>.isUnit() = (size - 1 == this.count { getStatus(it) == VarStatus.FALSE })
    //return unfalse variable
    private fun ArrayList<Int>.forced() = this.first { getStatus(it) != VarStatus.FALSE }

    // change level, undefine variables and so on
    private fun backjump(clause: ArrayList<Int>) {
        val prevLevel = clause.map { vars[it].level }.sortedDescending().firstOrNull { it != level }
        require(prevLevel != null) { "previous level is null" }

        while (trail.size > 0 && vars[trail.last()].level > prevLevel) {
            delVariable(trail.removeLast())
        }
        level = prevLevel
    }

    // add clause and change structures for it
    private fun addClause(clause: ArrayList<Int>) {
        clauses.add(clause)
    }

    // analyze conflict and return new clause
    private fun analyzeConflict(conflict: ArrayList<Int>): ArrayList<Int> {

        val active = MutableList<Boolean>(varsNumber + 1) { false }
        val seen = MutableList<Boolean>(varsNumber + 1) { false }
        conflict.forEach { lit ->
            active[abs(lit)] = true
        }
        val lemma = ArrayList<Int>()
        var ind = trail.size - 1
        while (active.count { it } > 1) {

            val v = trail[ind--]
            seen[v] = true
            if (!active[v]) continue

            if (vars[v].clause == -1) {
                active.fill(false)
                lemma.add(v)
                break
            }
            clauses[vars[v].clause].forEach { u ->
                val current = abs(u)
                if (vars[current].level != level) lemma.add(u)
                else if (!seen[current]) active[current] = true
            }
            active[v] = false
        }
        active.indexOfFirst { it }.let { if (it != -1) lemma.add(it) }
        //require(active.count { it } > 0) { "There are no FUIP" }
        return lemma
    }
}