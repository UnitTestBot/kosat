package org.kosat

import org.kosat.heuristics.Restarter
import org.kosat.heuristics.VSIDS
import org.kosat.heuristics.VariableSelector

/**
 * Solves [cnf] and returns
 * `null` if request unsatisfiable
 * [emptyList] is request is tautology
 * assignments of literals otherwise
 */
fun solveCnf(cnf: CnfRequest): Model {
    val clauses = (cnf.clauses.map { it.toClause() }).toMutableList()
    return CDCL(clauses, cnf.vars).solve()
}

class CDCL {

    /**
     * Initial constraints and externally added clauses by [newClause].
     * Clauses of size 1 are never stored and instead are located at level 0
     * on the trail.
     */
    val constraints = mutableListOf<Clause>()

    /**
     * The clauses learnt by the solver during the conflict analysis.
     * This should be replaced by a more efficient data structure
     * with a proper reduction mechanism. Learned clauses of size 1
     * are being [uncheckedEnqueue]'d to the level 0 of the trail.
     */
    val learnts = mutableListOf<Clause>()

    /**
     * The count of variables in the problem.
     */
    var numberOfVariables: Int = 0
        private set

    /**
     * For each variable contains current assignment,
     * clause it came from ([VarState.reason]) and decision level when it happened.
     */
    val vars: MutableList<VarState> = mutableListOf()

    /**
     * Trail of assignments, contains literals in the order they were assigned
     */
    private val trail: MutableList<Lit> = mutableListOf()

    /**
     * Index of first element in the trail which has not been propagated yet
     */
    private var qhead = 0

    /**
     * Two-watched literals heuristic.
     * `i`-th element of this list is set of clauses watched by variable `i`
     */
    private val watchers: MutableList<MutableList<Clause>> = mutableListOf()

    // controls the learned clause database reduction, should be replaced and moved in the future
    private var reduceNumber = 6000.0
    private var reduceIncrement = 500.0

    /**
     * The current decision level.
     */
    private var level: Int = 0

    /**
     * Used in analyzeConflict() to simplify clauses by
     * removing literals implied by their reasons.
     */
    private val minimizeMarks = mutableListOf<Int>()

    /**
     * @see [minimizeMarks]
     */
    private var currentMinimizationMark = 0

    // ---- Heuristics ---- //

    /**
     * The branching heuristic, used to choose the next decision variable
     */
    private val variableSelector: VariableSelector = VSIDS(numberOfVariables, this)

    /**
     * The restart strategy, used to decide when to restart the search
     * @see [solve]
     */
    private val restarter = Restarter(this)

    /**
     * Adds a literal to the end of the [trail],
     * assigns [LBool.TRUE] to it and [LBool.FALSE] to the negation,
     * but does not propagate it yet.
     */
    private fun uncheckedEnqueue(lit: Lit, reason: Clause? = null) {
        setValue(lit, LBool.TRUE)
        val v = lit.variable
        vars[v].reason = reason
        vars[v].level = level
        trail.add(lit)
    }

    // ---- Variable states ---- //

    /**
     * @return the value assigned to the literal during solving.
     */
    fun getValue(lit: Lit): LBool {
        return if (lit.isNeg) {
            !vars[lit.variable].value
        } else {
            vars[lit.variable].value
        }
    }

    /**
     * Sets the value of the literal to [value].
     * By construction, guarantees that the value
     * of the negation is set to `!value`.
     */
    private fun setValue(lit: Lit, value: LBool) {
        if (lit.isPos) {
            vars[lit.variable].value = value
        } else {
            vars[lit.variable].value = !value
        }
    }

    // ---- Public Interface ---- //

    /**
     * Create a new solver instance with no clauses.
     */
    constructor() : this(mutableListOf<Clause>())

    /**
     * Create a new solver instance with given clauses.
     * @param initialClauses the initial clauses.
     * @param initialVarsNumber the number of variables in the problem, if known.
     * Can help to avoid resizing of internal data structures.
     */
    constructor(
        initialClauses: Iterable<Clause>,
        initialVarsNumber: Int = 0,
    ) {
        while (numberOfVariables < initialVarsNumber) {
            addVariable()
        }

        initialClauses.forEach { newClause(it) }
        polarity = MutableList(numberOfVariables + 1) { LBool.UNDEFINED }
    }

