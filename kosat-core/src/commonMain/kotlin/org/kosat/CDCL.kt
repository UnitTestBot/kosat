package org.kosat

import org.kosat.cnf.CNF
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

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
    val db: ClauseDatabase = ClauseDatabase(this)

    /**
     * Assignment.
     */
    val assignment: Assignment = Assignment(this)

    /**
     * DRAT proof builder. Can be used to generate DRAT proofs,
     * but disabled by default. It is intentionally made public
     * variable.
     */
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
     * The reconstruction stack, used to restore the solver state
     * before adding new clauses or assumptions incrementally and
     * to reconstruct the model after solving.
     *
     * @see getModel
     * @see ReconstructionStack
     */
    val reconstructionStack: ReconstructionStack = ReconstructionStack()

    /**
     * The maximum amount of probes expected to be returned
     * by [generateProbes].
     *
     * @see [failedLiteralProbing]
     */
    private val flpMaxProbes = 1000

    /**
     * Amount of [equivalentLiteralSubstitution] rounds before and after
     * [failedLiteralProbing] to perform.
     */
    private val elsRounds = 5

    private val bveConfig = object {
        val varsLimit = Int.MAX_VALUE
        // val relativeEfficiencyThreshold = 0.5
        // val minimumVarsToTry = 30
        val resolventSizeLimit = 16
        val maxVarScore = 1600
        var maxNewResolventsPerElimination = 16
        val varScoreSumWeight = -1.0
        val varScoreProdWeight = 1.0
    }

    private val bveStats = object {
        var eliminationAttempts = 0
        var eliminatedVariables = 0
        var resolventsAdded = 0
        var clausesResolved = 0
        var unitsAssigned = 0
        var clausesAttached = 0
        var clausesDeleted = 0
        var clausesStrengthened = 0
        var clausesSubsumed = 0
        var tautologicalResolvents = 0
        var resolventsTooBig = 0
        var gatesFound = 0
    }

    /**
     * The branching heuristic, used to choose the next decision variable.
     */
    private val variableSelector: VariableSelector = VSIDS(assignment.numberOfVariables)

    /**
     * The restart strategy, used to decide when to restart the search.
     * @see [solve]
     */
    private val restarter = Restarter(this)

    /**
     * A list of clauses which were added since the last call to [solve].
     */
    private val newClauses = mutableListOf<Clause>()

    /**
     * Create a new solver instance with no clauses.
     */
    constructor() : this(mutableListOf<Clause>())

    /**
     * Create a new solver instance with given clauses.
     *
     * @param initialClauses the initial clauses.
     * @param initialVarsNumber the number of variables in the problem, if known.
     *        Can help to avoid resizing of internal data structures.
     */
    constructor(
        initialClauses: Iterable<Clause>,
        initialVarsNumber: Int = 0,
    ) {
        while (assignment.numberOfVariables < initialVarsNumber) {
            newVariable()
        }

        initialClauses.forEach { newClause(it) }
        polarity = MutableList(assignment.numberOfVariables) { LBool.UNDEF }
    }

    constructor(cnf: CNF) : this(cnf.clauses.map { Clause.fromDimacs(it) }, cnf.numVars)

    /**
     * Allocate a new variable in the solver.
     *
     * The [newClause] technically adds variables automatically,
     * but sometimes not all variables have to be mentioned in the clauses.
     */
    fun newVariable() {
        // Watch
        watchers.add(mutableListOf())
        watchers.add(mutableListOf())

        // Assignment
        assignment.addVariable()

        // Variable selection strategy
        variableSelector.addVariable()

        // Phase saving heuristics
        polarity.add(LBool.UNDEF)
    }

    /**
     * Return a value of the given literal, assuming it is not substituted.
     * It is a shortcut for [Assignment.value].
     *
     * @see Assignment.value
     */
    private fun value(lit: Lit): LBool {
        return assignment.value(lit)
    }

    /**
     * Add a new clause to the solver.
     */
    fun newClause(clause: Clause) {
        // Return early when already UNSAT
        if (!ok) return

        // Add not mentioned variables from the new clause
        val maxVar = clause.lits.maxOfOrNull { it.variable.index } ?: 0
        while (assignment.numberOfVariables < maxVar) {
            newVariable()
        }

        // Remove falsified literals from the new clause
        clause.lits.removeAll {
            assignment.isActive(it) && assignment.value(it) == LBool.FALSE
        }

        // If the clause contains complementary literals, ignore it as useless.
        if (sortDedupAndCheckComplimentary(clause.lits)) return

        newClauses.add(clause)
        clause.fromInput = true

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

            // Clauses of size >2 are added to the database.
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
    private var assumptions: DenseLitVec = DenseLitVec.empty

    /**
     * Solves the CNF problem using the CDCL algorithm.
     *
     * @return The [result][SolveResult] of the solving process:
     *   [SolveResult.SAT], [SolveResult.UNSAT], or [SolveResult.UNKNOWN].
     */
    fun solve(currentAssumptions: List<Lit> = emptyList()): SolveResult {
        // Unfreeze assumptions from the previous solve
        for (assumption in assumptions) assignment.unfreeze(assumption)
        // and assign new assumptions
        assumptions = DenseLitVec(currentAssumptions)

        // If given clauses are already cause UNSAT, no need to do anything
        if (!ok) return finishWithUnsat()

        // Check if the assumptions are trivially unsatisfiable
        if (sortDedupAndCheckComplimentary(assumptions)) return finishWithAssumptionsUnsat()

        // Clean up from the previous solve
        if (assignment.decisionLevel > 0) backtrack(0)
        cachedModel = null
        reconstructionStack.restore(this, newClauses, assumptions)
        for (assumption in assumptions) assignment.freeze(assumption)
        newClauses.clear()

        // Check for an immediate level 0 conflict
        propagate()?.let { return finishWithUnsat() }

        // Rebuild the variable selector
        // TODO: is there a way to not rebuild the selector every solve?
        variableSelector.build(db.clauses)

        // Enqueue the assumptions in the selector
        variableSelector.initAssumptions(assumptions)

        preprocess()?.let { return it }

        val time = measureTimedValue {
            search()
        }
        totalTime += time.duration
        println("Solving took ${time.duration} (total: $totalTime)")
        return time.value
    }

    companion object { var totalTime = Duration.ZERO }

    /**
     * The main CDCL search loop.
     *
     * @return the result of the search.
     */
    private fun search(): SolveResult {
        while (true) {
            check(assignment.qhead == assignment.trail.size)

            // Check if all variables are assigned, indicating satisfiability,
            // (unless assumptions are falsified)
            if (assignment.trail.size == assignment.numberOfActiveVariables) {
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

            check(assignment.qhead == assignment.trail.size)

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

            // If there is a conflict on level 0, the problem is UNSAT
            if (assignment.decisionLevel == 0) return false

            // Construct a new clause by analyzing conflict
            val learnt = analyzeConflict(conflict)

            // Return to decision level where learnt would be propagated
            val level = if (learnt.size > 1) assignment.level(learnt[1].variable) else 0
            backtrack(level)

            // Attach learnt to the solver
            if (learnt.size == 1) {
                assignment.uncheckedEnqueue(learnt[0], null)
            } else {
                attachClause(learnt)
                // Failure Driven Assertion: at level we backtracked to,
                // the learnt will be propagated and will result in a literal
                // added to the trail. We do that here.
                assignment.uncheckedEnqueue(learnt[0], learnt)
                db.clauseBumpActivity(learnt)
            }

            // Update the heuristics
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
    private var cachedModel: List<Boolean>? = null

    /**
     * Return the assignment of variables. This function is meant to be used
     * when the solver returns [SolveResult.SAT] after a call to [solve].
     */
    fun getModel(): List<Boolean> {
        if (cachedModel != null) return cachedModel!!

        cachedModel = reconstructionStack.reconstruct(assignment)

        return cachedModel!!
    }

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

        dratBuilder.addComment("Preprocessing: Equivalent literal substitution")

        // Running ELS before FLP helps to get more binary clauses
        // and remove cycles in the binary implication graph
        // to make probes for FLP more effective
        equivalentLiteralSubstitutionRounds()?.let { return it }

        dratBuilder.addComment("Preprocessing: Failed literal probing")

        failedLiteralProbing()?.let { return it }

        dratBuilder.addComment("Preprocessing: Equivalent literal substitution (post FLP)")

        // After FLP we might have gotten new binary clauses
        // though hyper-binary resolution, so we run ELS again
        // to substitute literals that are now equivalent
        equivalentLiteralSubstitutionRounds()?.let { return it }

        boundedVariableElimination()?.let { return it }

        // Without this, we might let the solver propagate nothing
        // and make a decision after all values are set.
        if (assignment.trail.size == assignment.numberOfVariables) {
            return finishWithSatIfAssumptionsOk()
        }

        dratBuilder.addComment("Post-Preprocessing cleanup")

        // Remove satisfied clauses and shrink the clauses by
        // removing falsified literals
        db.simplify()

        // Remove deleted clauses which may have occurred during preprocessing
        db.removeDeleted()

        dratBuilder.addComment("Finished preprocessing")

        return null
    }

    /**
     * Returns the list of literals that are directly implied by
     * the given literal though binary clauses.
     *
     * @see equivalentLiteralSubstitution
     */
    private fun binaryImplicationsFrom(lit: Lit): List<Lit> {
        check(value(lit) == LBool.UNDEF)
        val implied = mutableListOf<Lit>()

        for (watched in watchers[lit.neg]) {
            if (watched.deleted) continue
            if (watched.size != 2) continue

            val other = Lit(watched[0].inner xor watched[1].inner xor lit.neg.inner)

            check(value(other) != LBool.FALSE)
            if (value(other) != LBool.UNDEF) continue
            implied.add(other)
        }

        return implied
    }

    /**
     * Performs multiple rounds of [equivalentLiteralSubstitution].
     *
     * This is because ELS can be used to derive new binary clauses,
     * which in turn can be used to derive new ELS substitutions.
     */
    private fun equivalentLiteralSubstitutionRounds(): SolveResult? {
        // This simplify helps to increase the number of binary clauses
        // and allows to not think about assigned literals in ELS itself.
        db.simplify()

        for (i in 0 until elsRounds) {
            equivalentLiteralSubstitution()?.let { return it }
        }

        // We must update assumptions after ELS, because it can
        // substitute literals in assumptions, and even derive UNSAT.
        if (sortDedupAndCheckComplimentary(assumptions)) {
            return finishWithAssumptionsUnsat()
        }

        variableSelector.initAssumptions(assumptions)

        return null
    }

    /**
     * Equivalent Literal Substitution (ELS) is a preprocessing technique
     * that tries to find literals that are equivalent to each other.
     *
     * Consider clauses (-1, -2), (2, 3) and (-3, 1). In every satisfying
     * assignment, 1 is equal to -2, and -2 is equal to 3. This means that
     * we can substitute, for example, -2 and 3 with 1 in every clause,
     * and the problem will generally remain the same.
     *
     * This function tries to find such literals and substitute them. To do
     * this, it searches for strongly connected components in the binary
     * implication graph, selects one representative literal from each
     * component, and substitutes all other literals in the component with
     * the representative.
     */
    private fun equivalentLiteralSubstitution(): SolveResult? {
        require(assignment.decisionLevel == 0)

        // To find strongly connected components, we use Tarjan's algorithm.
        // https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm

        // Indicates that the literal is not visited yet
        val markUnvisited = 0
        // Indicates that the literal is in the stack (from Tarjan's algorithm)
        val markInStack = 1
        // Indicates that the literal got in the strongly connected component
        // after stack unwinding, but not substituted yet
        val markInScc = 2
        // Indicates that the literal is processed, and possibly substituted
        val markProcessed = 3

        // Marks for each literal, as above
        val marks = MutableList(assignment.numberOfVariables * 2) { markUnvisited }
        // Tarjan's algorithm counter per literal
        val num = MutableList(assignment.numberOfVariables * 2) { 0 }
        var counter = 0

        // Stack for Tarjan's algorithm
        val stack = mutableListOf<Lit>()

        // The representative literal for each variable.
        // Same for every literal in the SCC.
        val representatives = MutableList<Lit?>(assignment.numberOfVariables) { null }

        // Total number of literals substituted
        var totalSubstituted = 0

        // Tarjan's algorithm, returns the lowest number of all reachable nodes
        // from the given node, or null if the node is in a cycle with the
        // negation of itself, and the problem is UNSAT
        fun dfs(v: Lit): Int? {
            check(value(v) == LBool.UNDEF)
            check(marks[v] == 0)

            marks[v] = markInStack
            stack.add(v)

            counter++
            num[v] = counter
            var lowest = counter

            for (u in binaryImplicationsFrom(v)) {
                if (marks[u] == markUnvisited) {
                    val otherLowest = dfs(u) ?: return null
                    lowest = min(otherLowest, lowest)
                } else if (marks[u] != markProcessed) {
                    lowest = min(lowest, num[u])
                }
            }

            if (lowest == num[v]) {
                val scc = mutableListOf<Lit>()
                while (true) {
                    val u = stack.removeLast()
                    marks[u] = markInScc
                    scc.add(u)
                    if (u == v) {
                        // We can choose any literal from the SCC as a
                        // representative. We cannot substitute frozen literals,
                        // so we prioritize them as representatives. Moreover,
                        // there might be a problem with assigning different
                        // literals to the same variable, if DFS visits the same
                        // variable twice, so among all literals we choose the
                        // one with the smallest index.
                        var minFrozen = Int.MAX_VALUE
                        var minLit = Int.MAX_VALUE

                        for (w in scc) {
                            // If two complementary literals are in the same SCC,
                            // the problem is UNSAT
                            if (marks[w.neg] == markInScc) {
                                dratBuilder.addComment(
                                    "Discovered UNSAT due to complement literals being in the same SCC"
                                )
                                // Adding unit clauses is required for the proof
                                dratBuilder.addClause(Clause(DenseLitVec.of(w)))
                                dratBuilder.addClause(Clause(DenseLitVec.of(w.neg)))
                                return null
                            }
                            marks[w] = markProcessed

                            minLit = min(minLit, w.inner)
                            if (assignment.isFrozen(w)) {
                                minFrozen = min(minFrozen, w.inner)
                            }
                        }

                        val repr = if (minFrozen != Int.MAX_VALUE) Lit(minFrozen) else Lit(minLit)

                        for (w in scc) {
                            if (w != repr && !assignment.isFrozen(w)) {
                                representatives[w.variable] = repr xor w.isNeg
                            }
                        }
                        // Note that there is no need to add clauses to the proof
                        totalSubstituted += scc.size - 1
                        break
                    }
                }
            }

            return lowest
        }

        // We perform substitution for all reachable SCCs from every positive
        // literal. There is no need to do it for negative literals, because
        // those will just produce the same substitutions (the binary
        // implication graph is symmetrical), and we are likely to visit both
        // positive and negative literals anyway.
        for (varIndex in 0 until assignment.numberOfVariables) {
            val variable = Var(varIndex)
            val lit = variable.posLit

            if (
                !assignment.isActive(variable) ||
                (marks[lit] != markUnvisited || marks[lit.neg] != markUnvisited) ||
                value(lit) != LBool.UNDEF
            ) continue

            dfs(lit) ?: return finishWithUnsat()
        }

        if (totalSubstituted == 0) return null

        // Mark all substituted variables as inactive and memorize the substitution
        // in the reconstruction stack
        for (varIndex in 0 until assignment.numberOfVariables) {
            val v = Var(varIndex)
            if (representatives[v] == null) continue
            assignment.markInactive(v)
            reconstructionStack.pushSubstitution(representatives[v]!!, v.posLit)
        }

        // Replace clauses which might have simplified due to substitution
        for (clause in db.clauses + db.learnts) {
            if (clause.deleted) continue

            val willChange = clause.lits.any { representatives[it.variable] != null }
            if (!willChange) continue

            val newLits = DenseLitVec(clause.lits.map { representatives[it.variable]?.xor(it.isNeg) ?: it })
            val containsComplementary = sortDedupAndCheckComplimentary(newLits)
            // Note that clause cannot become empty,
            // however, it can contain complementary literals.
            // We simply remove such clauses.
            if (!containsComplementary) {
                val newClause = Clause(newLits, clause.learnt)
                if (newClause.size == 1) {
                    check(assignment.enqueue(newClause[0], null))
                } else {
                    attachClause(newClause)
                }
            }

            markDeleted(clause)
        }

        // Propagate all the new unit clauses which might have been discovered.
        propagate()?.let { return finishWithUnsat() }

        return null
    }

    /**
     * Literal probing is only useful if the probe can
     * lead to derivation of something by itself. We can
     * generate list of possibly useful probes ahead of time.
     *
     *  @return list of literals to try probing with
     */
    private fun generateProbes(): List<Lit> {
        val probes = mutableSetOf<Lit>()

        for (clause in db.clauses) {
            if (clause.deleted) continue
            if (clause.size == 2) continue

            // (A | B) <==> (-A -> B) <==> (-B -> A)
            // Both -A and -B can be used as probes, there is little need
            // to choose both, however.
            val (a, b) = clause.lits
            probes.add(if (a.inner < b.inner) a.neg else b.neg)
        }

        // Remove probes that follow from binary clauses,
        // leaving only roots of the binary implication graph
        for (clause in db.clauses) {
            if (clause.deleted) continue
            if (clause.size != 2) continue

            val (a, b) = clause.lits
            probes.remove(a)
            probes.remove(b)
        }

        return probes.take(flpMaxProbes).toMutableList()
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
                    var newBinary = hyperBinaryResolve(clause)

                    // Check that lit in either at index 0 of the clause and negated,
                    // or not in the clause at all.
                    check(
                        newBinary[0] == lit.neg ||
                            lit.variable != newBinary[0].variable && lit.variable != newBinary[1].variable
                    )

                    // The new clause may subsume the old one, rendering it useless
                    if (newBinary[0] in clause.lits) {
                        // We don't need to keep the clause in the watcher list
                        clausesToKeep.removeLast()
                        newBinary = newBinary.copy(learnt = clause.learnt)
                        attachClause(newBinary)
                        markDeleted(clause)
                    } else {
                        // If not, simply add the new clause
                        attachClause(newBinary)
                    }

                    // Make sure watch is not overwritten at the end of the loop
                    if (lit.neg == newBinary[0]) clausesToKeep.add(newBinary)

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

                val other = Lit(clause[0].inner xor clause[1].inner xor lit.neg.inner)

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
                    lca = Lit(lca.inner xor a.inner xor b.inner).neg
                } else {
                    check(assignment.reason(litVar)!!.size == 2)
                    val (a, b) = assignment.reason(litVar)!!.lits
                    lit = Lit(lit.inner xor a.inner xor b.inner).neg
                }
            }
        }

        requireNotNull(lca)
        return Clause(DenseLitVec.of(lca.neg, clause[0]), true)
    }

    /**
     * Priority queue for variables, used in [boundedVariableElimination].
     * It is a min-heap, where the key is the score of the variable
     * obtained by [bveVariableScore].
     */
    class VariableMinPriorityQueue(var size: Int) {
        private val keys: DoubleArray = DoubleArray(size)
        private val heap: IntArray = IntArray(size) { it }
        private val indices: MutableList<Int?> = MutableList(size) { it }

        fun contains(x: Var): Boolean = indices[x] != null

        private fun parent(v: Int) = (v - 1) / 2
        private fun left(v: Int) = 2 * v + 1
        private fun right(v: Int) = 2 * v + 2

        private fun siftUp(x: Var) {
            var v = indices[x]!!
            var p = parent(v)
            val hp = heap[p]
            val hv = heap[v]
            while (v > 0 && keys[hp] < keys[hv]) {
                indices[hv] = p
                indices[hp] = v
                heap.swap(v, p)
                v = p
                p = parent(v)
            }
        }

        private fun siftDown(x: Var) {
            var v = indices[x]!!
            while (true) {
                val l = left(v)
                val r = right(v)
                var smallest = v
                val hv = heap[v]
                if (l < size && keys[heap[l]] < keys[hv]) smallest = l
                if (r < size && keys[heap[r]] < keys[heap[smallest]]) smallest = r
                if (smallest == v) break
                indices[hv] = smallest
                indices[heap[smallest]] = v
                heap.swap(v, smallest)
                v = smallest
            }
        }

        fun getKey(x: Var): Double = keys[x]

        fun setKey(x: Var, value: Double) {
            if (!contains(x)) return
            keys[x] = value
            siftUp(x)
            siftDown(x)
        }

        fun pop(): Var {
            require(size > 0)
            val result = Var(heap[0])
            val u = size - 1
            indices[heap[0]] = u
            indices[heap[u]] = 0
            heap.swap(0, u)
            size--
            siftDown(Var(heap[0]))
            indices[result] = null
            return result
        }
    }

    /**
     * [boundedVariableElimination] requires a global mutable state, which is
     * stored here.
     */
    class EliminationState(numberOfVariables: Int) {
        /**
         * Occurrences of each literal in the problem: for every literal we
         * store the clauses it is in. Some clauses may be deleted and should
         * be ignored. The size of the occurrences list for each literal is not
         * necessarily equal to the value in [occurrenceNumbers] because we
         * don't count deleted clauses.
         */
        val occurrences: List<MutableList<Clause>> = MutableList(numberOfVariables * 2) { mutableListOf() }

        /**
         * This is the "real" amount of occurrences of each literal in the
         * problem.
         */
        val occurrenceNumbers: IntArray = IntArray(numberOfVariables * 2)

        /**
         * We use this queue to find the next variable to eliminate.
         */
        val variableOrder: VariableMinPriorityQueue = VariableMinPriorityQueue(numberOfVariables)

        /**
         * Number of not-deleted clauses in the problem. BVE creates and deletes
         * a lot of clauses, which take a lot of memory. We need garbage
         * collector to clean them up, which means we must remove references to
         * deleted clauses from time to time. After eliminating a variable,
         * we check if the number of clauses in the database (including deleted
         * ones) is more than twice the number of not-deleted clauses, which we
         * store here. If it is, we remove all deleted clauses from the
         * [db], [watchers], and [occurrences] lists.
         */
        var numberOfClauses: Int = 0

        /**
         * In [findOrGates] we use this list to store indices of binary clauses
         * that contain pivot literal. It is too expensive to create a new list
         * for each pivot, so we reuse this one for the entire BVE.
         */
        val gateMarks: IntArray = IntArray(numberOfVariables * 2)

        /**
         * In [removeSubsumedBy] we mark literals from the clause to
         * quickly check if other clauses contain all literals from it.
         */
        val subsumptionMarks: BooleanArray = BooleanArray(numberOfVariables * 2)
    }

    /**
     * The priority of a variable in [boundedVariableElimination]. The less,
     * the higher priority. The computed score is used as a key in the
     * [EliminationState.variableOrder] queue.
     */
    private fun bveVariableScore(state: EliminationState, x: Var): Double {
        val pos = state.occurrenceNumbers[x.posLit]
        val neg = state.occurrenceNumbers[x.negLit]
        val weightedSum = bveConfig.varScoreSumWeight * (pos + neg)
        val weightedProd = bveConfig.varScoreProdWeight * (pos * neg)
        return weightedSum + weightedProd
    }

    /**
     * In [boundedVariableElimination], we must keep track of occurrences list
     * ([EliminationState.occurrences]), number of clauses, and other things.
     * To simplify the code, we use this function to mark clauses as deleted
     * during BVE.
     */
    private fun bveMarkDeleted(state: EliminationState, clause: Clause) {
        require(!clause.deleted)
        require(!clause.learnt)
        bveStats.clausesDeleted++
        state.numberOfClauses--
        markDeleted(clause)
        for (lit in clause.lits) {
            state.occurrenceNumbers[lit]--
            state.variableOrder.setKey(lit.variable, bveVariableScore(state, lit.variable))
        }
    }

    /**
     * In [boundedVariableElimination], we must keep track of occurrences list
     * ([EliminationState.occurrences]), number of clauses, and other things.
     * To simplify the code, we use this function to attach new clauses.
     *
     * Similar to [bveMarkDeleted], but for adding new clauses.
     */
    private fun bveAttachClause(state: EliminationState, clause: Clause) {
        require(clause.size > 1)
        bveStats.clausesAttached++
        state.numberOfClauses++
        attachClause(clause)
        for (lit in clause.lits) {
            state.occurrences[lit].add(clause)
            state.occurrenceNumbers[lit]++
            state.variableOrder.setKey(lit.variable, bveVariableScore(state, lit.variable))
        }
    }

    /**
     * Shrinks the clause by removing all literals that are assigned to false,
     * and adds it through [bveAttachClause]. This function can derive empty
     * clause, or a unit clause which, when propagated, leads to empty clause,
     * so it returns [SolveResult.UNSAT] in those cases.
     */
    private fun bveAttachShrunkClause(state: EliminationState, clause: Clause): SolveResult? {
        if (clause.lits.any { assignment.value(it) == LBool.TRUE }) return null
        clause.lits.removeAll { assignment.value(it) == LBool.FALSE }

        when (clause.size) {
            0 -> return finishWithUnsat()
            1 -> {
                bveStats.unitsAssigned++
                if (!assignment.enqueue(clause.lits[0], null)) return finishWithUnsat()
                bvePropagate()?.let { return finishWithUnsat() }
            }
            else -> {
                bveAttachClause(state, clause)
            }
        }

        return null
    }

    /**
     * The [boundedVariableElimination] is a preprocessing technique that tries
     * to eliminate variables from the problem. It is based on multiple
     * observations, which are described in [bveTryEliminate], but the overall
     * idea is to choose a variable which does not occur often, and remove it
     * by replacing all the clauses it is in with resolvents of those clauses.
     * Of course, there can be too many resolvents, so we limit the number of
     * resolvents per elimination, as well as do other things to prevent the
     * algorithm and subsequent solving from taking too much time.
     */
    private fun boundedVariableElimination(): SolveResult? {
        require(assignment.decisionLevel == 0)

        // This state will be used all throughout the BVE
        val state = EliminationState(assignment.numberOfVariables)

        for (clause in db.clauses) {
            if (clause.deleted) continue
            state.numberOfClauses++
            for (lit in clause.lits) {
                state.occurrenceNumbers[lit]++
                state.occurrences[lit].add(clause)
                state.variableOrder.setKey(lit.variable, bveVariableScore(state, lit.variable))
            }
        }

        for (attemptNumber in 0 until bveConfig.varsLimit) {

            // We first need to find the next variable to eliminate. We do this
            // by choosing the variable with the smallest number of occurrences
            // among all active variables.

            var bestVariable: Var? = null

            while (state.variableOrder.size > 0) {
                // Variable ordering based on binary heap provides fast access
                // to the smallest element, significantly faster than simple
                // linear search.
                val v = state.variableOrder.pop()

                // Not every variable can be eliminated though. We check for
                // that here
                if (assignment.isActive(v) &&
                    !assignment.isFrozen(v) &&
                    assignment.value(v) == LBool.UNDEF &&
                    state.variableOrder.getKey(v) <= bveConfig.maxVarScore
                ) {
                    bestVariable = v
                    break
                }
            }

            if (bestVariable == null) break

            // Once we found a variable, we try to eliminate it. Note that
            // during the elimination, we can learn a unit clause which may
            // derive UNSAT.

            bveStats.eliminationAttempts++
            bveTryEliminate(state, bestVariable)?.let { return it }

            /*
            // To check how efficient the elimination is, we check the ratio
            // of eliminated variables to the number of elimination attempts,
            // after we tried to eliminate a certain number of variables.
            // If the ratio is too low, we stop the elimination, because
            // if it didn't work at the beginning, when variables are supposedly
            // easier to eliminate, it is unlikely to work later.
            if (bveStats.eliminationAttempts > bveConfig.minimumVarsToTry) {
                val relEfficiency = bveStats.eliminatedVariables.toDouble() / bveStats.eliminationAttempts
                if (relEfficiency < bveConfig.relativeEfficiencyThreshold) {
                    break
                }
            }
             */

            // This is the "garbage collection" routine we store number of
            // clauses for. If there are too many deleted clauses, we remove
            // them from the database, watchers, and occurrences list, allowing
            // the garbage collector to clean them up. It is easy to run out of
            // memory without this.
            if (db.clauses.size > state.numberOfClauses * 2) {
                db.clauses.removeAll { it.deleted }

                for (watches in watchers) {
                    watches.removeAll { it.deleted }
                }

                for (occurrenceList in state.occurrences) {
                    occurrenceList.removeAll { it.deleted }
                }
            }
        }

        // After the elimination is complete, we need to remove learnt clauses
        // which now contain eliminated variables. We also need to shrink
        // learnt clauses, because they may contain literals which are now
        // falsified.
        for (learnt in db.learnts.toMutableList()) {
            if (learnt.deleted) continue
            if (learnt.lits.any { !assignment.isActive(it) || assignment.value(it) == LBool.TRUE }) {
                markDeleted(learnt)
            } else {
                val needsShrink = learnt.lits.any { assignment.value(it) == LBool.UNDEF }
                if (!needsShrink) continue

                val newLearnt = learnt.copy(lits = learnt.lits.copy())
                newLearnt.lits.removeAll { assignment.value(it) == LBool.FALSE }

                when (newLearnt.size) {
                    0 -> return finishWithUnsat()

                    1 -> {
                        if (!assignment.enqueue(newLearnt.lits[0], null)) {
                            return finishWithUnsat()
                        }
                        bvePropagate()?.let { return finishWithUnsat() }
                    }

                    else -> attachClause(newLearnt)
                }

                markDeleted(learnt)
            }
        }

        println("Eliminated ${bveStats.eliminatedVariables} variables out of ${bveStats.eliminationAttempts}")
        println("Total clause DB mutations: additions: ${bveStats.clausesAttached}, deletions: ${bveStats.clausesDeleted}")
        println("Resolvents added: ${bveStats.resolventsAdded}, which resolved: ${bveStats.clausesResolved} clauses")
        println("Units assigned: ${bveStats.unitsAssigned}")
        println("Failed to resolve because resolvent was too big: ${bveStats.resolventsTooBig}")
        println("Tautological resolvents: ${bveStats.tautologicalResolvents}")
        println("Gates found: ${bveStats.gatesFound}")
        println("Backward subsumption removed ${bveStats.clausesSubsumed} and strengthened ${bveStats.clausesStrengthened} clauses")

        return null
    }

    /**
     * Try to eliminate a variable [pivot] from the problem. This is the core of
     * [boundedVariableElimination].
     *
     * The idea is to first find all clauses containing [pivot] variable. We
     * then perform "variable elimination by distribution", described in
     * the SatElite paper. This function can cause the problem to become UNSAT,
     * in which case it returns [SolveResult.UNSAT].
     *
     * The implementation details and overall algorithm are explained in the
     * function source code itself.
     */
    private fun bveTryEliminate(state: EliminationState, pivot: Var): SolveResult? {
        require(assignment.isActive(pivot))
        require(!assignment.isFrozen(pivot))
        require(!assignment.value(pivot) == LBool.UNDEF)

        // We first find all clauses containing the variable
        val posOccurrences = state.occurrences[pivot.posLit]
        val negOccurrences = state.occurrences[pivot.negLit]

        // This is what makes variable elimination "bounded". By setting the
        // limit for the number of resolvents, we can prevent the algorithm
        // from generating too many clauses.
        val bound: Int = bveConfig.maxNewResolventsPerElimination +
            posOccurrences.count { !it.deleted } +
            negOccurrences.count { !it.deleted }

        // As observed in SatElite paper, there are special kinds of clause
        // formations, called "gates", which, when resolved with other gates,
        // produce a tautological resolvent. Those are valuable for the
        // algorithm, so we try to find them here.
        var gateClauses: List<Clause>? = null

        // We can only use a single gate, so we just use the first one we found
        // Below we look for OR-gates. Those are constraints in the form
        // x = (a1 | a2 | ... | aK), where x is the pivot literal, and a1, a2,
        // ..., aK are other literals. This is also AND-gate for the negation
        // of the literal, of course. See the implementation for more details.
        if (gateClauses == null) gateClauses = findOrGates(state, pivot.posLit)
        if (gateClauses == null) gateClauses = findOrGates(state, pivot.negLit)
        if (gateClauses != null) bveStats.gatesFound++

        // Finally, we perform the resolutions.
        val resolventsToAdd = mutableListOf<Clause>()

        for (i in 0 until posOccurrences.size) {
            val posClause = posOccurrences[i]
            if (posClause.deleted) continue

            for (j in 0 until negOccurrences.size) {
                val negClause = negOccurrences[j]
                if (negClause.deleted) continue

                // As noted above, gates hold special meaning. Turns out
                // resolving gate clause with another gate clause produces
                // a tautological resolvent, so we skip those.
                // Moreover, resolving non-gate clause with another non-gate
                // clause produces a clause subsumed by the other resolvents,
                // which only leaves us with the case of resolving a gate
                // clause with a non-gate clause! See the SatElite paper for
                // more details.
                if (gateClauses != null) {
                    val isPosClauseGate = gateClauses.any { it === posClause }
                    val isNegClauseGate = gateClauses.any { it === negClause }
                    if (isPosClauseGate == isNegClauseGate) continue
                }

                // We perform the resolution here.
                val resolvent = resolve(posClause, negClause, pivot)

                // If it is tautological, we skip it.
                if (resolvent == null) {
                    bveStats.tautologicalResolvents++
                    continue
                }

                // If it is too big, we can't eliminate the variable. This limit
                // is added to prevent the algorithm from generating resolvents
                // of exponential size and running out of memory.
                if (resolvent.size > bveConfig.resolventSizeLimit) {
                    bveStats.resolventsTooBig++
                    return null
                }

                resolventsToAdd.add(resolvent)

                // We also limit the number of resolvents we add per elimination
                // to prevent the algorithm from generating too many clauses.
                if (resolventsToAdd.size > bound) return null
            }
        }

        // Finally, we eliminate the variable. Notice that the removal of the
        // learnts with the eliminated variable is done outside the main loop of
        // boundedVariableElimination, and performed later. We use the
        // specialized propagate procedure to overcome that.
        assignment.markInactive(pivot)
        bveStats.eliminatedVariables++

        // We add resolvents to the database, removing falsified literals from
        // them.
        for (resolvent in resolventsToAdd) {
            bveStats.resolventsAdded++
            bveAttachShrunkClause(state, resolvent)?.let { return it }
            removeSubsumedBy(state, resolvent)?.let { return it }
        }

        // Finally, we remove old clauses containing the variable.
        for (clause in posOccurrences) {
            if (clause.deleted) continue
            bveMarkDeleted(state, clause)
            bveStats.clausesResolved++
            reconstructionStack.push(clause, pivot.posLit)
        }

        for (clause in negOccurrences) {
            if (clause.deleted) continue
            bveMarkDeleted(state, clause)
            bveStats.clausesResolved++
            reconstructionStack.push(clause, pivot.negLit)
        }

        return null
    }

    /**
     * As mentioned in the SatElite paper, when clauses form a gate circuit, we
     * don't need to add every resolvent. This function looks for OR-gates,
     * which is the gate in form `x = (a1 | a2 | ... | aK)`. This is equivalent
     * to `-x = (-a1 & -a2 & ... & -aK)` so this function also discovers
     * AND-gates when used on the negation of the pivot literal. Note that this
     * function also discovers equivalences, that is, the gates of the form
     * `x = a`.
     *
     * In clausal form, this gate is represented as a big clause, the negation
     * of `x` and all the `a1, a2, ..., aK` literals, and a bunch of binary
     * clauses between `x` and negations of `a1, a2, ..., aK` literals. This
     * function returns such clauses. `x` is [pivot].
     */
    private fun findOrGates(
        state: EliminationState,
        pivot: Lit,
    ): List<Clause>? {
        val posOccurrences = state.occurrences[pivot]
        val negOccurrences = state.occurrences[pivot.neg]
        var foundAny = false

        val gateClauses = mutableListOf<Clause>()

        // We first mark all literals which occur in binary clauses with the
        // positive pivot.
        for (i in 0 until posOccurrences.size) {
            val clause = posOccurrences[i]
            if (clause.deleted) continue
            if (clause.size != 2) continue
            val other = Lit(clause[0].inner xor clause[1].inner xor pivot.inner)
            // We use the index of the clause + 1 as the mark, because we need
            // to restore the clause from the mark later. Of course, we can't
            // use 0 as the mark.
            state.gateMarks[other.neg] = i + 1
        }

        // We then look for the "big clause"
        for (i in 0 until negOccurrences.size) {
            val clause = negOccurrences[i]
            if (clause.deleted) continue

            // If all the literals in the clause, except the negation of the
            // pivot, are marked, then we found that clause.
            if (clause.lits.all { state.gateMarks[it] != 0 || it == pivot.neg }) {
                foundAny = true
                gateClauses.add(clause)

                for (lit in clause.lits) {
                    if (lit != pivot.neg) {
                        gateClauses.add(posOccurrences[state.gateMarks[lit] - 1])
                    }
                }

                break
            }
        }

        // We then must reset the marks.
        for (i in 0 until posOccurrences.size) {
            val clause = posOccurrences[i]
            if (clause.deleted) continue
            if (clause.size != 2) continue
            val other = Lit(clause[0].inner xor clause[1].inner xor pivot.inner)
            state.gateMarks[other.neg] = 0
        }

        if (!foundAny) return null
        return gateClauses
    }

    /*
     * Given a [clause], this function removes all the clauses which are
     * subsumed by it, as well as strengthens the clause by self-subsumption.
     *
     * During the [boundedVariableElimination] procedure, we produce a lot of
     * resolvents, some of which are subsumed by other clauses. This happens a
     * lot and removing such clauses is crucial for the performance of the BVE.
     *
     * Moreover, sometimes a clause can be "almost subsumed" by another clause,
     * that is, it would have been subsumed if not the one literal in the wrong
     * phase. Consider two clauses, `x or A` and `-x or B`, and assume `A`
     * subsumes `B`. Then, by resolving the two clauses, we get `A or B`, which
     * is equivalent to `B`. In that case, we say that `-x or B` is strengthened
     * by self-subsumption using `x or A`.
     *
     * Note that strengthening can cause a clause to become a unit, in which
     * case we must propagate it, and in case of a conflict, return UNSAT.
     */
    private fun removeSubsumedBy(state: EliminationState, clause: Clause): SolveResult? {
        // This function uses multiple heuristics to speed up the process.

        // First of all, we only consider clauses which are occurrences of the
        // least occurring literal. This may cause us to miss strengthening, but
        // the performance gain is worth it.
        val leastOccurrenceLit = clause.lits.minBy { state.occurrences[it].size }

        // We mark all the literals in the clause.
        for (lit in clause.lits) state.subsumptionMarks[lit] = true

        // And iterate over all the clauses which contain the least occurring
        // literal.
        val initialSize = state.occurrences[leastOccurrenceLit].size
        outer@ for (i in 0 until initialSize) {
            val otherClause = state.occurrences[leastOccurrenceLit][i]

            if (otherClause === clause) continue
            if (otherClause.deleted) continue

            // We also only consider clauses which are at least as big as the
            // clause we are trying to subsume.
            if (otherClause.size < clause.size) continue

            // This is the mismatch used for strengthening.
            var mismatch: Lit? = null

            for (lit in otherClause.lits) {
                if (!state.subsumptionMarks[lit]) {
                    // If mismatch is already set, then we have two literals
                    // which are not in the clause we are trying to subsume.
                    // If mismatch does not occur in the clause in the
                    // other phase, then we can't strengthen.
                    if (mismatch != null || !state.subsumptionMarks[lit.neg]) {
                        continue@outer
                    }

                    mismatch = lit
                }
            }

            if (mismatch == null) {
                // Finally, if there are no mismatches, then the clause is simply
                // subsumed.
                bveStats.clausesSubsumed++
                bveMarkDeleted(state, otherClause)
            } else if (state.subsumptionMarks[mismatch.neg]) {
                // Otherwise, we can strengthen the clause.
                val strengthenedClause = Clause(DenseLitVec(otherClause.lits.filter { it != mismatch }))
                // Note that we don't reset marks here with a hope that those
                // will never be used after UNSAT anyway.
                bveStats.clausesStrengthened++
                bveAttachShrunkClause(state, strengthenedClause)?.let { return it }
                bveMarkDeleted(state, otherClause)
            }
        }

        // We then must reset the marks.
        for (lit in clause.lits) state.subsumptionMarks[lit] = false

        return null
    }

    /**
     * The resolution procedure used by the [boundedVariableElimination].
     *
     * Given two clauses, [clause1] and [clause2], which
     * contain the same variable in opposite phases, this function returns the
     * resolvent of the two clauses, or null if the resolvent is a tautology,
     * or satisfied.
     *
     * It may return a unit clause, but it should never return an empty clause.
     */
    private fun resolve(clause1: Clause, clause2: Clause, pivot: Var): Clause? {
        require(!clause1.learnt && !clause2.learnt)
        require(!clause1.deleted && !clause2.deleted)
        val resolvent = DenseLitVec.emptyOfCapacity(clause1.size + clause2.size - 2)

        for (lit in clause1.lits) {
            if (lit.variable == pivot) continue
            val value = assignment.value(lit)
            if (value == LBool.FALSE) continue
            if (value == LBool.TRUE) return null

            resolvent.add(lit)
        }

        for (lit in clause2.lits) {
            if (lit.variable == pivot) continue
            val value = assignment.value(lit)
            if (value == LBool.FALSE) continue
            if (value == LBool.TRUE) return null

            resolvent.add(lit)
        }

        if (sortDedupAndCheckComplimentary(resolvent)) {
            return null
        }

        return Clause(DenseLitVec(resolvent))
    }

    /**
     * This is the custom propagation procedure used by the
     * [boundedVariableElimination]. It is similar to the regular propagation
     * except it ignores clauses which contain non-active literals. This is
     * because when propagating, we may accidentally use a learnt clause which
     * will cause learning non-active variables, and since removing learnt
     * clauses is not cheap, we cannot afford to do that every elimination.
     *
     * It also removes such clauses from the watch list, since they are useless
     * anyway.
     */
    private fun bvePropagate(): Clause? {
        require(ok && assignment.decisionLevel == 0)

        var conflict: Clause? = null

        while (assignment.qhead < assignment.trail.size) {
            val lit = assignment.dequeue()!!

            check(value(lit) == LBool.TRUE)

            val clausesToKeep = mutableListOf<Clause>()
            val possiblyBrokenClauses = watchers[lit.neg]

            for (i in 0 until possiblyBrokenClauses.size) {
                val clause = possiblyBrokenClauses[i]
                if (clause.deleted) continue

                clausesToKeep.add(clause)

                if (conflict != null) continue

                // This is where we ignore clauses with non-active literals.
                if (clause.lits.any { !assignment.isActive(it) }) {
                    clausesToKeep.removeLast()
                    continue
                }

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
                    assignment.uncheckedEnqueue(clause[0], clause)
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
     * Add [clause] into the database and attach watchers for it.
     */
    fun attachClause(clause: Clause) {
        require(clause.size >= 2) { clause }
        check(ok)
        db.add(clause)
        watchers[clause[0]].add(clause)
        watchers[clause[1]].add(clause)
        if (!clause.fromInput) dratBuilder.addClause(clause)
    }

    /**
     * Mark [clause] for deletion. During [ClauseDatabase.reduceIfNeeded]
     * it will be removed from the database, and the watchers
     * will be detached (the latter may also happen in [propagate]).
     */
    fun markDeleted(clause: Clause) {
        check(ok)
        clause.deleted = true
        if (!clause.fromInput) dratBuilder.deleteClause(clause)
    }

    /**
     * Propagate all the literals in the trail that are not yet propagated. If
     * a conflict is found, return the clause that caused it.
     *
     * This function takes every literal on the trail that has not been
     * propagated (that is, all literals for which `qhead <= index < trail.size`)
     * and applies the unit propagation rule to it, possibly leading to deducing
     * new literals. The new literals are added to the trail, and the process is
     * repeated until no more literals can be propagated, or a conflict is found.
     *
     * @return the conflict clause if a conflict is found, or `null` if no
     * conflict occurs.
     */
    private fun propagate(): Clause? {
        // check(ok)

        var conflict: Clause? = null

        while (assignment.qhead < assignment.trail.size) {
            val lit = assignment.dequeue()!!

            // check(value(lit) == LBool.TRUE)

            // Checking the list of clauses watching the negation of the literal.
            // In those clauses, both of the watched literals might be false,
            // which can either lead to a conflict (all literals in clause are false),
            // unit propagation (only one unassigned literal left), or invalidation
            // of the watchers (both watchers are false, but there are others)
            var j = 0
            val possiblyBrokenClauses = watchers[lit.neg]

            for (i in 0 until possiblyBrokenClauses.size) {
                val clause = possiblyBrokenClauses[i]
                if (clause.deleted) continue

                possiblyBrokenClauses[j++] = clause

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
                    j--
                }
            }

            watchers[lit.neg].retainFirst(j)

            if (conflict != null) break
        }

        return conflict
    }

    /**
     * Analyzes the conflict clause returned by [propagate]
     * and returns a new clause that can be learned.
     *
     * This function analyzes the conflict clause by walking back on
     * the trail and replacing all but one literal in the conflict clause
     * by their reasons. The learned clause is then simplified by removing
     * redundant literals.
     *
     * @param conflict the conflict clause.
     * @return the learned clause.
     */
    private fun analyzeConflict(conflict: Clause): Clause {
        // We analyze conflict by walking back on implication graph,
        // starting with the literals in the conflict clause.
        // (Technically, the literals of the conflict are added on
        // the first iteration, and we start with nothing, but it
        // is easier to think about it this way.)
        // For every literal from the last decision level, we replace
        // that literal with its reason, until only one literal from
        // the last decision level is left. This literal is called
        // the "Unique Implication Point" (UIP).

        // Keep track of the variables we have "seen" during the analysis
        // (see implementation below for details)
        val seen = BooleanArray(assignment.numberOfVariables)

        // The list of literals of the learnt
        val learntLits = mutableListOf<Lit>()

        // How many literals from the last decision level we have seen,
        // but not yet replaced with their reasons?
        var lastLevelLitCount = 0

        // The next clause we are about to add to the cut
        var clauseToAdd = conflict

        // The index of the last literal from the last decision level,
        // not yet replaced with its reason
        var index = assignment.trail.lastIndex

        while (true) {
            db.clauseBumpActivity(clauseToAdd)

            for (lit in clauseToAdd.lits) {
                if (seen[lit.variable]) continue

                // Mark all the variables in the clause as seen, if not already
                seen[lit.variable] = true

                if (assignment.level(lit) == assignment.decisionLevel) {
                    // If the literal is from the last decision level,
                    // just count it here: we will replace it with its reason later
                    // because every literal (except lit) in its reason
                    // is before lit on the trail, and lit is already seen
                    lastLevelLitCount++
                } else {
                    // Literals from the previous decision levels are added to the learnt
                    learntLits.add(lit)
                }
            }

            // After we added all the literals from the clause to the learnt,
            // we find the next literal from the last decision level to replace
            // with its reason. We do this by walking back on the trail.
            while (!seen[assignment.trail[index].variable]) index--

            // If only one literal from the last decision level is left,
            // we have found the UIP.
            if (lastLevelLitCount == 1) break

            // Otherwise, it must be replaced with its reason on the next iteration
            lastLevelLitCount--
            val lastLevelVar = assignment.trail[index].variable
            index--
            clauseToAdd = assignment.reason(lastLevelVar)!!
        }

        // Add the UIP to the learnt in the correct phase.
        val uip = assignment.trail[index]
        learntLits.add(uip.neg)

        // Some literals in the learnt can follow from their reasons,
        // included in the learnt. We remove them here.
        learntLits.removeAll { lit ->
            val reason = assignment.reason(lit.variable) ?: return@removeAll false
            // lit is redundant if all the literals in its reason are already seen
            // (and, therefore, included in the learnt or follow from it)
            val redundant = reason.lits.all { it == lit.neg || seen[it.variable] }
            redundant
        }

        // Sort the learnt by the decision level of the literals
        // (highest level first), making sure UIP is the first literal,
        // and the second max level literal is the second.
        // This is required to have watchers work correctly during the
        // next propagate (only the first two literals are watched).
        learntLits.sortByDescending { assignment.level(it) }

        val learnt = Clause(DenseLitVec(learntLits), learnt = true)

        // Sorting also helps to calculate the LBD of the learnt
        // without additional memory.
        learnt.lbd = 1
        for (i in 0 until learnt.size - 1) {
            if (assignment.level(learnt[i]) != assignment.level(learnt[i + 1])) {
                learnt.lbd++
            }
        }

        return learnt
    }
}

/**
 * Takes a list of literals, sorts it and removes duplicates in place,
 * then checks if the list contains a literal and its negation
 * and returns true if so.
 */
private fun sortDedupAndCheckComplimentary(lits: DenseLitVec): Boolean {
    lits.sort()

    var i = 0
    for (j in 1 until lits.size) {
        if (lits[i] == lits[j].neg) return true
        if (lits[i] != lits[j]) {
            i++
            lits[i] = lits[j]
        }
    }

    lits.retainFirst(i + 1)

    return false
}
