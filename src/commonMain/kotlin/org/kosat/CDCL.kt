package org.kosat

import com.soywiz.klock.PerformanceCounter
import com.soywiz.klock.TimeSpan
import com.soywiz.klock.microseconds
import org.kosat.heuristics.Preprocessor
import org.kosat.heuristics.Restarter
import org.kosat.heuristics.VSIDS
import org.kosat.heuristics.VariableSelector
import kotlin.math.abs

// CDCL
fun solveCnf(cnf: CnfRequest): List<Int>? {
    val clauses = (cnf.clauses.map { it.lits }).toMutableList()
    return CDCL(clauses.map { Clause(it) }.toMutableList(), cnf.vars).solve()
}

enum class SolverType {
    INCREMENTAL, NON_INCREMENTAL;
}

class CDCL(private val solverType: SolverType = SolverType.INCREMENTAL) {

    // we never store clauses of size 1
    // they are lying at 0 decision level of trail

    // initial constraints + externally added by newClause
    val constraints = mutableListOf<Clause>()

    // learnt from conflicts clauses, once in a while their number halved
    val learnts = mutableListOf<Clause>()
    var numberOfVariables: Int = 0

    // contains current assignment, clause it came from and decision level when it happened
    val vars: MutableList<VarState> = MutableList(numberOfVariables + 1) { VarState(VarStatus.UNDEFINED, null, -1) }

    // all decisions and consequences, contains variables
    private val trail: MutableList<Int> = mutableListOf()

    // two watched literals heuristic; in watchers[i] set of clauses watched by variable i
    private val watchers = MutableList(numberOfVariables * 2 + 1) { mutableListOf<Clause>() }

    // list of unit clauses to propagate
    val units: MutableList<Clause> = mutableListOf() // TODO must be queue

    var reduceNumber = 6000.0
    var reduceIncrement = 500.0

    // current decision level
    var level: Int = 0

    // minimization lemma in analyze conflicts
    private val minimizeMarks = MutableList(numberOfVariables * 2 + 1) { 0 }
    var mark = 0

    /** Heuristics **/

    // branching heuristic
    private val variableSelector: VariableSelector = VSIDS(numberOfVariables)

    // preprocessing includes deleting subsumed clauses and bve, offed by default
    private var preprocessor: Preprocessor? = null

    // restart search from time to time
    private val restarter = Restarter(this)

    /** Variable states **/

