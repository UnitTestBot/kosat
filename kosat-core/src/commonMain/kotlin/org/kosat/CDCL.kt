package org.kosat

import org.kosat.cnf.CNF

/**
 * Solves [cnf] and returns
 * `null` if request unsatisfiable
 * [emptyList] is request is tautology
 * assignments of literals otherwise
 */
fun solveCnf(cnf: CnfRequest): List<Boolean>? {
    val clauses = cnf.clauses.map { Clause.fromDimacs(it) }.toMutableList()
    val solver = CDCL(clauses, cnf.vars)
    val result = solver.solve()
    return if (result == SolveResult.SAT) {
        solver.getModel()
    } else {
        null
    }
}

/**
 * CDCL (Conflict-Driven Clause Learning) solver instance
 * for solving Boolean satisfiability (SAT) problem.
 */
class CDCL {
    /**
     * Clause database.
     */
    private val db: ClauseDatabase = ClauseDatabase(this)

    /**
     * Assignment.
     */
    val assignment: Assignment = Assignment(this)

    var dratBuilder: AbstractDratBuilder = NoOpDratBuilder()

    /**
     * Can solver perform the search? This becomes false if given constraints
     * cause unsatisfiability in some way.
     */
    private var ok = true

    /**
     * Two-watched literals heuristic.
     *
     * `i`-th element of this list is the set of clauses watched by variable `i`.
     */
    val watchers: MutableList<MutableList<Clause>> = mutableListOf()

    /**
     * The number of variables.
     */
    var numberOfVariables: Int = 0
        private set

    /**
     * The maximum amount of probes expected to be returned
     * by [generateProbes].
     *
     * @see [failedLiteralProbing]
     */
    private val flpMaxProbes = 1000

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
    private val variableSelector: VariableSelector = VSIDS(numberOfVariables)

    /**
     * The restart strategy, used to decide when to restart the search.
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
     *
     * @param initialClauses the initial clauses.
     * @param initialVarsNumber the number of variables in the problem, if known.
     *   Can help to avoid resizing of internal data structures.
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

    constructor(cnf: CNF) : this(cnf.clauses.map { Clause.fromDimacs(it) }, cnf.numVars)

    /**
     * Allocate a new variable in the solver.
     *
     * The [newClause] technically adds variables automatically,
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

        // Used for lemma minimization in analyzeConflict()
        minimizeMarks.add(0)
        minimizeMarks.add(0)

        return numberOfVariables
    }

    fun value(lit: Lit): LBool {
        return assignment.value(lit)
    }

    /**
     * Add a new clause to the solver.
     */
    fun newClause(clause: Clause) {
        check(assignment.decisionLevel == 0)

        // Return early when already UNSAT
        if (!ok) return

        // Add not mentioned variables from the new clause
        val maxVar = clause.lits.maxOfOrNull { it.variable.index } ?: 0
        while (numberOfVariables < maxVar) {
            newVariable()
        }

        // Remove falsified literals from the new clause
        clause.lits.removeAll { value(it) == LBool.FALSE }

        // If the clause contains complementary literals, it is useless,
        // otherwise removes duplicate literals in it.
        if (sortDedupAndCheckComplimentary(clause.lits)) return

        when (clause.size) {
            // Empty clause is an immediate UNSAT
            0 -> finishWithUnsat()

            // Enqueue the literal from a unit clauses.
            1 -> {
                // Note that this enqueue can't cause a conflict
                // because if the negation of the literal is in the clause,
                // it is already removed above.
                check(assignment.value(clause[0]) != LBool.FALSE)

                if (assignment.value(clause[0]) == LBool.UNDEF) {
                    assignment.uncheckedEnqueue(clause[0], null)
                }
            }

            else -> attachClause(clause)
        }
    }

    fun backtrack(level: Int) {
        while (assignment.trail.isNotEmpty() && assignment.level(assignment.trail.last().variable) > level) {
            val lit = assignment.trail.removeLast()
            val v = lit.variable
            polarity[v] = assignment.value(v)
            assignment.unassign(v)
            variableSelector.backTrack(v)
        }

        check(assignment.qhead >= assignment.trail.size)
        assignment.qhead = assignment.trail.size
        assignment.decisionLevel = level
    }

