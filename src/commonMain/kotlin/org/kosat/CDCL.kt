package org.kosat

import org.kosat.heuristics.Preprocessor
import org.kosat.heuristics.Restarter
import org.kosat.heuristics.Selector
import org.kosat.heuristics.VSIDS
import kotlin.math.abs
import kotlin.math.max

// CDCL
fun solveCnf(cnf: CnfRequest): List<Int>? {
    val clauses = (cnf.clauses.map { it.lits }).toMutableList()
    return CDCL(clauses, cnf.vars).solve()
}


class CDCL(val clauses: MutableList<MutableList<Int>>, initNumber: Int = 0) {
    var varsNumber = initNumber
        private set

    private val selector: Selector = VSIDS(varsNumber)

    init {
        // set varsNumber equal to either initNumber(from constructor of class) either maximal variable from cnf
        varsNumber =
            max(initNumber, clauses.flatten().let { all -> if (all.isNotEmpty()) all.maxOf { abs(it) } else 0 })
    }

    enum class VarStatus {
        TRUE, FALSE, UNDEFINED;

        operator fun not(): VarStatus {
            return when (this) {
                TRUE -> FALSE
                FALSE -> TRUE
                UNDEFINED -> UNDEFINED
            }
        }
    }

    // all decisions and consequences
    private val trail: MutableList<Int> = mutableListOf()

    // clear trail until given level
    fun clearTrail(until: Int = -1) {
        while (trail.isNotEmpty() && vars[trail.last()].level > until) {
            delVariable(trail.removeLast())
        }
    }

    // get status of literal
    private fun getStatus(lit: Int): VarStatus {
        if (vars[litIndex(lit)].status == VarStatus.UNDEFINED) return VarStatus.UNDEFINED
        if (lit < 0) return !vars[-lit].status
        return vars[lit].status
    }

    // set status for literal
    private fun setStatus(lit: Int, status: VarStatus) {
        if (lit < 0) {
            vars[-lit].status = !status
        } else {
            vars[lit].status = status
        }
    }

    data class VarState(
        var status: VarStatus,
        var clause: Int,
        var level: Int,
    )

    // convert values to a possible satisfying result: if a variable less than 0 it's FALSE, otherwise it's TRUE
    private fun variableValues(): List<Int> {
        preprocessor?.recoverAnswer(vars)
        return vars
            .mapIndexed { index, v ->
                when (v.status) {
                    VarStatus.TRUE -> index
                    VarStatus.FALSE -> -index
                    else -> index
                }
            }.sortedBy { litIndex(it) }.filter { litIndex(it) > 0 }
    }
    
    private var preprocessor: Preprocessor? = null

    // values of variables
    private val vars: MutableList<VarState> = MutableList(varsNumber + 1) { VarState(VarStatus.UNDEFINED, -1, -1) }
    
    // decision level
    var level: Int = 0

    // two watched literals heuristic; in watchers[i] set of clauses watched by variable i
    private val watchers = MutableList(varsNumber + 1) { mutableSetOf<Int>() }

    private fun litIndex(lit: Int): Int = abs(lit)

    // list of unit clauses to propagate
    val units: MutableList<Int> = mutableListOf()

    private val restarter = Restarter(this)

    // assumptions for incremental sat-solver
    private var assumptions: List<Int> = emptyList()

    fun solve(currentAssumptions: List<Int>): List<Int>? {
        assumptions = currentAssumptions
        selector.initAssumptions(assumptions)
        val result = solve()
        assumptions.forEach {
            if (getStatus(it) == VarStatus.FALSE) {
                assumptions = emptyList()
                return null
            }
        }
        assumptions = emptyList()
        return result
    }

