package org.kosat

/**
 * Solves [cnf] and returns
 * `null` if request unsatisfiable
 * [emptyList] is request is tautology
 * assignments of literals otherwise
 */
fun solveCnf(cnf: CnfRequest): List<Boolean>? {
    val clauses = (cnf.clauses.map { it.toClause() }).toMutableList()
    val solver = CDCL(clauses, cnf.vars)
    val result = solver.solve()
    return if (result == SolveResult.SAT) {
        solver.getModel()
    } else {
        null
    }
}

class CDCL {
    /**
     * Initial externally added clauses by [newClause].
     * Clauses of size 1 are never stored and instead are located at level 0
     * on the [trail].
     */
    val clauses = mutableListOf<Clause>()

    /**
     * The clauses learnt by the solver during the conflict analysis.
     * This should be replaced by a more efficient data structure
     * with a proper reduction mechanism. Learned clauses of size 1
     * are being [uncheckedEnqueue]'d to the level 0 of the trail.
     */
    val learnts = mutableListOf<Clause>()

    /**
     * Can solver perform the search? This becomes false if given constraints
     * cause unsatisfiability in a trivial way (e.g. empty clause, conflicting
     * unit clauses) and whether the solver can continue the search.
     */
    private var ok = true

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
     * Trail of assignments, contains literals in the order they were assigned.
     */
    private val trail: MutableList<Lit> = mutableListOf()

    /**
     * Index of first element in the [trail] which has not been propagated yet.
     */
    private var qhead = 0

    /**
     * Two-watched literals heuristic.
     * `i`-th element of this list is the set of clauses watched by variable `i`.
     */
    private val watchers: MutableList<MutableList<Clause>> = mutableListOf()

    // controls the learned clause database reduction, should be replaced and moved in the future
    private var reduceNumber = 6000.0
    private var reduceIncrement = 500.0

    /**
     * The maximum amount of probes expected to be returned
     * by [generateProbes].
     *
     * @see [failedLiteralProbing]
     */
    private val flpMaxProbes = 1000

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
     * The branching heuristic, used to choose the next decision variable.
     */
    private val variableSelector: VariableSelector = VSIDS(numberOfVariables, this)

    /**
     * The restart strategy, used to decide when to restart the search.
     * @see [solve]
     */
    private val restarter = Restarter(this)

    /**
     * Whether a variable from the conflict clause is from the last
     * [level]. Used exclusively in [analyzeConflict] to avoid
     * re-allocations.
     */
    private val seenInAnalyzeConflict = MutableList(numberOfVariables) { false }

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

        seenInAnalyzeConflict.add(false)

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
     * Remove variables from the trail until the given layer is reached,
     * and reset the decision level to that layer.
     *
     * @param untilLevel the layer to stop at. Variables on this layer will **not** be removed.
     */
    fun cancelUntil(untilLevel: Int) {
        while (trail.isNotEmpty() && vars[trail.last().variable].level > untilLevel) {
            val lit = trail.removeLast()
            val v = lit.variable
            polarity[v] = getValue(v.posLit)
            setValue(v.posLit, LBool.UNDEFINED)
            vars[v].reason = null
            vars[v].level = -1
            variableSelector.backTrack(v)
        }

        qhead = trail.size
        level = untilLevel
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
    fun solve(currentAssumptions: List<Lit>): SolveResult {
        assumptions = currentAssumptions
        variableSelector.initAssumptions(assumptions)

        val result = solve()

        if (result == SolveResult.UNSAT) {
            assumptions = emptyList()
            return result
        }

        val model = getModel()

        currentAssumptions.forEach { lit ->
            if (model[lit.variable] != lit.isPos) {
                assumptions = emptyList()
                return SolveResult.UNSAT
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

    fun solve(): SolveResult {
        var numberOfConflicts = 0
        var numberOfDecisions = 0

        if (!ok) {
            return SolveResult.UNSAT
        }

        if (clauses.isEmpty()) {
            return SolveResult.SAT
        }

        if (clauses.any { it.all { lit -> getValue(lit) == LBool.FALSE } }) {
            return SolveResult.UNSAT
        }

        cancelUntil(0)
        cachedModel = null

        variableSelector.build(clauses)

        preprocess()?.let { return it }

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
                    return SolveResult.UNSAT
                }

                // build new clause by conflict clause
                val lemma = analyzeConflict(conflictClause)

                // compute lbd "score" for lemma
                lemma.lbd = lemma.distinctBy { vars[it.variable].level }.size

                // return to decision level where lemma would be propagated
                val level = if (lemma.size > 1) vars[lemma[1].variable].level else 0
                cancelUntil(level)

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
                    // println("KoSat conflicts:   $numberOfConflicts")
                    // println("KoSat decisions:   $numberOfDecisions")
                    return SolveResult.SAT
                }

                // try to guess variable
                level++
                var nextDecisionLiteral = variableSelector.nextDecision(vars, level)
                numberOfDecisions++

                // in case there is assumption propagated to false
                if (nextDecisionLiteral.isUndef) {
                    return SolveResult.UNSAT
                }

                // phase saving heuristic
                if (level > assumptions.size && polarity[nextDecisionLiteral.variable] == LBool.FALSE) {
                    nextDecisionLiteral = nextDecisionLiteral.neg
                }

                uncheckedEnqueue(nextDecisionLiteral)
            }
        }
    }