    /**
     * Used for phase saving heuristic. Memorizes the polarity of
     * the given variable when it was last assigned, but reset during backtracking.
     */
    private var polarity: MutableList<LBool> = mutableListOf()

    /**
     * The assumptions given to an incremental solver.
     */
    private var assumptions: MutableList<Lit> = mutableListOf()

    /**
     * Solves the CNF problem using the CDCL algorithm.
     *
     * @return The [result][SolveResult] of the solving process:
     *   [SolveResult.SAT], [SolveResult.UNSAT], or [SolveResult.UNKNOWN].
     */
    fun solve(currentAssumptions: List<Lit> = emptyList()): SolveResult {
        assumptions = currentAssumptions.toMutableList()

        // If given clauses are already cause UNSAT, no need to do anything
        if (!ok) return finishWithUnsat()

        // Check if the assumptions are trivially unsatisfiable,
        // and remove duplicates along the way.
        if (sortDedupAndCheckComplimentary(assumptions)) return finishWithAssumptionsUnsat()

        // Clean up from the previous solve
        if (assignment.decisionLevel > 0) backtrack(0)
        cachedModel = null

        // If the problem is trivially SAT, simply return the result
        // (and check for assumption satisfiability)
        if (db.clauses.isEmpty()) return finishWithSatIfAssumptionsOk()

        // Check for an immediate level 0 conflict
        propagate()?.let { return finishWithUnsat() }

        // Rebuild the variable selector
        // TODO: is there a way to not rebuild the selector every solve?
        variableSelector.build(db.clauses)

        // Enqueue the assumptions in the selector
        variableSelector.initAssumptions(assumptions)

        preprocess()?.let { return it }

        return search()
    }

    // ---- Search ---- //

    /**
     * The main CDCL search loop.
     *
     * @return the result of the search.
     */
    private fun search(): SolveResult {
        while (true) {
            require(assignment.qhead == assignment.trail.size)

            // If (the problem is already) SAT, return the current assignment
            if (assignment.trail.size == numberOfVariables) {
                return finishWithSatIfAssumptionsOk()
            }

            // At this point, the state of the solver is predictable and coherent,
            // the trail is propagated, the clauses are satisfied,
            // it is safe to backtrack, make a decision, remove or add clauses, etc.
            // We use this opportunity to do some "maintenance".
            // For example, it is safe to shrink clauses
            // (by removing the falsified literals from them),
            // because it won't cause a clause becoming empty or unit.

            db.reduceIfNeeded()
            restarter.restartIfNeeded()

            // And after that, we are ready to make a decision.
            assignment.newDecisionLevel()
            var nextDecisionLiteral = variableSelector.nextDecision(assignment)

            // We always choose the assumption literals first, then the rest.
            // If there are no non-assumed literals left, the problem is
            // UNSAT under given assumptions.
            nextDecisionLiteral ?: return finishWithAssumptionsUnsat()

            // TODO: currently, to check if the literal is assumed or guessed, we check
            //       the decision level. This is not precise (some assumptions can be deduced)
            //       and error-prone.
            // Use the phase from the search before, if possible (so called "Phase Saving")
            if (assignment.decisionLevel > assumptions.size && polarity[nextDecisionLiteral.variable] == LBool.FALSE) {
                nextDecisionLiteral = nextDecisionLiteral.neg
            }

            // Enqueue the decision literal, expect it to propagate at the next iteration.
            assignment.uncheckedEnqueue(nextDecisionLiteral, null)

            // Propagate the decision,
            // in case there is a conflict, backtrack, and repeat
            // (until the conflict is resolved).
            val shouldContinue = propagateAnalyzeBacktrack()

            // If the conflict on level 0 is reached, the problem is UNSAT.
            if (!shouldContinue) return finishWithUnsat()
        }
    }