    fun solve(): List<Int>? {
        restarter.countOccurrence()
        restarter.updateSig()

        // simplifying given cnf formula
        // preprocessing()
        if (assumptions.isEmpty()) { //TODO users give the state
            //FIXME
            //preprocessor = Preprocessor(clauses.map { Clause(it) }.toMutableList(), varsNumber)
        }

        // extreme cases
        if (clauses.isEmpty()) return variableValues()
        if (clauses.any { it.size == 0 }) return null
        if (clauses.any { it.all { lit -> getStatus(lit) == VarStatus.FALSE } }) return null

        // branching heuristic
        selector.build(clauses.map { Clause(it) })

        // TODO: remove
        buildWatchers()

        // main loop
        while (true) {
            val conflictClause = propagate()
            if (conflictClause != -1) {
                // in case there is a conflict in CNF and trail is already in 0 state
                if (level == 0) return null

                // build new clause by conflict clause
                val lemma = analyzeConflict(clauses[conflictClause])

                addClause(lemma)
                backjump(lemma)

                restarter.update()

                // VSIDS
                selector.update(Clause(lemma))

                continue
            }

            // If (the problem is already) SAT, return the current assignment
            if (satisfiable()) {
                val model = variableValues()
                clearTrail(0)
                return model
            }

            // try to guess variable
            level++
            val nextVariable = selector.nextDecisionVariable(vars, level)

            // Check that assumption we want to make isn't controversial
            if (level <= assumptions.size && wrongAssumption(nextVariable)) {
                clearTrail(0)
                return null
            }
            setVariableValues(-1, nextVariable)
        }
    }

    private fun wrongAssumption(lit: Int) = getStatus(lit) == VarStatus.FALSE

    // add clause and add watchers to it
    private fun addClause(clause: MutableList<Int>) {
        clauses.add(clause)
        addWatchers(clause, clauses.lastIndex)

        restarter.addClause(clause)
    }

    // public function for adding new clauses
    fun newClause(clause: MutableList<Int>) {
        addClause(clause)
        val maxVar = clause.maxOf { abs(it) }
        while (newVar() < maxVar) {
        }
    }

    // public function for adding new variables
    fun newVar(): Int {
        selector.addVariable()
        varsNumber++
        vars.add(VarState(VarStatus.UNDEFINED, -1, -1))
        return varsNumber
    }

    // run only once in the beginning TODO: not anymore
    private fun buildWatchers() {
        while (watchers.size > varsNumber + 1) {
            watchers.removeLast()
        }
        clauses.forEachIndexed { index, clause ->
            addWatchers(clause, index)
        }
    }

    // add watchers to clause. Run in buildWatchers and addClause
    private fun addWatchers(clause: MutableList<Int>, index: Int) {
        // every clause of size 1 watched by it only variable
        if (clause.size == 1) {
            watchers[litIndex(clause[0])].add(index)
            units.add(index)
            return
        }
        val undef = clause.count { getStatus(it) == VarStatus.UNDEFINED }

        if (undef >= 2) {
            // in case there are at least 2 undefined variable in clause
            val a = clause.indexOfFirst { getStatus(it) == VarStatus.UNDEFINED }
            val b = clause.drop(a + 1).indexOfFirst { getStatus(it) == VarStatus.UNDEFINED } + a + 1
            watchers[litIndex(clause[a])].add(index)
            watchers[litIndex(clause[b])].add(index)
        } else if (undef == 1) {
            // in case there are exactly 1 undefined variable in clause (only in case newClause)
            val a = clause.indexOfFirst { getStatus(it) == VarStatus.UNDEFINED }
            watchers[litIndex(clause[a])].add(index)
            if (clause.count { getStatus(it) == VarStatus.FALSE } == clause.size - 1) {
                units.add(index)
            }
            addForLastInTrail(1, clause, index)
        } else {
            // for clauses added by conflict and by newClause if it already has all defined literals
            addForLastInTrail(2, clause, index)
        }
    }

    // find n last assigned variables from given clause
    private fun addForLastInTrail(n: Int, clause: List<Int>, index: Int) {
        var cnt = 0
        val clauseVars = clause.map { litIndex(it) }
        // want to watch to last n literals from trail
        for (ind in trail.lastIndex downTo 0) {
            if (trail[ind] in clauseVars) {
                cnt++
                watchers[trail[ind]].add(index)
                if (cnt == n) {
                    return
                }
            }
        }
    }

    // check is all clauses satisfied or not
    private fun satisfiable() = clauses.all { clause -> clause.any { lit -> getStatus(lit) == VarStatus.TRUE } }