    /**
     * Since returning the model is a potentially expensive operation, we cache
     * the result of the first call to [getModel] and return the cached value
     * on subsequent calls. This is reset in [solve].
     */
    private var cachedModel: List<Boolean>? = null

    /**
     * Return the assignment of variables. This function is meant to be used
     * when the solver returns [SolveResult.SAT] after a call to [solve].
     */
    fun getModel(): List<Boolean> {
        if (cachedModel != null) return cachedModel!!

        cachedModel = vars.map {
            when (it.value) {
                LBool.TRUE, LBool.UNDEFINED -> true
                LBool.FALSE -> false
            }
        }

        return cachedModel!!
    }

    // ---- Preprocessing ---- //

    /**
     * Preprocessing is ran before the main loop of the solver,
     * allowing to spend some time simplifying the problem
     * in exchange for a possibly faster solving time.
     *
     * @return if the solution is conclusive after preprocessing,
     * return the model, otherwise return null
     */
    fun preprocess(): SolveResult? {
        require(level == 0)

        // Don't bother with anything if there is already
        // a level 0 conflict
        propagate()?.let { return SolveResult.UNSAT }

        failedLiteralProbing()?.let { return it }

        // Without this, we might let the solver propagate nothing
        // and make a decision after all values are set.
        if (trail.size == numberOfVariables) {
            return SolveResult.SAT
        }

        return null
    }

    /**
     * Literal probing is only useful if the probe can lead
     * lead to derivation of something by itself. We can
     * generate list of possibly useful probes ahead of time.
     *
     * TODO: Right now, this implementation is very naive.
     *       If there is a cycle in binary implication graph,
     *       we will generate a lot of useless probes that will
     *       propagate in exactly the same way and possibly
     *       produce a lot of duplicate/implied binary clauses.
     *       This will be fixed with the implementation of
     *       Equivalent Literal Substitution (ELS).
     *
     *  @return list of literals to try probing with
     */
    private fun generateProbes(): List<Lit> {
        val probes = mutableSetOf<Lit>()

        for (clause in clauses) {
            // (A | B) <==> (-A -> B) <==> (-B -> A)
            // Both -A and -B can be used as probes
            if (clause.size == 2) {
                val (a, b) = clause
                if (getValue(a) == LBool.UNDEFINED) probes.add(a.neg)
                if (probes.size >= flpMaxProbes) break
                if (getValue(b) == LBool.UNDEFINED) probes.add(b.neg)
                if (probes.size >= flpMaxProbes) break
            }
        }

        return probes.toMutableList()
    }