    /**
     * Add a new variable to the solver.
     * The [addClause] technically adds variables automatically,
     * but sometimes not all variables have to be mentioned in the clauses.
     */
    fun addVariable(): Int {
        numberOfVariables++

        variableSelector.addVariable()

        vars.add(VarState(LBool.UNDEFINED, null, -1))

        polarity.add(LBool.UNDEFINED)

        watchers.add(mutableListOf())
        watchers.add(mutableListOf())

        minimizeMarks.add(0)
        minimizeMarks.add(0)

        seen.add(false)

        return numberOfVariables
    }

    /**
     * Add a new clause to the solver.
     */
    fun newClause(clause: Clause) {
        require(level == 0)

        // add not mentioned variables from new clause
        val maxVar = clause.maxOfOrNull { it.variable.ord } ?: 0
        while (numberOfVariables < maxVar) {
            addVariable()
        }

        // don't add clause if it already had true literal
        if (clause.any { getValue(it) == LBool.TRUE }) {
            return
        }

        // delete every false literal from new clause
        clause.lits.removeAll { getValue(it) == LBool.FALSE }

        // if the clause contains x and -x then it is useless
        if (clause.any { it.neg in clause }) {
            return
        }

        // handling case for clauses of size 1
        if (clause.size == 1) {
            uncheckedEnqueue(clause[0])
        } else {
            addClause(clause)
        }
    }

    // ---- Trail ---- //

    /**
     * Delete last variable from the trail, reset its value to
     * [LBool.UNDEFINED], memorize its polarity
     */
    private fun trailRemoveLast() {
        val lit = trail.removeLast()
        val v = lit.variable
        polarity[v] = getValue(v.posLit)
        setValue(v.posLit, LBool.UNDEFINED)
        vars[v].reason = null
        vars[v].level = -1
        variableSelector.backTrack(v)
    }

    /**
     * Remove variables from the trail until the given layer is reached.
     * @param until the layer to stop at. Variables on this layer will **not** be removed.
     */
    private fun clearTrail(until: Int) {
        while (trail.isNotEmpty() && vars[trail.last().variable].level > until) {
            trailRemoveLast()
        }
    }

    /**
     * Used for phase saving heuristic. Memorizes the polarity of
     * the given variable when it was last assigned, but reset during backtracking.
     */
    private var polarity: MutableList<LBool> = mutableListOf()

    // --- Solve with assumptions ---- //

    /**
     * The assumptions given to an incremental solver.
     */
    private var assumptions: List<Lit> = emptyList()

    /**
     * Solve the problem with the given assumptions.
     */
    fun solve(currentAssumptions: List<Lit>): Model {
        assumptions = currentAssumptions
        variableSelector.initAssumptions(assumptions)
        val result = solve()
        if (result == Model.UNSAT) {
            assumptions = emptyList()
            return result
        }
        currentAssumptions.forEach { lit ->
            if (result.values!![lit.variable] != if (lit.isPos) LBool.TRUE else LBool.FALSE) {
                assumptions = emptyList()
                return Model.UNSAT
            }
        }
        assumptions = emptyList()
        return result
    }

    // half of learnt get reduced
    fun reduceDB() {
        learnts.sortByDescending { it.lbd }
        val deletionLimit = learnts.size / 2
        var cnt = 0
        for (clause in learnts) {
            if (cnt == deletionLimit) {
                break
            }
            if (!clause.deleted) {
                cnt++
                clause.deleted = true
            }
        }
        learnts.removeAll { it.deleted }
    }

    // ---- Solve ---- //

