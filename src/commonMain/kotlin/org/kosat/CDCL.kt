package org.kosat

import org.kosat.heuristics.Preprocessor
import org.kosat.heuristics.Restarter
import org.kosat.heuristics.VariableSelector
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

class CDCL(private val solverType: SolverType = SolverType.INCREMENTAL) : Incremental {

    val clauses = mutableListOf<Clause>()
    var varsNumber: Int = 0

    // values of variables
    val vars: MutableList<VarState> = MutableList(varsNumber + 1) { VarState(VarStatus.UNDEFINED, null, -1, -1) }

    // all decisions and consequences
    private val trail: MutableList<Int> = mutableListOf()

    // two watched literals heuristic; in watchers[i] set of clauses watched by variable i
    private val watchers = MutableList(varsNumber + 1) { mutableSetOf<Clause>() }

    // list of unit clauses to propagate
    val units: MutableList<Clause> = mutableListOf()

    var reduceNumber = 6000.0
    var reduceIncrement = 500.0

    // decision level
    var level: Int = 0

    private val minimizeMarks = MutableList(varsNumber * 2 + 1) { 0 }
    var mark = 0

    /** Heuristics **/

    private val variableSelector: VariableSelector = VSIDS(varsNumber)

    private var preprocessor: Preprocessor? = null

    private val restarter = Restarter(this)

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
        var clause: Clause?,
        var level: Int,
        var trailIndex: Int
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

    // TODO why not abs..
    private fun litIndex(lit: Int): Int = abs(lit)

