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

    val constraints = mutableListOf<Clause>()
    // doesn't contain learnt clauses of size 1
    val learnts = mutableListOf<Clause>()
    var varsNumber: Int = 0

    // values of variables
    val vars: MutableList<VarState> = MutableList(varsNumber + 1) { VarState(VarStatus.UNDEFINED, null, -1) }

    // all decisions and consequences
    private val trail: MutableList<Int> = mutableListOf()

    // two watched literals heuristic; in watchers[i] set of clauses watched by variable i
    private val watchers = MutableList(varsNumber * 2 + 1) { mutableListOf<Clause>() }

    // list of unit clauses to propagate
    val units: MutableList<Clause> = mutableListOf() // TODO must be queue

    var reduceNumber = 6000.0
    var reduceIncrement = 500.0

    // decision level
    var level: Int = 0

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
    )

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

    private fun variable(lit: Int): Int = abs(lit)

    // TODO rename
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
        initClauses: MutableList<Clause>,
        initVarsNumber: Int = 0,
        solverType: SolverType = SolverType.INCREMENTAL
    ) : this(solverType) {
        while (varsNumber < initVarsNumber) {
            addVariable()
        }
        initClauses.forEach { newClause(it) }
        phaseSaving = MutableList(varsNumber + 1) { VarStatus.UNDEFINED } // TODO is phaseSaving adapted for incremental?
    }

    // public function for adding new variables
    override fun addVariable() { // TODO simple checks of duplicate variables in newClause
        varsNumber++

        variableSelector.addVariable()

        analyzeActivity.add(false)

        vars.add(VarState(VarStatus.UNDEFINED, null, -1))
        watchers.add(mutableListOf())
        watchers.add(mutableListOf())
    }

    // public function for adding new clauses
    fun newClause(clause: Clause) {
        require(level == 0)
        val maxVar = clause.maxOfOrNull { abs(it) } ?: 0
        while (varsNumber < maxVar) {
            addVariable()
        }
        // don't add clause if it already had true literal
        if (clause.any { getStatus(it) == VarStatus.TRUE }) {
            return
        }
        clause.lits.removeAll { getStatus(it) == VarStatus.FALSE }
        clause.locked = true
        if (clause.size == 1) {
            units.add(clause)
        } else {
            addConstraint(clause)
        }
    }

    /** Trail: **/

    // clear trail until given level
    fun clearTrail(until: Int = -1) {
        while (trail.isNotEmpty() && vars[trail.last()].level > until) {
            undefineVariable(trail.removeLast())
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
        learnts.sortBy { -it.lbd }
        val lim = learnts.size / 2
        var i = 0
        learnts.forEach { clause ->
            if (!clause.locked && i < lim) {
                i++
                clause.deleted = true
            }
        }
        learnts.removeAll { it.deleted }
    }

    /** Solve **/

    fun solve(): List<Int>? {

        preprocessor = if (solverType == SolverType.NON_INCREMENTAL) {
            Preprocessor(this)
        } else {
            null
        }

        // extreme cases
        // if (clauses.isEmpty()) return variableValues()
        if (constraints.any { it.isEmpty() }) return null
        if (constraints.any { it.all { lit -> getStatus(lit) == VarStatus.FALSE } }) return null

        // branching heuristic
        variableSelector.build(constraints)

        var totalNumberOfConflicts = 0

        // main loop
        while (true) {
            val conflictClause = propagate()
            if (conflictClause != null) {
                totalNumberOfConflicts++
                // in case there is a conflict in CNF and trail is already in 0 state
                if (level == 0) {
                    println(totalNumberOfConflicts)
                    return null
                }

                // build new clause by conflict clause
                val lemma = analyzeConflict(conflictClause)

                lemma.lbd = lemma.distinctBy { vars[variable(it)].level }.size
                // println(lemma.lbd)
                // if (clauses.size % 1000 == 0) println(clauses.size)

                backjump(lemma)

                // if lemma.size == 1 we already added it to units at 0 level
                if (lemma.size != 1) {
                    learnClause(lemma)
                }

                if (learnts.size > reduceNumber) {
                    println("Conflicts found: $totalNumberOfConflicts. Clauses learnt: ${learnts.size}")
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
                println(totalNumberOfConflicts)
                return model
            }

            // try to guess variable
            level++
            var nextVariable = variableSelector.nextDecisionVariable(vars, level)

            if (level > assumptions.size && phaseSaving[abs(nextVariable)] == VarStatus.FALSE) {
                nextVariable = -abs(nextVariable)
            } // TODO move to nextDecisionVariable
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
            }.sortedBy { variable(it) }.filter { variable(it) > 0 }
    }

    /** Two watchers **/

    // add watchers to new clause. Run in buildWatchers and addClause
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

    // check is all clauses satisfied or not
    private fun satisfiable() = constraints.all { clause -> clause.any { lit -> getStatus(lit) == VarStatus.TRUE } }

    private fun addConstraint(clause: Clause) {
        require(clause.size != 1)
        constraints.add(clause)
        if (clause.isNotEmpty()) {
            addWatchers(clause)
        }

        preprocessor?.addClause(clause)
    }

    // add clause and add watchers to it
    private fun learnClause(clause: Clause) {
        require(clause.size != 1)
        learnts.add(clause)
        if (clause.isNotEmpty()) {
            addWatchers(clause)
        }
        preprocessor?.addClause(clause)
    }

    // delete a variable from the trail
    private fun undefineVariable(v: Int) {
        phaseSaving[v] = getStatus(v)
        setStatus(v, VarStatus.UNDEFINED)
        vars[v].clause = null
        vars[v].level = -1
    }

    // return index of conflict clause, or -1 if there is no conflict clause
    private fun propagate(): Clause? {
        while (units.size > 0) {
            val clause = units.removeLast()
            if (clause.any { getStatus(it) == VarStatus.TRUE }) continue

            if (clause.all { getStatus(it) == VarStatus.FALSE }) {
                return clause
            }

            val lit = clause.first { getStatus(it) == VarStatus.UNDEFINED }
            setVariableValues(clause, lit)
        }

        return null
    }

    // add a variable to the trail and update watchers of clauses linked to this variable
    private fun setVariableValues(clause: Clause?, lit: Int): Boolean {
        if (getStatus(lit) != VarStatus.UNDEFINED) return false

        setStatus(lit, VarStatus.TRUE)
        val v = variable(lit)
        vars[v].clause = clause
        vars[v].level = level
        trail.add(v)
        updateWatchers(lit)
        return true
    }

    // change level, undefine variables, clear units; if clause.size == 1 we backjump to 0 level
    private fun backjump(clause: Clause) {
        level = clause.map { vars[variable(it)].level }.sortedDescending().firstOrNull { it != level } ?: 0

        clearTrail(level)

        // after backjump it's the only clause to propagate
        units.clear()
        units.add(clause)
    }

    // analyze conflict and return new clause

    /** contains used variables during conflict analyze (should resize in [addVariable]) **/
    private val analyzeActivity = MutableList(varsNumber + 1) { false }

    private fun analyzeConflict(conflict: Clause): Clause {

        fun updateLemma(lemma: Clause, lit: Int): Int {
            var ind = lemma.indexOfFirst { it == lit }
            if (ind == -1) {
                lemma.add(lit)
                ind = lemma.lastIndex
            }
            return ind
        }

        var activeVariables = 0
        val lemma = Clause()

        conflict.forEach { lit ->
            if (vars[variable(lit)].level == level) {
                analyzeActivity[variable(lit)] = true
                activeVariables++
            } else {
                updateLemma(lemma, lit)
            }
        }
        var ind = trail.size - 1
        while (activeVariables > 1) {

            val v = trail[ind--]
            if (!analyzeActivity[v]) continue

            vars[v].clause?.forEach { u ->
                val current = variable(u)
                if (vars[current].level != level) {
                    updateLemma(lemma, u)
                } else if (current != v && !analyzeActivity[current]) {
                    analyzeActivity[current] = true
                    activeVariables++
                }
            }
            analyzeActivity[v] = false
            activeVariables--
        }
        analyzeActivity.indexOfFirst { it }.let { v ->
            require (v != -1)
            if (v != -1) {
                val uipIndex = updateLemma(lemma, if (getStatus(v) == VarStatus.TRUE) -v else v)
                // fancy swap (move UIP vertex to 0 position)
                lemma[uipIndex] = lemma[0].also { lemma[0] = lemma[uipIndex] }
                analyzeActivity[v] = false
            }
        }
        // move last defined literal to 1 position TODO: there's room for simplify this
        if (lemma.size > 1) {
            var v = 0
            var previousLevel = -1
            lemma.forEachIndexed { ind, lit ->
                val literalLevel = vars[variable(lit)].level
                if (literalLevel != level && literalLevel > previousLevel) {
                    previousLevel = literalLevel
                    v = ind
                }
            }
            lemma[1] = lemma[v].also { lemma[v] = lemma[1] }
        }
        return lemma
    }
}