    fun solve(): Model {
        var numberOfConflicts = 0
        var numberOfDecisions = 0

        if (constraints.isEmpty())
            return Model(emptyList())

        if (constraints.any { it.isEmpty() })
            return Model.UNSAT

        if (constraints.any { it.all { lit -> getValue(lit) == LBool.FALSE } })
            return Model.UNSAT

        variableSelector.build(constraints)

        // main loop
        while (true) {
            val conflictClause = propagate()
            if (conflictClause != null) {
                // CONFLICT
                numberOfConflicts++

                // in case there is a conflict in CNF and trail is already in 0 state
                if (level == 0) {
                    // println("KoSat conflicts:   $numberOfConflicts")
                    // println("KoSat decisions:   $numberOfDecisions")
                    return Model.UNSAT
                }

                // build new clause by conflict clause
                val lemma = analyzeConflict(conflictClause)

                // compute lbd "score" for lemma
                lemma.lbd = lemma.distinctBy { vars[it.variable].level }.size

                // return to decision level where lemma would be propagated
                backjump(lemma)

                // after backjump there is only one clause to propagate
                qhead = trail.size

                // if lemma.size == 1 we just add it to 0 decision level of trail
                if (lemma.size == 1) {
                    uncheckedEnqueue(lemma[0])
                } else {
                    uncheckedEnqueue(lemma[0], lemma)
                    addLearnt(lemma)
                }

                // remove half of learnts
                if (learnts.size > reduceNumber) {
                    reduceNumber += reduceIncrement
                    restarter.restart()
                    reduceDB()
                }
                variableSelector.update(lemma)

                // restart search after some number of conflicts
                restarter.update()
            } else {
                // NO CONFLICT
                require(qhead == trail.size)

                // If (the problem is already) SAT, return the current assignment
                if (trail.size == numberOfVariables) {
                    val model = getModel()
                    reset()
                    // println("KoSat conflicts:   $numberOfConflicts")
                    // println("KoSat decisions:   $numberOfDecisions")
                    return model
                }

                // try to guess variable
                level++
                var nextDecisionLiteral = variableSelector.nextDecision(vars, level)
                numberOfDecisions++

                // in case there is assumption propagated to false
                if (nextDecisionLiteral.isUndef) {
                    reset()
                    return Model.UNSAT
                }

                // phase saving heuristic
                if (level > assumptions.size && polarity[nextDecisionLiteral.variable] == LBool.FALSE) {
                    nextDecisionLiteral = nextDecisionLiteral.neg
                }

                uncheckedEnqueue(nextDecisionLiteral)
            }
        }
    }

    fun reset() {
        level = 0
        clearTrail(0)
        qhead = trail.size
    }

    // return current assignment of variables
    private fun getModel(): Model = Model(vars.map {
        when (it.value) {
            LBool.TRUE, LBool.UNDEFINED -> LBool.TRUE
            LBool.FALSE -> LBool.FALSE
        }
    })

    // ---- Two watchers ---- //

    // add watchers to new clause. Run in addConstraint in addLearnt
    private fun addWatchers(clause: Clause) {
        require(clause.size > 1)
        watchers[clause[0]].add(clause)
        watchers[clause[1]].add(clause)
    }

    // ---- CDCL functions ---- //

    // add new constraint and watchers to it, executes only in newClause
    private fun addClause(clause: Clause) {
        require(clause.size != 1)
        constraints.add(clause)
        if (clause.isNotEmpty()) {
            addWatchers(clause)
        }
    }

    // add clause and add watchers to it
    private fun addLearnt(clause: Clause) {
        require(clause.size != 1)
        learnts.add(clause)
        if (clause.isNotEmpty()) {
            addWatchers(clause)
        }
    }