    /** Interface **/

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
        phaseSaving = MutableList(varsNumber + 1) { VarStatus.UNDEFINED }
    }

    // public function for adding new variables
    override fun addVariable() {
        varsNumber++

        variableSelector.addVariable()
        restarter.addVariable()

        vars.add(VarState(VarStatus.UNDEFINED, null, -1, -1))
        watchers.add(mutableSetOf())

        minimizeMarks.add(0)
        minimizeMarks.add(0)
    }

    // public function for adding new clauses
    fun newClause(clause: Clause) {
        val maxVar = clause.maxOfOrNull { abs(it) } ?: 0
        while (varsNumber < maxVar) {
            addVariable()
        }
        clause.locked = true
        addClause(clause)
    }

    /** Trail: **/

    // clear trail until given level
    fun clearTrail(until: Int = -1) {
        while (trail.isNotEmpty() && vars[trail.last()].level > until) {
            delVariable(trail.removeLast())
        }
    }

    /** Solve with assumptions **/

    // assumptions for incremental sat-solver
    private var assumptions: List<Int> = emptyList()

    // phase saving
    private var phaseSaving: MutableList<VarStatus> = mutableListOf()

    fun solve(currentAssumptions: List<Int>): List<Int>? {
        require(solverType == SolverType.INCREMENTAL)

        assumptions = currentAssumptions
        variableSelector.initAssumptions(assumptions)
        val result = solve()
        if (result == null) {
            assumptions = emptyList()
            return null
        }
        assumptions.forEach { lit ->
            if (result.find { it == -lit } != null) {
                assumptions = emptyList()
                return null
            }
        }
        assumptions = emptyList()
        return result
    }


    fun reduceDB() {
        clauses.sortBy { -it.lbd }
        val lim = clauses.size / 2
        var i = 0
        clauses.forEach { clause ->
            if (!clause.locked && i < lim) {
                i++
                clause.deleted = true
            }
        }
        clauses.removeAll { it.deleted }
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
        if (clauses.any { it.isEmpty() }) return null
        if (clauses.any { it.all { lit -> getStatus(lit) == VarStatus.FALSE } }) return null

        // branching heuristic
        variableSelector.build(clauses)

        // main loop
        while (true) {
            val conflictClause = propagate()
            if (conflictClause != null) {
                // in case there is a conflict in CNF and trail is already in 0 state
                if (level == 0) return null

                // build new clause by conflict clause
                val lemma = analyzeConflict(conflictClause)

                lemma.lbd = lemma.distinctBy { vars[litIndex(it)].level }.size
                // println(lemma.lbd)
                //if (clauses.size % 1000 == 0) println(clauses.size)

                addClause(lemma)
                backjump(lemma)

                if (clauses.size > reduceNumber) {
                    reduceNumber += reduceIncrement
                    // reduceIncrement *= 1.1
                    restarter.restart()
                    reduceDB()
                }

                // Restart after adding a clause to maintain correct watchers
                restarter.update()

                // VSIDS
                variableSelector.update(lemma)

                continue
            }

            // If (the problem is already) SAT, return the current assignment
            if (satisfiable()) {
                val model = variableValues()
                reset()
                return model
            }

            // try to guess variable
            level++
            var nextVariable = variableSelector.nextDecisionVariable(vars, level)

            // Check that assumption we want to make isn't controversial
            if (level <= assumptions.size && wrongAssumption(nextVariable)) {
                reset()
                return null
            }

            if (level > assumptions.size && phaseSaving[abs(nextVariable)] == VarStatus.FALSE) {
                nextVariable = -abs(nextVariable)
            }
            setVariableValues(null, nextVariable)
        }
    }

    private fun reset() {
        level = 0
        clearTrail(0)
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
                    else -> {
                        if (assumptions.find { it == -index } != null) {
                            -index
                        } else {
                            index
                        }
                    }
                }
            }.sortedBy { litIndex(it) }.filter { litIndex(it) > 0 }
    }

    /** Two watchers **/

    private fun getTrailIndex(lit: Int): Int {
        return vars[litIndex(lit)].trailIndex
    }

    private fun updateWatchers(lit: Int) {
        val clausesToRemove = mutableSetOf<Clause>()
        watchers[litIndex(lit)].forEach { brokenClause ->
            var undef = 0
            val undefIndex = mutableListOf<Int>()
            var hasTrue = false
            brokenClause.forEachIndexed { index, it ->
                if (getStatus(it) == VarStatus.UNDEFINED) {
                    undef++
                    if (undef <= 2) {
                        undefIndex.add(index)
                    }
                } else if (getStatus(it) == VarStatus.TRUE) {
                    hasTrue = true
                }
            }
            if (undef > 1) {
                val newWatcherInd = if (undefIndex[0] > 1) undefIndex[0] else undefIndex[1]
                val newWatcher = brokenClause[newWatcherInd]
                watchers[litIndex(newWatcher)].add(brokenClause)
                if (litIndex(brokenClause[0]) == litIndex(lit)) {
                    val tmp = brokenClause[0]
                    brokenClause[0] = newWatcher
                    brokenClause[newWatcherInd] = tmp
                } else {
                    val tmp = brokenClause[1]
                    brokenClause[1] = newWatcher
                    brokenClause[newWatcherInd] = tmp
                }
                clausesToRemove.add(brokenClause)
            } else if (undef == 1 && !hasTrue) {
                units.add(brokenClause)
            }
        }
        watchers[litIndex(lit)].removeAll(clausesToRemove)
    }

    private fun addWatchers(clause: Clause) {
        if (clause.size == 1) {
            watchers[litIndex(clause[0])].add(clause)
            units.add(clause)
            return
        }
        //val undef = clause.count { getStatus(it) == VarStatus.UNDEFINED }

        var undef = 0
        var falseCounter = 0
        val undefIndex = mutableListOf<Int>()
        var lastInTrail = -1
        var secondLastInTrail = -1

        clause.forEachIndexed { ind, lit ->
            if (getStatus(lit) == VarStatus.UNDEFINED) {
                undef++
                if (undef <= 2) {
                    undefIndex.add(ind)
                }
            } else {
                if (lastInTrail == -1 || getTrailIndex(clause[lastInTrail]) < getTrailIndex(lit)) {
                    secondLastInTrail = lastInTrail
                    lastInTrail = ind
                } else if (secondLastInTrail == -1 || getTrailIndex(clause[secondLastInTrail]) < getTrailIndex(lit)) {
                    secondLastInTrail = ind
                }
                if (getStatus(lit) == VarStatus.FALSE) {
                    falseCounter++
                }
            }
        }

        var a: Int
        var b: Int

        if (undef >= 2) {
            a = undefIndex[0]
            b = undefIndex[1]
            watchers[litIndex(clause[a])].add(clause)
            watchers[litIndex(clause[b])].add(clause)
        } else if (undef == 1) {
            a = undefIndex[0]
            watchers[litIndex(clause[a])].add(clause)
            if (falseCounter == clause.size - 1) {
                units.add(clause)
            }
            b = lastInTrail
            watchers[litIndex(clause[b])].add(clause)
            //addForLastInTrail(1, clause, index)
        } else {
            // for clauses added by conflict and by newClause if it already controversial
            a = lastInTrail
            b = secondLastInTrail
            watchers[litIndex(clause[a])].add(clause)
            watchers[litIndex(clause[b])].add(clause)
            //addForLastInTrail(2, clause, index)
        }
        // put watchers on first place
        if (a > b) {
            a = b.also { b = a }
        }
        when (a) {
            0 -> clause[b] = clause[1].also { clause[1] = clause[b] }
            1 -> clause[b] = clause[0].also { clause[0] = clause[b] }
            else -> {
                clause[b] = clause[1].also { clause[1] = clause[b] }
                clause[a] = clause[0].also { clause[0] = clause[a] }
            }
        }
    }

