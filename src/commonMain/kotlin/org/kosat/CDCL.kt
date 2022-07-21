package org.kosat

import kotlin.math.abs
import kotlin.math.max

// CDCL
fun solveCnf(cnf: CnfRequest): List<Int>? {
    val clauses = (cnf.clauses.map { it.lit }).toMutableList()
    val solver = Kosat(clauses, cnf.vars)
    return if (solver.solve()) solver.getModel() else null
}

class CDCL(val clauses: MutableList<MutableList<Int>>, initNumber: Int = 0) {
    var varsNumber = initNumber
        private set

    init {
        // set varsNumber equal to either initNumber(from constructor of class) either maximal variable from cnf
        varsNumber = max(initNumber, clauses.flatten().let { all -> if (all.isNotEmpty()) all.maxOf { abs(it) } else 0})
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

    private fun getStatus(lit: Int): VarStatus {
        if (vars[litIndex(lit)].status == VarStatus.UNDEFINED) return VarStatus.UNDEFINED
        if (lit < 0) return !vars[-lit].status
        return vars[lit].status
    }

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
    private fun variableValues() = vars
        .mapIndexed { index, v ->
            when (v.status) {
                VarStatus.TRUE -> index
                VarStatus.FALSE -> -index
                else -> index
            }
        }.sortedBy { litIndex(it) }.filter { litIndex(it) > 0 }

    // values of variables
    private val vars: MutableList<VarState> = MutableList(varsNumber + 1) { VarState(VarStatus.UNDEFINED, -1, -1) }

    // all decisions and consequences
    private val trail: MutableList<Int> = mutableListOf()

    // decision level
    private var level: Int = 0

    // two watched literals heuristic; in watchers[i] set of clauses watched by variable i
    private val watchers = MutableList(varsNumber + 1) { mutableSetOf<Int>() }
    private fun litIndex(lit: Int): Int = abs(lit)

    // list of unit clauses to propagate
    private val units: MutableList<Int> = mutableListOf()

    // assumptions for incremental sat-solver
    private var assumptions: List<Int> = emptyList()

    // clear trail until given level
    private fun clearTrail(until: Int = -1) {
        while (trail.isNotEmpty() && vars[trail.last()].level > until) {
            delVariable(trail.removeLast())
        }
    }

    fun solveWithAssumptions(currentAssumptions: List<Int> = emptyList()): List<Int>? {
        assumptions = currentAssumptions
        val result = solve()
        assumptions.forEach {
            if (getStatus(it) == VarStatus.FALSE) {
                assumptions = emptyList()
                return null
            }
        }
        assumptions = emptyList()
        return  result
    }

    // remove clauses which contain x and -x
    private fun removeUselessClauses() {
        clauses.removeAll { clause -> clause.any { -it in clause } }
    }


    fun solve(): List<Int>? {
        removeUselessClauses()

        // extreme cases
        if (clauses.isEmpty()) return emptyList()
        if (clauses.any { it.size == 0 }) return null
        if (clauses.any { it.all { lit -> getStatus(lit) == VarStatus.FALSE }}) return null

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

                // VSIDS
                numberOfConflicts++
                if (numberOfConflicts == decay) {
                    // update scores
                    numberOfConflicts = 0
                    score.forEachIndexed { ind, _ -> score[ind] /= divisionCoeff }
                    lemma.forEach { lit -> score[litIndex(lit)]++ }
                }

                continue
            }

            // If (the problem is already) SAT, return the current assignment
            if (satisfiable()) {
                clearTrail(0)
                return variableValues()
            }

            // try to guess variable
            level++
            val nextVariable = getNextVariable(level)

            // Check that assumption we want to make isn't controversial
            if (wrongAssumption(nextVariable)) {
                clearTrail(0)
                return null
            }
            addVariable(-1, nextVariable)
        }
    }

    private fun getNextVariable(level: Int): Int {
        return if (level > assumptions.size) {
            vsids()
        } else {
            return assumptions[level - 1]
        }
    }

    private fun wrongAssumption(lit: Int) = getStatus(lit) == VarStatus.FALSE

    // add clause and add watchers to it
    private fun addClause(clause: MutableList<Int>) {
        clauses.add(clause)
        addWatchers(clause, clauses.lastIndex)
    }

    fun newClause(clause: MutableList<Int>) {
        addClause(clause)
        val maxVar = clause.maxOf { abs(it) }
        while (newVar() < maxVar) { }
    }

    fun newVar(): Int {
        varsNumber++
        vars.add(VarState(VarStatus.UNDEFINED, -1, -1))
        return varsNumber
    }

    // run only once in the beginning
    private fun buildWatchers() {
        clauses.forEachIndexed { index, clause ->
            addWatchers(clause, index)
        }
    }

    // add watchers to clause. Run in buildWatchers and addClause
    private fun addWatchers(clause: MutableList<Int>, index: Int) {
        if (clause.size == 1) {
            watchers[litIndex(clause[0])].add(index)
            units.add(index)
            return
        }
        val undef = clause.count { getStatus(it) == VarStatus.UNDEFINED }

        if (undef >= 2) {
            val a = clause.indexOfFirst { getStatus(it) == VarStatus.UNDEFINED }
            val b = clause.drop(a + 1).indexOfFirst { getStatus(it) == VarStatus.UNDEFINED } + a + 1
            watchers[litIndex(clause[a])].add(index)
            watchers[litIndex(clause[b])].add(index)
        } else if (undef == 1) {
            val a = clause.indexOfFirst { getStatus(it) == VarStatus.UNDEFINED }
            watchers[litIndex(clause[a])].add(index)
            if (clause.count { getStatus(it) == VarStatus.FALSE } == clause.size - 1) {
                units.add(index)
            }
            addForLastInTrail(1, clause, index)
        } else {
            // for clauses added by conflict and by newClause if it already controversial
            addForLastInTrail(2, clause, index)
        }
    }

    // find n last assigned variables from given clause
    private fun addForLastInTrail(n: Int, clause: List<Int>, index: Int) {
        var cnt = 0
        val clauseVars = clause.map { litIndex(it) }
        for (ind in trail.lastIndex downTo 0) { // want to watch on last 2 literals from trail for conflict clause
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
    private fun addVariable(clause: Int, lit: Int): Boolean {
        if (getStatus(lit) != VarStatus.UNDEFINED) return false

        setStatus(lit, VarStatus.TRUE)
        val v = litIndex(lit)
        vars[v].clause = clause
        vars[v].level = level
        trail.add(v)
        updateWatchers(lit)
        return true
    }

    // update watchers for clauses linked with lit
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

    // del a variable from the trail
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
            addVariable(clause, lit)
        }

        return -1
    }

    // change level, undefine variables, clear units
    private fun backjump(clause: MutableList<Int>) {
        level = clause.map { vars[litIndex(it)].level }.sortedDescending().firstOrNull { it != level } ?: 0

        clearTrail(level)

        units.clear()
        units.add(clauses.lastIndex) // after backjump it's the only clause to propagate
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

    // VSIDS
    private val score = MutableList(varsNumber + 1) {
        clauses.count { clause -> clause.contains(it) || clause.contains(-it) }.toDouble()
    }
    private val decay = 50
    private val divisionCoeff = 2.0
    private var numberOfConflicts = 0

    private fun vsids(): Int {
        var ind = -1
        for (i in 1..varsNumber) {
            if (vars[i].status == VarStatus.UNDEFINED && (ind == -1 || score[ind] < score[i])) {
                ind = i
            }
        }
        return ind
    }
}