    /**
     * [propagate]. If the conflict is found, [analyzeConflict], [backtrack],
     * and with the literal learned from the conflict, repeat the process.
     *
     * If level 0 conflict is eventually found, return false
     * (setting the solver in UNSAT state is left to the caller).
     * Otherwise, if no conflict is found and the decision has to be made,
     * return true.
     */
    private fun propagateAnalyzeBacktrack(): Boolean {
        while (true) {
            val conflict = propagate() ?: return true

            // There is a conflict on level 0, so the problem is UNSAT
            if (assignment.decisionLevel == 0) return false

            // Build new clause by analyzing conflict
            val learnt = analyzeConflict(conflict)

            // Return to decision level where lemma would be propagated
            val level = if (learnt.size > 1) assignment.level(learnt[1].variable) else 0
            backtrack(level)

            // Attach learnt to the solver
            if (learnt.size == 1) {
                assignment.uncheckedEnqueue(learnt[0], null)
            } else {
                attachClause(learnt)
                assignment.uncheckedEnqueue(learnt[0], learnt)
                db.clauseBumpActivity(learnt)
            }

            variableSelector.update(learnt)
            db.clauseDecayActivity()

            restarter.numberOfConflictsAfterRestart++
        }
    }

    /**
     * Finish solving with UNSAT (without considering assumptions),
     * mark the solver as not ok, add empty clause to the DRAT proof,
     * flush the proof and return [SolveResult.UNSAT].
     */
    private fun finishWithUnsat(): SolveResult {
        ok = false
        dratBuilder.addEmptyClauseAndFlush()
        return SolveResult.UNSAT
    }

    /**
     * Finish solving due to unsatisfiability under assumptions.
     * Solver will still be able to perform search after this.
     */
    private fun finishWithAssumptionsUnsat(): SolveResult {
        return SolveResult.UNSAT
    }

    /**
     * Finish solving with SAT and check if all assumptions are satisfied.
     * If not, return [SolveResult.UNSAT] due to assumptions being impossible
     * to satisfy, otherwise return [SolveResult.SAT].
     */
    private fun finishWithSatIfAssumptionsOk(): SolveResult {
        for (assumption in assumptions) {
            if (value(assumption) == LBool.FALSE) {
                return finishWithAssumptionsUnsat()
            }
        }

        dratBuilder.flush()
        return SolveResult.SAT
    }

    /**
     * Since returning the model is a potentially expensive operation, we cache
     * the result of the first call to [getModel] and return the cached value
     * on subsequent calls. This is reset in [solve].
     */
    private var cachedModel: MutableList<Boolean>? = null