    // get status of literal
    fun getStatus(lit: Int): VarStatus {
        if (vars[variable(lit)].status == VarStatus.UNDEFINED) return VarStatus.UNDEFINED
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

    // TODO: consistent indexation
    private fun variable(lit: Int): Int = abs(lit)

    // TODO: rename
    private fun watchedPos(lit: Int): Int {
        return if (lit < 0) {
            2 * (-lit)
        } else {
            2 * lit - 1
        }
    }

    /** Interface **/

    constructor() : this(mutableListOf<Clause>())

    constructor(
        initialClauses: MutableList<Clause>,
        initialVarsNumber: Int = 0,
        solverType: SolverType = SolverType.INCREMENTAL
    ) : this(solverType) {
        reserveVars(initialVarsNumber)
        initialClauses.forEach { newClause(it) }
        polarity = MutableList(numberOfVariables + 1) { VarStatus.UNDEFINED } // TODO is phaseSaving adapted for incremental?
    }

    private fun reserveVars(max: Int) {
        while (numberOfVariables < max) {
            addVariable()
        }
    }

    // public function for adding new variables
    fun addVariable(): Int { // TODO simple checks of duplicate variables in newClause
        numberOfVariables++

        variableSelector.addVariable()

        analyzeActivity.add(false)

        vars.add(VarState(VarStatus.UNDEFINED, null, -1))

        watchers.add(mutableListOf())
        watchers.add(mutableListOf())
        minimizeMarks.add(0)
        minimizeMarks.add(0)

        return numberOfVariables
    }

    // public function for adding new clauses
    fun newClause(clause: Clause) {
        require(level == 0)

        // add not mentioned variables from new clause
        val maxVar = clause.maxOfOrNull { abs(it) } ?: 0
        while (numberOfVariables < maxVar) {
            addVariable()
        }

        // don't add clause if it already had true literal
        if (clause.any { getStatus(it) == VarStatus.TRUE }) {
            return
        }

        // delete every false literal from new clause
        clause.lits.removeAll { getStatus(it) == VarStatus.FALSE }

        // if clause contains x and -x than it is useless
        if (clause.any { -it in clause }) {
            return
        }

        // handling case of clause of size 1
        if (clause.size == 1) {
            units.add(clause)
        } else {
            addConstraint(clause)
        }
    }

    /** Trail: **/

    // delete last variable from the trail
    private fun trailRemoveLast() {
        val v = trail.removeLast()
        polarity[v] = getStatus(v)
        setStatus(v, VarStatus.UNDEFINED)
        vars[v].reason = null
        vars[v].level = -1
        variableSelector.backTrack(v)
    }

    // clear trail until given level
    fun clearTrail(until: Int = -1) {
        while (trail.isNotEmpty() && vars[trail.last()].level > until) {
            trailRemoveLast()
        }
    }

    /** Solve with assumptions **/

    // assumptions for incremental sat-solver
    private var assumptions: List<Int> = emptyList()

    // phase saving
    private var polarity: MutableList<VarStatus> = mutableListOf()

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


    // half of learnt get reduced
    fun reduceDB() {
        learnts.sortBy { -it.lbd }
        val lim = learnts.size / 2
        var i = 0
        learnts.forEach { clause ->
            if (i < lim) {
                i++
                clause.deleted = true
            }
        }
        learnts.removeAll { it.deleted }
    }

    /** Solve **/

    var ok = true

    fun solve(): List<Int>? {

        val start: Double = PerformanceCounter.microseconds

        var totalNumberOfConflicts = 0

        preprocessor = if (solverType == SolverType.NON_INCREMENTAL) {
            Preprocessor(this)
        } else {
            null
        }
        preprocessor?.apply()

        // extreme cases
        if (constraints.any { it.isEmpty() }) return null
        if (constraints.any { it.all { lit -> getStatus(lit) == VarStatus.FALSE } }) return null

        variableSelector.build(constraints)

        // main loop
        while (ok) {
            val conflictClause = propagate()
            if (conflictClause != null) {
                // CONFLICT
                totalNumberOfConflicts++

                // in case there is a conflict in CNF and trail is already in 0 state
                if (level == 0) {
                    println(totalNumberOfConflicts)
                    return null
                }

                // build new clause by conflict clause
                val lemma = analyzeConflict(conflictClause)

                lemma.lbd = lemma.distinctBy { vars[variable(it)].level }.size

                backjump(lemma)

                // if lemma.size == 1 we already added it to units at 0 level
                if (lemma.size != 1) {
                    addLearnt(lemma)
                }

                // remove half of learnts
                if (learnts.size > reduceNumber) {
                    reduceNumber += reduceIncrement
                    val end: Double = PerformanceCounter.microseconds
                    val elapsed: TimeSpan = (end - start).microseconds
                    /*if (elapsed.seconds > 120) {
                        ok = false
                    }*/
                    restarter.restart()
                    reduceDB()
                }
                variableSelector.update(lemma)

                // restart search after some number of conflicts
                restarter.update()
            } else {
                // NO CONFLICT

                // If (the problem is already) SAT, return the current assignment
                if (trail.size == numberOfVariables) {
                    val model = getModel()
                    reset()
                    println(totalNumberOfConflicts)
                    return model
                }


                // try to guess variable
                level++
                var nextDecisionVariable = variableSelector.nextDecision(vars, level)

                // phase saving heuristic
                if (level > assumptions.size && polarity[abs(nextDecisionVariable)] == VarStatus.FALSE) {
                    nextDecisionVariable = -abs(nextDecisionVariable)
                } // TODO move to nextDecisionVariable

                assign(nextDecisionVariable, null)
            }
        }
        return emptyList()
    }

    private fun reset() {
        level = 0
        clearTrail(0)
    }

    // return current assignment of variables
    private fun getModel(): List<Int> {
        if (solverType == SolverType.NON_INCREMENTAL) {
            preprocessor?.recoverAnswer()
        }

        return vars.drop(1)
            .mapIndexed { index, v ->
                when (v.status) {
                    VarStatus.TRUE -> index + 1
                    VarStatus.FALSE -> -index - 1
                    VarStatus.UNDEFINED -> {
                        println(vars)
                        throw Exception("Unexpected unassigned variable")
                    }
                }
            }
    }

    /** Two watchers **/

    // add watchers to new clause. Run and addConstraint and addLearnt
    private fun addWatchers(clause: Clause) {
        require(clause.size > 1)
        watchers[watchedPos(clause[0])].add(clause)
        watchers[watchedPos(clause[1])].add(clause)
    }

    // update watchers for clauses linked with literal; 95% of time we are in this function
    private fun updateWatchers(lit: Int) {
        val clausesToRemove = mutableSetOf<Clause>()
        watchers[watchedPos(-lit)].forEach { brokenClause ->
            if (!brokenClause.deleted) {
                if (variable(brokenClause[0]) == variable(lit)) {
                    brokenClause[0] = brokenClause[1].also { brokenClause[1] = brokenClause[0] }
                }
                // if second watcher is true skip clause
                if (getStatus(brokenClause[0]) != VarStatus.TRUE) {
                    var firstNotFalse = -1
                    for (ind in 2 until brokenClause.size) {
                        if (getStatus(brokenClause[ind]) != VarStatus.FALSE) {
                            firstNotFalse = ind
                            break
                        }
                    }
                    if (firstNotFalse == -1) {
                        units.add(brokenClause)
                    } else {
                        watchers[watchedPos(brokenClause[firstNotFalse])].add(brokenClause)
                        brokenClause[firstNotFalse] = brokenClause[1].also { brokenClause[1] = brokenClause[firstNotFalse] }
                        clausesToRemove.add(brokenClause)
                    }
                }
            }
        }
        watchers[watchedPos(-lit)].removeAll(clausesToRemove)
    } // TODO does kotlin create new "ссылки" to objects or there are only one?

    /** CDCL functions **/

    // add new constraint, executes only in newClause
    private fun addConstraint(clause: Clause) {
        require(clause.size != 1)
        constraints.add(clause)
        if (clause.isNotEmpty()) {
            addWatchers(clause)
        }

        preprocessor?.addClause(clause)
    }

    // add clause and add watchers to it
    private fun addLearnt(clause: Clause) {
        require(clause.size != 1)
        learnts.add(clause)
        if (clause.isNotEmpty()) {
            addWatchers(clause)
        }
        preprocessor?.addClause(clause)
    }

    // return conflict clause, or null if there is no conflict clause
    private fun propagate(): Clause? {
        while (units.size > 0) {
            val clause = units.removeLast()
            if (clause.any { getStatus(it) == VarStatus.TRUE }) continue

            if (clause.all { getStatus(it) == VarStatus.FALSE }) {
                return clause
            }

            val lit = clause.first { getStatus(it) == VarStatus.UNDEFINED }
            assign(lit, clause)
        }

        return null
    }

    // add a variable to the trail and update watchers of clauses linked to this literal
    private fun assign(lit: Int, clause: Clause?) {
        if (getStatus(lit) != VarStatus.UNDEFINED) return

        setStatus(lit, VarStatus.TRUE)
        val v = variable(lit)
        vars[v].reason = clause
        vars[v].level = level
        trail.add(v)
        updateWatchers(lit)
    }

    // change level, undefine variables, clear units (if clause.size == 1 we backjump to 0 level)
    private fun backjump(clause: Clause) {
        level = clause.map { vars[variable(it)].level }.sortedDescending().firstOrNull { it != level } ?: 0

        clearTrail(level)

        // after backjump it's the only clause to propagate
        units.clear()
        units.add(clause)
    }

    /** contains used variables during conflict analyze (should resize in [addVariable]) **/
    private val analyzeActivity = MutableList(numberOfVariables + 1) { false }

    // deleting lits that have ancestor in implication graph in reason
    private fun minimize(clause: Clause): Clause {
        mark++
        clause.forEach { minimizeMarks[watchedPos(it)] = mark }
        return Clause(clause.filterNot { lit ->
            vars[abs(lit)].reason?.all {
                minimizeMarks[watchedPos(it)] == mark
            } ?: false
        }.toMutableList())
    }

    // analyze conflict and return new clause
    private fun analyzeConflict(conflict: Clause): Clause {

        fun updateLemma(lemma: MutableSet<Int>, lit: Int) {
            lemma.add(lit)
        }

        var numberOfActiveVariables = 0
        val lemma = mutableSetOf<Int>()

        conflict.forEach { lit ->
            if (vars[variable(lit)].level == level) {
                analyzeActivity[variable(lit)] = true
                numberOfActiveVariables++
            } else {
                updateLemma(lemma, lit)
            }
        }
        var ind = trail.lastIndex


        while (numberOfActiveVariables > 1) {

            val v = trail[ind--]
            if (!analyzeActivity[v]) continue

            vars[v].reason?.forEach { u ->
                val current = variable(u)
                if (vars[current].level != level) {
                    updateLemma(lemma, u)
                } else if (current != v && !analyzeActivity[current]) {
                    analyzeActivity[current] = true
                    numberOfActiveVariables++
                }
            }
            analyzeActivity[v] = false
            numberOfActiveVariables--
        }

        var newClause: Clause

        trail.last { analyzeActivity[it] }.let { v ->
            require(v != -1)
            updateLemma(lemma, if (getStatus(v) == VarStatus.TRUE) -v else v)
            newClause = Clause(lemma.toMutableList())
            val uipIndex = newClause.indexOfFirst { abs(it) == v }
            // fancy swap (move UIP vertex to 0 position)
            newClause[uipIndex] = newClause[0].also { newClause[0] = newClause[uipIndex] }
            analyzeActivity[v] = false
        }
        // move last defined literal to 1 position TODO: there's room for simplify this
        if (newClause.size > 1) {
            var v = 0
            var previousLevel = -1
            newClause.forEachIndexed { i, lit ->
                val literalLevel = vars[variable(lit)].level
                if (literalLevel != level && literalLevel > previousLevel) {
                    previousLevel = literalLevel
                    v = i
                }
            }
            newClause[1] = newClause[v].also { newClause[v] = newClause[1] }
        }
        return minimize(newClause)
    }
}