    // add a variable to the trail and update watchers of clauses linked to this variable
    private fun setVariableValues(clause: Int, lit: Int): Boolean {
        if (getStatus(lit) != VarStatus.UNDEFINED) return false

        setStatus(lit, VarStatus.TRUE)
        val v = litIndex(lit)
        vars[v].clause = clause
        vars[v].level = level
        trail.add(v)
        updateWatchers(lit)
        return true
    }

    // update watchers for clauses linked with literal
    private fun updateWatchers(lit: Int) {
        val clausesToRemove = mutableSetOf<Int>()
        watchers[litIndex(lit)].forEach { brokenClause ->
            val undef = clauses[brokenClause].count { getStatus(it) == VarStatus.UNDEFINED }
            val firstTrue = clauses[brokenClause].firstOrNull { getStatus(it) == VarStatus.TRUE }
            if (undef > 1) {
                val newWatcher = clauses[brokenClause].first {
                    getStatus(it) == VarStatus.UNDEFINED && brokenClause !in watchers[litIndex(it)]
                }
                watchers[litIndex(newWatcher)].add(brokenClause)
                clausesToRemove.add(brokenClause)
            } else if (undef == 1 && firstTrue == null) {
                units.add(brokenClause)
            }
        }
        watchers[litIndex(lit)].removeAll(clausesToRemove)
    }

    // delete a variable from the trail
    private fun delVariable(v: Int) {
        setStatus(v, VarStatus.UNDEFINED)
        vars[v].clause = -1
        vars[v].level = -1
    }

    // return index of conflict clause, or -1 if there is no conflict clause
    private fun propagate(): Int {
        while (units.size > 0) {
            val clause = units.removeLast()
            if (clauses[clause].any { getStatus(it) == VarStatus.TRUE }) continue

            // guarantees that clauses in unit don't become defined incorrect
            require(clauses[clause].any { getStatus(it) != VarStatus.FALSE })

            val lit = clauses[clause].first { getStatus(it) == VarStatus.UNDEFINED }
            // check if we get a conflict
            watchers[litIndex(lit)].forEach { brokenClause ->
                if (brokenClause != clause) {
                    val undef = clauses[brokenClause].count { getStatus(it) == VarStatus.UNDEFINED }
                    if (undef == 1 && -lit in clauses[brokenClause] && clauses[brokenClause].all { getStatus(it) != VarStatus.TRUE }) {
                        // quick fix for analyzeConflict
                        setStatus(lit, VarStatus.TRUE)
                        val v = litIndex(lit)
                        vars[v].clause = clause
                        vars[v].level = level
                        trail.add(v)
                        return brokenClause
                    }
                }
            }
            setVariableValues(clause, lit)
        }

        return -1
    }

    // change level, undefine variables, clear units
    private fun backjump(clause: MutableList<Int>) {
        level = clause.map { vars[litIndex(it)].level }.sortedDescending().firstOrNull { it != level } ?: 0

        clearTrail(level)

        // after backjump it's the only clause to propagate
        units.clear()
        units.add(clauses.lastIndex)
    }

    // add a literal to lemma if it hasn't been added yet
    private fun updateLemma(lemma: MutableList<Int>, lit: Int) {
        if (lit !in lemma) {
            lemma.add(lit)
        }
    }

    // analyze conflict and return new clause
    private fun analyzeConflict(conflict: MutableList<Int>): MutableList<Int> {

        val active = MutableList(varsNumber + 1) { false }
        val lemma = mutableListOf<Int>()

        conflict.forEach { lit ->
            if (vars[litIndex(lit)].level == level) {
                active[litIndex(lit)] = true
            } else {
                updateLemma(lemma, lit)
            }
        }
        var ind = trail.size - 1
        while (active.count { it } > 1) {

            val v = trail[ind--]
            if (!active[v]) continue

            clauses[vars[v].clause].forEach { u ->
                val current = litIndex(u)
                if (vars[current].level != level) {
                    updateLemma(lemma, u)
                } else if (current != v) {
                    active[current] = true
                }
            }
            active[v] = false
        }
        active.indexOfFirst { it }.let { v ->
            if (v != -1) {
                updateLemma(lemma, if (getStatus(v) == VarStatus.TRUE) -v else v)
            }
        }
        return lemma
    }






}