/*    // add watchers to new clause. Run in buildWatchers and addClause
    private fun addWatchers(clause: Clause) {
        // every clause of size 1 watched by it only variable
        if (clause.size == 1) {
            watchers[litIndex(clause[0])].add(clause)
            units.add(clause)
            return
        }
        val undef = clause.count { getStatus(it) == VarStatus.UNDEFINED }

        if (undef >= 2) {
            // in case there are at least 2 undefined variable in clause
            val a = clause.indexOfFirst { getStatus(it) == VarStatus.UNDEFINED }
            val b = clause.drop(a + 1).indexOfFirst { getStatus(it) == VarStatus.UNDEFINED } + a + 1
            watchers[litIndex(clause[a])].add(clause)
            watchers[litIndex(clause[b])].add(clause)
        } else if (undef == 1) {
            // in case there are exactly 1 undefined variable in clause (only in case newClause)
            val a = clause.indexOfFirst { getStatus(it) == VarStatus.UNDEFINED }
            watchers[litIndex(clause[a])].add(clause)
            if (clause.count { getStatus(it) == VarStatus.FALSE } == clause.size - 1) {
                units.add(clause)
            }
            addForLastInTrail(1, clause)
        } else {
            // for clauses added by conflict and by newClause if it already has all defined literals
            addForLastInTrail(2, clause)
        }
    }

    // find n last assigned variables from given clause
    private fun addForLastInTrail(n: Int, clause: Clause) {
        var cnt = 0
        val clauseVars = clause.map { litIndex(it) }
        // want to watch to last n literals from trail
        for (ind in trail.lastIndex downTo 0) {
            if (trail[ind] in clauseVars) {
                cnt++
                watchers[trail[ind]].add(clause)
                if (cnt == n) {
                    return
                }
            }
        }
    }

    // update watchers for clauses linked with literal
    private fun updateWatchers(lit: Int) {
        val clausesToRemove = mutableSetOf<Clause>()
        watchers[litIndex(lit)].forEach { brokenClause ->
            if (!brokenClause.deleted) {
                val undef = brokenClause.count { getStatus(it) == VarStatus.UNDEFINED }
                val firstTrue = brokenClause.firstOrNull { getStatus(it) == VarStatus.TRUE }
                if (undef > 1) {
                    val newWatcher = brokenClause.first {
                        getStatus(it) == VarStatus.UNDEFINED && brokenClause !in watchers[litIndex(it)]
                    }
                    watchers[litIndex(newWatcher)].add(brokenClause)
                    clausesToRemove.add(brokenClause)
                } else if (undef == 1 && firstTrue == null) {
                    units.add(brokenClause)
                }
            }
        }
        watchers[litIndex(lit)].removeAll(clausesToRemove)
    }*/

    /** CDCL functions **/

    // check is all clauses satisfied or not
    private fun satisfiable() = clauses.all { clause -> clause.any { lit -> getStatus(lit) == VarStatus.TRUE } }

    // add clause and add watchers to it TODO: rename
    private fun addClause(clause: Clause) {
        clauses.add(clause)
        addWatchers(clause)

        restarter.addClause(clause)
        preprocessor?.addClause(clause)
    }

    // delete a variable from the trail TODO: rename
    private fun delVariable(v: Int) {
        phaseSaving[v] = getStatus(v)
        setStatus(v, VarStatus.UNDEFINED)
        vars[v].clause = null
        vars[v].level = -1
        vars[v].trailIndex = -1
    }

    // return index of conflict clause, or -1 if there is no conflict clause
    private fun propagate(): Clause? {
        while (units.size > 0) {
            val clause = units.removeLast()
            if (clause.any { getStatus(it) == VarStatus.TRUE }) continue

            // guarantees that clauses in unit don't become defined incorrect
            //require(clause.any { getStatus(it) != VarStatus.FALSE })
            val lit = clause.firstOrNull { getStatus(it) == VarStatus.UNDEFINED } ?: continue

            // check if we get a conflict
            watchers[litIndex(lit)].forEach { brokenClause ->
                if (!brokenClause.deleted && brokenClause != clause) {
                    // just because we need to check if undef is 1 or >=2 (so we check only watchers) - good acceleration
                    var undef = 0
                    if (getStatus(brokenClause[0]) == VarStatus.UNDEFINED) {
                        undef++
                    }
                    if (brokenClause.size > 1 && getStatus(brokenClause[1]) == VarStatus.UNDEFINED) {
                        undef++
                    }
                    // there is a slow check with .all (takes most time) - ideas to speed up (???)
                    if (undef == 1 && -lit in brokenClause && brokenClause.all { getStatus(it) != VarStatus.TRUE }) {
                        setStatus(lit, VarStatus.TRUE)
                        val v = litIndex(lit)
                        vars[v].clause = clause
                        vars[v].level = level
                        trail.add(v)
                        vars[v].trailIndex = trail.lastIndex
                        return brokenClause
                    }
                }
            }
            setVariableValues(clause, lit)
        }

        return null
    }

    // add a variable to the trail and update watchers of clauses linked to this variable
    private fun setVariableValues(clause: Clause?, lit: Int): Boolean {
        if (getStatus(lit) != VarStatus.UNDEFINED) return false

        setStatus(lit, VarStatus.TRUE)
        val v = litIndex(lit)
        vars[v].clause = clause
        vars[v].level = level
        vars[v].trailIndex = trail.lastIndex
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
        units.add(clauses.last())
    }

    // return position of literal in occurrence array
    private fun litPos(lit: Int): Int {
        return if (lit >= 0) {
            lit * 2 - 1 // odd indexes
        } else {
            -lit * 2 // even indexes
        }
    }

    private fun minimize(clause: Clause): Clause {
        mark++
        clause.forEach { minimizeMarks[litPos(it)] = mark }
        return Clause(clause.filterNot { lit ->
            vars[abs(lit)].clause?.all {
                minimizeMarks[litPos(it)] == mark
            } ?: false
        }.toMutableList())
    }

    // analyze conflict and return new clause
    private fun analyzeConflict(conflict: Clause): Clause {

        fun updateLemma(lemma: Clause, lit: Int) {
            if (lit !in lemma) {
                lemma.add(lit)
            }
        }

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

            vars[v].clause?.forEach { u ->
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
        return minimize(lemma)
    }
}