    // return conflict clause, or null if there is no conflict clause
    private fun propagate(): Clause? {
        var conflict: Clause? = null
        while (qhead < trail.size) {
            val lit = trail[qhead++]

            if (getValue(lit) == LBool.FALSE) {
                return vars[lit.variable].reason
            }

            // Checking the list of clauses watching the negation of the literal.
            // In those clauses, both of the watched literals might be false,
            // which can either lead to a conflict (all literals in clause are false),
            // unit propagation (only one unassigned literal left), or invalidation
            // of the watchers (both watchers are false, but there are others)
            val clausesToKeep = mutableListOf<Clause>()
            val possiblyBrokenClauses = watchers[lit.neg]

            for (clause in possiblyBrokenClauses) {
                if (clause.deleted) continue

                clausesToKeep.add(clause)

                if (conflict != null) continue

                // we are always watching the first two literals in the clause
                // this makes sure that the second watcher is lit,
                // and the first one is the other one
                if (clause[0].variable == lit.variable) {
                    clause.swap(0, 1)
                }

                // if first watcher (not lit) is true then the clause is already true, skipping it
                if (getValue(clause[0]) == LBool.TRUE) continue

                // Index of the first literal in the clause not assigned to false
                var firstNotFalse = -1
                for (ind in 2 until clause.size) {
                    if (getValue(clause[ind]) != LBool.FALSE) {
                        firstNotFalse = ind
                        break
                    }
                }

                if (firstNotFalse == -1 && getValue(clause[0]) == LBool.FALSE) {
                    // all the literals in the clause are already assigned to false
                    conflict = clause
                } else if (firstNotFalse == -1) { // getValue(brokenClause[0]) == VarValue.UNDEFINED
                    // the only unassigned literal (which is the second watcher) in the clause must be true
                    uncheckedEnqueue(clause[0], clause)
                } else {
                    // there is at least one literal in the clause not assigned to false,
                    // so we can use it as a new first watcher instead
                    watchers[clause[firstNotFalse]].add(clause)
                    clause.swap(firstNotFalse, 1)
                    clausesToKeep.removeLast()
                }
            }

            watchers[lit.neg] = clausesToKeep

            if (conflict != null) break
        }
        return conflict
    }

    // change level, undefine variables, clear units (if clause.size == 1 we backjump to 0 level)
    // Pre-conditions: second element in clause should have first max level except current one
    private fun backjump(clause: Clause) {
        level = if (clause.size > 1) vars[clause[1].variable].level else 0
        clearTrail(level)
    }

    /**
     * Analyze conflict and return new clause
     * Post-conditions:
     *      - first element in clause has max (current) propagate level
     *      - second element in clause has second max propagate level
     */
    private val seen = MutableList(numberOfVariables) { false }

    private fun analyzeConflict(conflict: Clause): Clause {
        var numberOfActiveVariables = 0
        val lemma = mutableSetOf<Lit>()

        conflict.forEach { lit ->
            if (vars[lit.variable].level == level) {
                seen[lit.variable] = true
                numberOfActiveVariables++
            } else {
                lemma.add(lit)
            }
        }

        var lastLevelWalkIndex = trail.lastIndex

        // The UIP is the only literal in the current decision level
        // of the conflict clause. To build it, we walk back on the
        // last level of the trail and replace all but one literal
        // in the conflict clause by their reason.
        while (numberOfActiveVariables > 1) {
            val v = trail[lastLevelWalkIndex--].variable
            if (!seen[v]) continue

            // The null assertion is safe because we only traverse
            // the last level, and every variable on this level
            // has a reason except for the decision variable,
            // which will not be visited because even if it is seen,
            // it is the last seen variable in order of the trail.
            vars[v].reason!!.forEach { u ->
                val current = u.variable
                if (vars[current].level != level) {
                    lemma.add(u)
                } else if (current != v && !seen[current]) {
                    seen[current] = true
                    numberOfActiveVariables++
                }
            }

            seen[v] = false
            numberOfActiveVariables--
        }

        var newClause: Clause

        trail.last { seen[it.variable] }.let { lit ->
            val v = lit.variable
            lemma.add(if (getValue(v.posLit) == LBool.TRUE) v.negLit else v.posLit)

            // Simplify clause by removing redundant literals which follow from their reasons
            currentMinimizationMark++
            lemma.forEach { minimizeMarks[it] = currentMinimizationMark }
            newClause = Clause(
                lemma.filter { possiblyImpliedLit ->
                    vars[possiblyImpliedLit.variable].reason?.any {
                        minimizeMarks[it] != currentMinimizationMark
                    } ?: true
                }.toMutableList(),
            )

            val uipIndex = newClause.indexOfFirst { it.variable == v }
            // move UIP vertex to 0 position
            newClause.swap(uipIndex, 0)
            seen[v] = false
        }
        // move last defined literal to 1 position
        if (newClause.size > 1) {
            val secondMax = newClause.drop(1).indices.maxByOrNull { vars[newClause[it + 1].variable].level } ?: 0
            newClause.swap(1, secondMax + 1)
        }
        return newClause
    }
}
