package org.kosat

import org.kosat.heuristics.Preprocessor
import org.kosat.heuristics.Restarter
import org.kosat.heuristics.Selector
import org.kosat.heuristics.VSIDS
import kotlin.math.abs

// CDCL
fun solveCnf(cnf: CnfRequest): List<Int>? {
    val clauses = (cnf.clauses.map { it.lits }).toMutableList()
    return CDCL(clauses.map { Clause(it) }.toMutableList(), cnf.vars).solve()
}

enum class SolverType {
    INCREMENTAL, NON_INCREMENTAL;
}

class CDCL(private val solverType: SolverType = SolverType.INCREMENTAL): Incremental {

    /** Interface **/

    val clauses = mutableListOf<Clause>()
    var varsNumber: Int = 0

    constructor() : this(mutableListOf<Clause>())

    constructor(
        initClauses: MutableList<Clause>,
        initVarsNumber: Int = 0,
        solverType: SolverType = SolverType.INCREMENTAL
    ) : this(solverType) {
        while (varsNumber < initVarsNumber) {
            addVariable()
        }
        initClauses.forEach { newClause(it) }
    }

    // public function for adding new variables
    override fun addVariable() {
        varsNumber++

        selector.addVariable()
        restarter.addVariable()

        vars.add(VarState(VarStatus.UNDEFINED, -1, -1))
        watchers.add(mutableSetOf())

        //TODO: add new vars everywhere it need!!!
    }

    // public function for adding new clauses
    fun newClause(clause: Clause) {
        val maxVar = clause.maxOfOrNull { abs(it) } ?: 0
        while (varsNumber < maxVar) {
            addVariable()
        }
        addClause(clause)
    }

    /** Variable states **/

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

    data class VarState(
        var status: VarStatus,
        var clause: Int,
        var level: Int,
    )

    // get status of literal
    fun getStatus(lit: Int): VarStatus {
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

    // values of variables
    val vars: MutableList<VarState> = MutableList(varsNumber + 1) { VarState(VarStatus.UNDEFINED, -1, -1) }

    // TODO why not abs..
    private fun litIndex(lit: Int): Int = abs(lit)

    /** Trail: **/

    // all decisions and consequences
    private val trail: MutableList<Int> = mutableListOf()

    // clear trail until given level
    fun clearTrail(until: Int = -1) {
        while (trail.isNotEmpty() && vars[trail.last()].level > until) {
            delVariable(trail.removeLast())
        }
    }

    /** Heuristics **/

    private val selector: Selector = VSIDS(varsNumber)

    private var preprocessor: Preprocessor? = null

    private val restarter = Restarter(this)

    /** Solve with assumptions **/

    // assumptions for incremental sat-solver
    private var assumptions: List<Int> = emptyList()

    fun solve(currentAssumptions: List<Int>): List<Int>? {
        require(solverType == SolverType.INCREMENTAL)

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

    private fun wrongAssumption(lit: Int) = getStatus(lit) == VarStatus.FALSE

    /** Solve **/

    fun solve(): List<Int>? {
        restarter.countOccurrence()
        restarter.updateSig()

        preprocessor = if (solverType == SolverType.NON_INCREMENTAL) {
            Preprocessor(this)
        } else {
            null
        }

        // extreme cases
        if (clauses.isEmpty()) return variableValues()
        if (clauses.any { it.size == 0 }) return null
        if (clauses.any { it.all { lit -> getStatus(lit) == VarStatus.FALSE } }) return null

        // branching heuristic
        selector.build(clauses)

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
                selector.update(lemma)

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

    // convert values to a possible satisfying result: if a variable less than 0 it's FALSE, otherwise it's TRUE
    // TODO where to place
    private fun variableValues(): List<Int> {
        if (solverType == SolverType.NON_INCREMENTAL) {
            preprocessor?.recoverAnswer()
        }

        return vars
            .mapIndexed { index, v ->
                when (v.status) {
                    VarStatus.TRUE -> index
                    VarStatus.FALSE -> -index
                    else -> index
                }
            }.sortedBy { litIndex(it) }.filter { litIndex(it) > 0 }
    }

    /** Two watchers **/

    // two watched literals heuristic; in watchers[i] set of clauses watched by variable i
    private val watchers = MutableList(varsNumber + 1) { mutableSetOf<Int>() }

    // list of unit clauses to propagate
    val units: MutableList<Int> = mutableListOf()

    // add watchers to new clause. Run in buildWatchers and addClause
    private fun addWatchers(clause: Clause, index: Int) {
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
    private fun addForLastInTrail(n: Int, clause: Clause, index: Int) {
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

    /** CDCL functions **/

    // decision level
    var level: Int = 0

    // check is all clauses satisfied or not
    private fun satisfiable() = clauses.all { clause -> clause.any { lit -> getStatus(lit) == VarStatus.TRUE } }

    // add clause and add watchers to it TODO: rename
    private fun addClause(clause: Clause) {
        clauses.add(clause)
        addWatchers(clause, clauses.lastIndex)

        restarter.addClause(clause)
        preprocessor?.addClause(clause)
    }

    // delete a variable from the trail TODO: rename
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

    // change level, undefine variables, clear units
    private fun backjump(clause: Clause) {
        level = clause.map { vars[litIndex(it)].level }.sortedDescending().firstOrNull { it != level } ?: 0

        clearTrail(level)

        // after backjump it's the only clause to propagate
        units.clear()
        units.add(clauses.lastIndex)
    }

    // add a literal to lemma if it hasn't been added yet
    private fun updateLemma(lemma: Clause, lit: Int) {
        if (lit !in lemma) {
            lemma.add(lit)
        }
    }

    // analyze conflict and return new clause
    private fun analyzeConflict(conflict: Clause): Clause {

        val active = MutableList(varsNumber + 1) { false }
        val lemma = Clause()

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
