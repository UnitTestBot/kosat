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
     * Clause database.
     */
    private val db: ClauseDatabase = ClauseDatabase()

    /**
     * Assignment.
     */
    private val assignment: Assignment = Assignment()

    /**
     * Two-watched literals heuristic.
     * `i`-th element of this list is set of clauses watched by variable `i`
     */
    private val watchers: MutableList<MutableList<Clause>> = mutableListOf()

    /**
     * The number of variables.
     */
    var numberOfVariables: Int = 0
        private set

    // controls the learned clause database reduction, should be replaced and moved in the future
    private var reduceNumber = 6000.0
    private var reduceIncrement = 500.0

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
    private val variableSelector: VariableSelector = VSIDS(numberOfVariables)

    /**
     * The restart strategy, used to decide when to restart the search
     * @see [solve]
     */
    private val restarter = Restarter(this)

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
            newVariable()
        }

        initialClauses.forEach { newClause(it) }
        polarity = MutableList(numberOfVariables + 1) { LBool.UNDEF }
    }

    /**
     * Allocate a new variable in the solver.
     *
     * The [addClause] technically adds variables automatically,
     * but sometimes not all variables have to be mentioned in the clauses.
     */
    fun newVariable(): Int {
        numberOfVariables++

        // Watch
        watchers.add(mutableListOf())
        watchers.add(mutableListOf())

        // Assignment
        assignment.addVariable()

        // Variable selection strategy
        variableSelector.addVariable()

        // Phase saving heuristics
        polarity.add(LBool.UNDEF)

        // ???
        minimizeMarks.add(0)
        minimizeMarks.add(0)

        // ???
        seen.add(false)

        return numberOfVariables
    }

    fun value(lit: Lit): LBool {
        return assignment.value(lit)
    }

    fun uncheckedEnqueue(lit: Lit, reason: Clause?) {
        assignment.uncheckedEnqueue(lit, reason)
    }

    /**
     * Add a new clause to the solver.
     */
    fun newClause(clause: Clause) {
        check(assignment.decisionLevel == 0)

        // add not mentioned variables from new clause
        val maxVar = clause.lits.maxOfOrNull { it.variable.index } ?: 0
        while (numberOfVariables < maxVar) {
            newVariable()
        }

        // don't add clause if it already had true literal
        if (clause.lits.any { value(it) == LBool.TRUE }) {
            return
        }

        // delete every false literal from new clause
        clause.lits.removeAll { value(it) == LBool.FALSE }

        // if the clause contains x and -x then it is useless
        if (clause.lits.any { it.neg in clause.lits }) {
            return
        }

        // handling case for clauses of size 1
        if (clause.size == 1) {
            uncheckedEnqueue(clause[0], null)
        } else {
            addClause(clause)
        }
    }

    // ---- Trail ---- //

    fun backtrack(level: Int) {
        while (assignment.trail.isNotEmpty() && assignment.level(assignment.trail.last().variable) > level) {
            val lit = assignment.trail.removeLast()
            val v = lit.variable
            polarity[v] = assignment.value(v)
            assignment.unassign(v)
            variableSelector.backTrack(v)
        }

        assignment.qhead = assignment.trail.size
        assignment.decisionLevel = level
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
            if (result.values!![lit.variable] != lit.isPos) {
                assumptions = emptyList()
                return Model.UNSAT
            }
        }
        assumptions = emptyList()
        return result
    }

    // half of learnt get reduced
    fun reduceDB() {
        db.learnts.sortByDescending { it.lbd }
        val deletionLimit = db.learnts.size / 2
        var cnt = 0
        for (clause in db.learnts) {
            if (cnt == deletionLimit) {
                break
            }
            if (!clause.deleted) {
                cnt++
                clause.deleted = true
            }
        }
        db.learnts.removeAll { it.deleted }
    }

    // ---- Solve ---- //

    fun solve(): Model {
        var numberOfConflicts = 0
        var numberOfDecisions = 0

        if (db.clauses.isEmpty()) {
            return getModel()
        }

        if (db.clauses.any { clause -> clause.lits.isEmpty() }) {
            return Model.UNSAT
        }

        if (db.clauses.any { clause -> clause.lits.all { value(it) == LBool.FALSE } }) {
            return Model.UNSAT
        }

        variableSelector.build(db.clauses)

        // main loop
        while (true) {
            val conflictClause = propagate()
            if (conflictClause != null) {
                // CONFLICT
                numberOfConflicts++

                // in case there is a conflict in CNF and trail is already in 0 state
                if (assignment.decisionLevel == 0) {
                    // println("KoSat conflicts:   $numberOfConflicts")
                    // println("KoSat decisions:   $numberOfDecisions")
                    return Model.UNSAT
                }

                // build new clause by conflict clause
                val lemma = analyzeConflict(conflictClause)

                // compute lbd "score" for lemma
                lemma.lbd = lemma.lits.distinctBy { assignment.level(it.variable) }.size

                // return to decision level where lemma would be propagated
                val level = if (lemma.size > 1) assignment.level(lemma[1].variable) else 0
                backtrack(level)

                // after backjump there is only one clause to propagate
                // assignment.qhead = assignment. trail.size

                // if lemma.size == 1 we just add it to 0 decision level of trail
                if (lemma.size == 1) {
                    uncheckedEnqueue(lemma[0], null)
                } else {
                    uncheckedEnqueue(lemma[0], lemma)
                    addLearnt(lemma)
                }

                // remove half of learnts
                if (db.learnts.size > reduceNumber) {
                    reduceNumber += reduceIncrement
                    restarter.restart()
                    reduceDB()
                }
                variableSelector.update(lemma)

                // restart search after some number of conflicts
                restarter.update()
            } else {
                // NO CONFLICT
                require(assignment.qhead == assignment.trail.size)

                // If (the problem is already) SAT, return the current assignment
                if (assignment.trail.size == numberOfVariables) {
                    val model = getModel()
                    reset()
                    // println("KoSat conflicts:   $numberOfConflicts")
                    // println("KoSat decisions:   $numberOfDecisions")
                    return model
                }

                // try to guess variable
                assignment.newDecisionLevel()
                var nextDecisionLiteral = variableSelector.nextDecision(assignment)
                numberOfDecisions++

                // in case there is assumption propagated to false
                if (nextDecisionLiteral.isUndef) {
                    reset()
                    return Model.UNSAT
                }

                // phase saving heuristic
                if (assignment.decisionLevel > assumptions.size && polarity[nextDecisionLiteral.variable] == LBool.FALSE) {
                    nextDecisionLiteral = nextDecisionLiteral.neg
                }

                uncheckedEnqueue(nextDecisionLiteral, null)
            }
        }
    }

    fun reset() {
        backtrack(0)
    }

    // return current assignment of variables
    private fun getModel(): Model = Model(
        assignment.value.map {
            when (it) {
                LBool.TRUE, LBool.UNDEF -> true
                LBool.FALSE -> false
            }
        },
    )

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
        db.clauses.add(clause)
        if (clause.lits.isNotEmpty()) {
            addWatchers(clause)
        }
    }

    // add clause and add watchers to it
    private fun addLearnt(clause: Clause) {
        require(clause.size != 1)
        db.learnts.add(clause)
        if (clause.lits.isNotEmpty()) {
            addWatchers(clause)
        }
    }

    // return conflict clause, or null if there is no conflict clause
    private fun propagate(): Clause? {
        var conflict: Clause? = null
        while (assignment.qhead < assignment.trail.size) {
            val lit = assignment.dequeue()!!

            if (value(lit) == LBool.FALSE) {
                return assignment.reason(lit.variable)
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
                    clause.lits.swap(0, 1)
                }

                // if first watcher (not lit) is true then the clause is already true, skipping it
                if (value(clause[0]) == LBool.TRUE) continue

                // Index of the first literal in the clause not assigned to false
                var firstNotFalse = -1
                for (ind in 2 until clause.size) {
                    if (value(clause[ind]) != LBool.FALSE) {
                        firstNotFalse = ind
                        break
                    }
                }

                if (firstNotFalse == -1 && value(clause[0]) == LBool.FALSE) {
                    // all the literals in the clause are already assigned to false
                    conflict = clause
                } else if (firstNotFalse == -1) { // getValue(brokenClause[0]) == VarValue.UNDEFINED
                    // the only unassigned literal (which is the second watcher) in the clause must be true
                    uncheckedEnqueue(clause[0], clause)
                } else {
                    // there is at least one literal in the clause not assigned to false,
                    // so we can use it as a new first watcher instead
                    watchers[clause[firstNotFalse]].add(clause)
                    clause.lits.swap(firstNotFalse, 1)
                    clausesToKeep.removeLast()
                }
            }

            watchers[lit.neg] = clausesToKeep

            if (conflict != null) break
        }
        return conflict
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

        conflict.lits.forEach { lit ->
            if (assignment.level(lit.variable) == assignment.decisionLevel) {
                seen[lit.variable] = true
                numberOfActiveVariables++
            } else {
                lemma.add(lit)
            }
        }

        var lastLevelWalkIndex = assignment.trail.lastIndex

        // The UIP is the only literal in the current decision level
        // of the conflict clause. To build it, we walk back on the
        // last level of the trail and replace all but one literal
        // in the conflict clause by their reason.
        while (numberOfActiveVariables > 1) {
            val v = assignment.trail[lastLevelWalkIndex--].variable
            if (!seen[v]) continue

            // The null assertion is safe because we only traverse
            // the last level, and every variable on this level
            // has a reason except for the decision variable,
            // which will not be visited because even if it is seen,
            // it is the last seen variable in order of the trail.
            assignment.reason(v)!!.lits.forEach { u ->
                val current = u.variable
                if (assignment.level(current) != assignment.decisionLevel) {
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

        assignment.trail.last { seen[it.variable] }.let { lit ->
            val v = lit.variable
            lemma.add(if (value(v.posLit) == LBool.TRUE) v.negLit else v.posLit)

            // Simplify clause by removing redundant literals which follow from their reasons
            currentMinimizationMark++
            lemma.forEach { minimizeMarks[it] = currentMinimizationMark }
            newClause = Clause(
                lemma.filter { possiblyImpliedLit ->
                    assignment.reason(possiblyImpliedLit.variable)?.lits?.any {
                        minimizeMarks[it] != currentMinimizationMark
                    } ?: true
                }.toMutableList(),
            )

            val uipIndex = newClause.lits.indexOfFirst { it.variable == v }
            // move UIP vertex to 0 position
            newClause.lits.swap(uipIndex, 0)
            seen[v] = false
        }
        // move last defined literal to 1 position
        if (newClause.size > 1) {
            val secondMax =
                newClause.lits.drop(1).indices.maxByOrNull { assignment.level(newClause[it + 1].variable) } ?: 0
            newClause.lits.swap(1, secondMax + 1)
        }
        return newClause
    }
}