    /**
     * Return the assignment of variables. This function is meant to be used
     * when the solver returns [SolveResult.SAT] after a call to [solve].
     */
    fun getModel(): List<Boolean> {
        if (cachedModel != null) return cachedModel!!

        cachedModel = assignment.value.map {
            when (it) {
                LBool.TRUE, LBool.UNDEF -> true
                LBool.FALSE -> false
            }
        }.toMutableList()

        for (assumption in assumptions) {
            check(value(assumption) != LBool.FALSE)
            cachedModel!![assumption.variable] = assumption.isPos
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
        require(assignment.decisionLevel == 0)
        require(assignment.qhead == assignment.trail.size)

        dratBuilder.addComment("Preprocessing")
        dratBuilder.addComment("Preprocessing: Failed literal probing")

        failedLiteralProbing()?.let { return it }

        // Without this, we might let the solver propagate nothing
        // and make a decision after all values are set.
        if (assignment.trail.size == numberOfVariables) {
            return finishWithSatIfAssumptionsOk()
        }

        dratBuilder.addComment("Post-Preprocessing cleanup")

        db.simplify()
        db.removeDeleted()

        dratBuilder.addComment("Finished preprocessing")

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

        for (clause in db.clauses) {
            // (A | B) <==> (-A -> B) <==> (-B -> A)
            // Both -A and -B can be used as probes
            if (clause.size == 2) {
                val (a, b) = clause.lits
                if (assignment.value(a) == LBool.UNDEF) probes.add(a.neg)
                if (probes.size >= flpMaxProbes) break
                if (assignment.value(b) == LBool.UNDEF) probes.add(b.neg)
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
     *
     * Now, this new added clause can be set as [VarState.reason]
     * of variable 3. In fact, every time we enqueue a literal,
     * we can guarantee that its reason is binary. That binary
     * clause, if new, is the result of Hyper-binary Resolution
     * (see `[hyperBinaryResolve]`) of the old non-binary clause
     * and reasons of literals on the trail.
     */
    private fun failedLiteralProbing(): SolveResult? {
        require(assignment.decisionLevel == 0)

        val probesToTry = generateProbes()

        for (probe in probesToTry) {
            // If we know that the probe is already assigned, skip it
            if (assignment.value(probe) != LBool.UNDEF) {
                continue
            }

            check(assignment.trail.size == assignment.qhead)

            assignment.decisionLevel++
            assignment.uncheckedEnqueue(probe, null)
            val conflict = propagateProbeAndLearnBinary()
            backtrack(0)

            if (conflict != null) {
                if (!assignment.enqueue(probe.neg, null)) {
                    return finishWithUnsat()
                }

                // Can we learn more while we are at level 0?
                propagate()?.let {
                    return finishWithUnsat()
                }
            }
        }

        return null
    }

    /**
     * A specialized version of [propagate] for [failedLiteralProbing].
     *
     * It tries to learn new binary clauses while propagating literals implied
     * by the probe. During the propagation, this function aggressively
     * prioritizes binary clauses, so that we don't learn too many redundant
     * ones. Other than that, it is the copy-paste of [propagate].
     *
     * Every time we have to propagate a non-binary clause, we perform a
     * [hyperBinaryResolve] and generate a new binary clause from it, which we
     * then assign as a reason for the deduced literal.
     */
    private fun propagateProbeAndLearnBinary(): Clause? {
        require(assignment.decisionLevel == 1)
        assignment.qheadBinaryOnly = assignment.qhead

        var conflict: Clause? = null

        // First, only try binary clauses
        propagateOnlyBinary()?.let { return it }

        while (assignment.qhead < assignment.trail.size) {
            val lit = assignment.trail[assignment.qhead++]
            val clausesToKeep = mutableListOf<Clause>()

            // Iterating with indexes to prevent ConcurrentModificationException
            // when adding new binary clauses. This is ok because any new clause
            // follows from other watched clause anyway.
            val initialWatchesSize = watchers[lit.neg].size
            for (watchedClauseIndex in 0 until initialWatchesSize) {
                val clause = watchers[lit.neg][watchedClauseIndex]
                if (clause.deleted) continue

                clausesToKeep.add(clause)

                // already used
                if (clause.size == 2) continue
                if (conflict != null) continue

                if (clause[0].variable == lit.variable) {
                    clause.lits.swap(0, 1)
                }

                if (value(clause[0]) == LBool.TRUE) continue

                var firstNotFalse = -1
                for (ind in 2 until clause.size) {
                    if (value(clause[ind]) != LBool.FALSE) {
                        firstNotFalse = ind
                        break
                    }
                }

                if (firstNotFalse == -1 && value(clause[0]) == LBool.FALSE) {
                    conflict = clause
                } else if (firstNotFalse == -1) {
                    // we deduced this literal from a non-binary clause,
                    // so we can learn a new clause
                    val newBinary = hyperBinaryResolve(clause)

                    // The new clause may subsume the old one, rendering it useless
                    // TODO: However, we don't know if this clause is learned or given,
                    //   so we can't let it be deleted. On the other hand, we can't
                    //   allow just adding it to the list of clauses to keep, because
                    //   this may result in too many clauses being kept.
                    //   This should be reworked with the new clause storage mechanism,
                    //   and new flags for clauses. Won't fix for now.
                    // TODO: this turns out to be more difficult than I expected and
                    //   requires further investigation.
                    // if (newBinary[0] in clause.lits) {
                    //     clause.deleted = true
                    //     clausesToKeep.removeLast()
                    //     newBinary.learnt = clause.learnt
                    // }

                    attachClause(newBinary)

                    // Make sure watch is not overwritten at the end of the loop
                    if (lit == newBinary[0]) clausesToKeep.add(newBinary)

                    assignment.uncheckedEnqueue(clause[0], newBinary)
                    // again, we try to only use binary clauses first
                    propagateOnlyBinary()?.let { conflict = it }
                } else {
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
     * Used in [propagateProbeAndLearnBinary] to only propagate using
     * binary clauses. It uses a separate queue pointer ([Assignment.qheadBinaryOnly]).
     */
    private fun propagateOnlyBinary(): Clause? {
        require(assignment.qheadBinaryOnly >= assignment.qhead)

        while (assignment.qheadBinaryOnly < assignment.trail.size) {
            val lit = assignment.trail[assignment.qheadBinaryOnly++]

            check(value(lit) == LBool.TRUE)

            for (clause in watchers[lit.neg]) {
                if (clause.deleted) continue
                if (clause.size != 2) continue

                val other = if (clause[0].variable == lit.variable) {
                    clause[1]
                } else {
                    clause[0]
                }

                when (value(other)) {
                    // if the other literal is true, the
                    // clause is already satisfied
                    LBool.TRUE -> continue
                    // both literals are false, there is a conflict
                    LBool.FALSE -> {
                        // at this point, it does not matter how the conflict
                        // was discovered, the caller won't do anything after
                        // this anyway, but we still need to backtrack somehow,
                        // starting from this literal:
                        assignment.qhead = assignment.qheadBinaryOnly
                        return clause
                    }
                    // the other literal is unassigned
                    LBool.UNDEF -> assignment.uncheckedEnqueue(other, clause)
                }
            }
        }

        return null
    }

    /**
     * Hyper binary resolution for [failedLiteralProbing]. This is used to
     * produce new, most efficient binary clauses in the FLP.
     *
     * The problem it is trying to solve is the following: during the
     * [failedLiteralProbing], we deduced a literal from a non-binary clause.
     * However, since the only decision literal on the trail is the probe,
     * we know that this clause can be simplified to a binary clause:
     * it is the implication from the probe to the deduced literal.
     *
     * This may not be the most "efficient" binary clause, however.
     * For example, consider clauses (-1, 2), (-2, 3), (-2, -3, 4).
     * ```
     * 1 --> 2 --> 3 --> 4
     *       |___________^
     * ```
     * We can add clause (-1, 4), but it is not the most "efficient" one.
     * Instead, we can add (-2, 4). That way (-1, 4) follows from (-1, 2)
     * and (-2, 4) through resolution anyway, potentially saving us some
     * search tree space, since, having 2, we can now deduce 4 faster.
     *
     * The antecedent of the implication is the *lowest common ancestor*
     * of the negation of all false literals in the clause in the
     * implication tree (reminder: during probing, the reason for every
     * literal in binary). This function finds this LCA, and produces
     * a binary clause of the form (-LCA, deduced literal).
     *
     * This clause is called a "Hyper-binary Resolvent", because it is
     * the resolution of the non-binary clause with the implications from
     * LCA to the negation of a literal in the clause. Only running HBR
     * during probing is introduced by PrecoSAT, and based on the fact
     * that most of the hyper-binary resolvents were generated during
     * probing on decision level 1 anyhow. (according to CaDiCaL docs)
     *
     * This function assumes that the first literal in the clause is the
     * only unassigned literal yet.
     *
     * @see failedLiteralProbing
     */
    private fun hyperBinaryResolve(clause: Clause): Clause {
        require(assignment.decisionLevel == 1)
        require(clause.size > 2)
        require(value(clause[0]) == LBool.UNDEF)

        // On level 1, the literals on the trail form a binary implication tree.
        // Therefore, the negation of all literal (on level > 0) in the clause
        // has a unique **lowest common ancestor**, commonly denoted as LCA.
        var lca: Lit? = null

        // Iteration of this look finds the LCA of
        // (ancestor, clause[otherLitIndex].neg)
        root@ for (otherLitIndex in 1 until clause.size) {
            var lit = clause[otherLitIndex].neg
            if (assignment.level(lit.variable) == 0) continue

            if (lca == null) lca = lit

            while (lca != lit) {
                val lcaVar = lca!!.variable
                val litVar = lit.variable

                // If any of the reasons is null, it means that the literal
                // is the probe itself, in the root of the implication tree.
                // In this case, we reached the root of the tree.
                if (assignment.reason(lcaVar) == null) {
                    break@root
                }

                if (assignment.reason(litVar) == null) {
                    lca = lit
                    break@root
                }

                // To find the LCA, we repeatedly (in this while)
                // go up the tree from the literal with the larger
                // index on the trail (this literal is deeper)
                if (assignment.trailIndex(lcaVar) > assignment.trailIndex(litVar)) {
                    check(assignment.reason(lcaVar)!!.size == 2)
                    val (a, b) = assignment.reason(lcaVar)!!.lits
                    // TODO: this can be done with xor,
                    //   will fix after merging feature/assignment
                    lca = if (a == lca) b.neg else a.neg
                } else {
                    check(assignment.reason(litVar)!!.size == 2)
                    val (a, b) = assignment.reason(litVar)!!.lits
                    lit = if (a == lit) b.neg else a.neg
                }
            }
        }

        requireNotNull(lca)
        return Clause(mutableListOf(lca.neg, clause[0]), true)
    }

    // ---- CDCL functions ---- //

    /**
     * Add [clause] into the database and attach watchers for it.
     */
    private fun attachClause(clause: Clause) {
        require(clause.size >= 2) { clause }
        check(ok)
        db.add(clause)
        watchers[clause[0]].add(clause)
        watchers[clause[1]].add(clause)
        if (clause.learnt) dratBuilder.addClause(clause)
    }

    /**
     * Remove [clause] from the database. Watchers will be detached
     * later, during [ClauseDatabase.reduceIfNeeded].
     */
    fun detachClause(clause: Clause) {
        check(ok)
        clause.deleted = true
        if (clause.learnt) dratBuilder.deleteClause(clause)
    }

    /**
     * Propagate all the literals in the trail that are
     * not yet propagated. If a conflict is found, return
     * the clause that caused it.
     *
     * This function takes every literal on the trail that
     * has not been propagated (that is, all literals for
     * which `qhead <= index < trail.size`) and applies
     * the unit propagation rule to it, possibly leading
     * to deducing new literals. The new literals are added
     * to the trail, and the process is repeated until no
     * more literals can be propagated, or a conflict
     * is found.
     */
    private fun propagate(): Clause? {
        check(ok)

        var conflict: Clause? = null

        while (assignment.qhead < assignment.trail.size) {
            val lit = assignment.dequeue()!!

            check(value(lit) == LBool.TRUE)

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
                    assignment.uncheckedEnqueue(clause[0], clause)
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
     * Analyzes the conflict clause returned by [propagate]
     * and returns a new clause that can be learnt.
     * Post-conditions:
     *      - first element in clause has max (current) propagate level
     *      - second element in clause has second max propagate level
     */
    private fun analyzeConflict(conflict: Clause): Clause {
        var numberOfActiveVariables = 0
        val learntLits = mutableSetOf<Lit>()
        val seen = BooleanArray(numberOfVariables)

        conflict.lits.forEach { lit ->
            if (assignment.level(lit.variable) == assignment.decisionLevel) {
                seen[lit.variable] = true
                numberOfActiveVariables++
            } else {
                learntLits.add(lit)
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
            val reason = assignment.reason(v)!!

            db.clauseBumpActivity(reason)

            reason.lits.forEach { u ->
                val current = u.variable
                if (assignment.level(current) != assignment.decisionLevel) {
                    learntLits.add(u)
                } else if (current != v && !seen[current]) {
                    seen[current] = true
                    numberOfActiveVariables++
                }
            }

            seen[v] = false
            numberOfActiveVariables--
        }

        var learnt: Clause

        assignment.trail.last { seen[it.variable] }.let { lit ->
            val v = lit.variable
            learntLits.add(if (value(v.posLit) == LBool.TRUE) v.negLit else v.posLit)

            // Simplify clause by removing redundant literals which follow from their reasons
            currentMinimizationMark++
            learntLits.forEach { minimizeMarks[it] = currentMinimizationMark }
            learnt = Clause(
                learntLits.filter { possiblyImpliedLit ->
                    assignment.reason(possiblyImpliedLit.variable)?.lits?.any {
                        minimizeMarks[it] != currentMinimizationMark
                    } ?: true
                }.toMutableList(),
                learnt = true,
            )

            val uipIndex = learnt.lits.indexOfFirst { it.variable == v }
            // move UIP vertex to 0 position
            learnt.lits.swap(uipIndex, 0)
            seen[v] = false
        }
        // move last defined literal to 1 position
        if (learnt.size > 1) {
            val secondMax = learnt.lits.drop(1).indices.maxByOrNull {
                assignment.level(learnt[it + 1].variable)
            } ?: 0
            learnt.lits.swap(1, secondMax + 1)
        }

        // compute lbd "score" for lemma
        learnt.lbd = learnt.lits.distinctBy { assignment.level(it.variable) }.size

        return learnt
    }
}