    /**
     * Try to propagate each probe and see if it leads to
     * deduction of new binary clauses, or maybe even a
     * conflict.
     *
     * Consider clauses (-1, 2), (-1, -2, 3), and a probe 1.
     * By propagating 1, we can derive 2, which in turn
     * allows us to derive 3. This means that we can add
     * a new binary clause (-1, 3) to the problem. There are
     * other mechanisms that can derive this clause, but
     * probing is one of them.
     */
    private fun failedLiteralProbing(): SolveResult? {
        val probesToTry = generateProbes()

        for (probe in probesToTry) {
            // If we know that the probe is already true, just skip it.
            if (getValue(probe) == LBool.TRUE) {
                continue
            }

            level++
            uncheckedEnqueue(probe)
            val startOfProbeTrail = trail.size
            val conflictClause = propagate()

            if (conflictClause != null) {
                // Assigning a value to a probe lead to a conflict
                // immediately means that the probe must be false.
                cancelUntil(0)

                if (getValue(probe) == LBool.TRUE) {
                    return SolveResult.UNSAT
                }

                uncheckedEnqueue(probe.neg)

                // Can we learn more while we are at level 0?
                propagate()?.let { return SolveResult.UNSAT }
            } else {
                // Going through the trail and checking if there
                // are any literals with non-binary reason clauses.
                for (lit in trail.drop(startOfProbeTrail)) {
                    val reason = vars[lit.variable].reason!!
                    if (reason.size > 2) {
                        // (probe -> lit) <==> (-probe | lit)
                        val newBinaryClause = Clause(mutableListOf(probe.neg, lit))
                        addClause(newBinaryClause)

                        if (probe.neg in reason) {
                            // the clause is subsumed by the discovered binary clause
                            reason.deleted = true
                        }
                    }
                }

                cancelUntil(0)
            }
        }

        clauses.retainAll { !it.deleted }

        return null
    }

    // ---- Two watchers ---- //

    /**
     * Add watchers to new clause. Expected to be run
     * in [addClause] and in [addLearnt]
     */
    private fun addWatchers(clause: Clause) {
        require(clause.size > 1)
        watchers[clause[0]].add(clause)
        watchers[clause[1]].add(clause)
    }

    // ---- CDCL functions ---- //

    /**
     * Add new clause and watchers to it.
     *
     * This function assumes that the clause size is at least 2,
     * and it is expected to be run by the solver internally to
     * add clauses to the solver. This will be moved to the
     * proper clause database in the future.
     */
    private fun addClause(clause: Clause) {
        if (clause.isEmpty()) ok = false
        if (!ok) return

        require(clause.size > 1)
        clauses.add(clause)
        addWatchers(clause)
    }

    /**
     * Add new learnt clause and watchers to it.
     */
    private fun addLearnt(clause: Clause) {
        require(clause.size != 1)
        learnts.add(clause)
        if (clause.isNotEmpty()) {
            addWatchers(clause)
        }
    }

    /**
     * Propagate all the literals in the [trail] that are
     * not yet propagated. If a conflict is found, return
     * the clause that caused it.
     *
     * This function takes every literal on the trail that
     * has not been propagated (that is, all literals for
     * which [qhead] <= index < [trail].size) and applies
     * the unit propagation rule to it, possibly leading
     * to deducing new literals. The new literals are added
     * to the trail, and the process is repeated until no
     * more literals can be propagated, or a conflict
     * is found.
     */
    private fun propagate(): Clause? {
        var conflict: Clause? = null
        while (qhead < trail.size) {
            val lit = trail[qhead++]

            check(getValue(lit) == LBool.TRUE)

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

    /**
     * Analyzes the conflict clause returned by [propagate]
     * and returns a new clause that can be learnt.
     * Post-conditions:
     *      - first element in clause has max (current) propagate level
     *      - second element in clause has second max propagate level
     */
    private fun analyzeConflict(conflict: Clause): Clause {
        var numberOfActiveVariables = 0
        val lemma = mutableSetOf<Lit>()

        conflict.forEach { lit ->
            if (vars[lit.variable].level == level) {
                seenInAnalyzeConflict[lit.variable] = true
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
            if (!seenInAnalyzeConflict[v]) continue

            // The null assertion is safe because we only traverse
            // the last level, and every variable on this level
            // has a reason except for the decision variable,
            // which will not be visited because even if it is seen,
            // it is the last seen variable in order of the trail.
            vars[v].reason!!.forEach { u ->
                val current = u.variable
                if (vars[current].level != level) {
                    lemma.add(u)
                } else if (current != v && !seenInAnalyzeConflict[current]) {
                    seenInAnalyzeConflict[current] = true
                    numberOfActiveVariables++
                }
            }

            seenInAnalyzeConflict[v] = false
            numberOfActiveVariables--
        }

        var newClause: Clause

        trail.last { seenInAnalyzeConflict[it.variable] }.let { lit ->
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
            seenInAnalyzeConflict[v] = false
        }
        // move last defined literal to 1 position
        if (newClause.size > 1) {
            val secondMax = newClause.drop(1).indices.maxByOrNull { vars[newClause[it + 1].variable].level } ?: 0
            newClause.swap(1, secondMax + 1)
        }
        return newClause
    }
}
