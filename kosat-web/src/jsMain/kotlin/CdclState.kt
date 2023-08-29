import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.LBool
import org.kosat.LitVec
import org.kosat.SolveResult
import org.kosat.Var
import org.kosat.cnf.CNF
import org.kosat.get
import org.kosat.retainFirst
import org.kosat.set
import org.kosat.swap

/**
 * To allow user to interact with the solver in a granular way, we have to
 * extend the state of the solver with additional information on top level,
 * which is usually contained in the local variables of the solver, and
 * unavailable from the outside.
 *
 * This class is a wrapper around the CDCL solver, which exposes the [conflict]
 * variable, allowing to analyze the conflict clause step by step.
 *
 * The outside mutations are performed through the [execute] method, which takes
 * a [SolverCommand] and performs the corresponding action on the solver. To
 * check if the command is valid, use [requirementsFor]. It returns a list of
 * [Requirement]s, which are either fulfilled or not. If they are not fulfilled,
 * the command is not valid. If they are fulfilled, the command is valid, and
 * **it must cause instance to irreversibly change its state.** See
 * [CdclWrapper.commandsToRunEagerly] and [requirementsFor] for more information on this.
 *
 * This class also provides functionality to "guess" the next action of the
 * solver, which is used to highlight the next action in the UI. It is
 * relatively simple and does not take a lot of things into account, but it is
 * good enough for the simplest CDCL.
 *
 * Note that this class is mutable, and therefore cannot be used in React as is.
 * It is wrapped in [CdclWrapper] to provide immutability.
 *
 * @see SolverCommand
 * @see CdclWrapper
 */
class CdclState(initialProblem: CNF) {
    /**
     * The wrapped solver.
     */
    var inner: CDCL = CDCL(initialProblem)

    /**
     * The last conflict clause occurred, if there is one. We use it to try to
     * emulate the conflict analysis step by step. At first, it is the conflict
     * returned by [CDCL.propagate]. After either [CDCL.analyzeConflict] or
     * possibly multiple calls to [CDCL.analyzeOne] it turns into a learnt
     * clause, which sometimes can be minimized with
     * [SolverCommand.AnalysisMinimize]. After that, it is used to learn a new
     * clause with [SolverCommand.LearnAndBacktrack], and we reset to null.
     */
    var conflict: Clause? = null

    /**
     * The result of the solver as it is known at the moment.
     */
    val result: SolveResult
        get() {
            return when {
                !inner.ok -> SolveResult.UNSAT
                !propagated -> SolveResult.UNKNOWN
                inner.assignment.trail.size == inner.assignment.numberOfActiveVariables ->
                    SolveResult.SAT

                else -> SolveResult.UNKNOWN
            }
        }

    init {
        // FIXME: workaround
        inner.vsids.build(
            inner.assignment.numberOfVariables,
            inner.db.clauses,
        )
    }

    /**
     * Whether all literals are propagated, and there is no conflict.
     */
    private val propagated
        get() = inner.ok
            && conflict == null
            && inner.assignment.qhead == inner.assignment.trail.size

    /**
     * Get the satisfying assignment from the solver. This is only possible if
     * the solver is in SAT state.
     */
    fun getModel(): List<Boolean> {
        check(result == SolveResult.SAT)
        return inner.getModel()
    }

    /**
     * Executes the given command on the solver, mutating it. The command must
     * be valid, i.e. all requirements must be fulfilled.
     *
     * @see requirementsFor
     */
    fun execute(command: SolverCommand) {
        check(requirementsFor(command).all { it.fulfilled })
        when (command) {
            is SolverCommand.Solve -> inner.solve()

            is SolverCommand.Search -> {
                // FIXME: workaround, same as above
                inner.vsids.build(
                    inner.assignment.numberOfVariables,
                    inner.db.clauses,
                )
                inner.search()
            }

            is SolverCommand.Propagate -> {
                conflict = inner.propagate()
                if (conflict != null && inner.assignment.decisionLevel == 0) {
                    inner.finishWithUnsat()
                }
            }

            is SolverCommand.PropagateOne -> {
                conflict = inner.propagateOne()
                if (conflict != null && inner.assignment.decisionLevel == 0) {
                    inner.finishWithUnsat()
                }
            }

            is SolverCommand.PropagateUpTo -> {
                while (inner.assignment.qhead <= command.trailIndex) {
                    conflict = inner.propagateOne()
                    if (conflict != null) {
                        break
                    }
                }

                if (conflict != null && inner.assignment.decisionLevel == 0) {
                    inner.finishWithUnsat()
                }
            }

            is SolverCommand.AnalyzeConflict -> {
                conflict = inner.analyzeConflict(conflict!!, minimize = false)
            }

            is SolverCommand.AnalyzeOne -> {
                conflict = inner.analyzeOne(conflict!!)
            }

            is SolverCommand.AnalysisMinimize -> {
                conflict = inner.analyzeConflict(conflict!!)
            }

            is SolverCommand.LearnAndBacktrack -> {
                val learnt = conflict!!
                conflict = null
                inner.learnAndBacktrack(learnt)
            }

            is SolverCommand.Backtrack -> {
                inner.backtrack(command.level)
                conflict = null
            }

            is SolverCommand.Enqueue -> {
                inner.assignment.newDecisionLevel()
                inner.assignment.uncheckedEnqueue(command.lit, null)
            }
        }
    }

    /**
     * This method must return a list of [Requirement]s for the given command.
     * All returned requirements are fulfilled if and only if the command is
     * valid, can be performed on the current state of the solver, **and will
     * cause some changes in the solver.**
     *
     * **Transitive closure of applying commands with fulfilled requirements
     * must lead to an eventual termination.** Note that this is already true
     * for a normal and functioning CDCL implementation. See
     * [CdclWrapper.commandsToRunEagerly] for more information on this.
     *
     * @return A list of [Requirement]s for the given command. If all of them
     *         are fulfilled, the command is valid.
     */
    fun requirementsFor(command: SolverCommand): List<Requirement> = inner.run {
        val leftToPropagate = assignment.qhead < assignment.trail.size

        val notUnsat = Requirement(
            ok,
            "Solver must not be in UNSAT state",
            obvious = true,
        )

        val propagatedRequirements = listOf(
            notUnsat,
            Requirement(conflict == null, "There must be no conflict"),
            Requirement(!leftToPropagate, "All literals must be propagated")
        )

        val conflictLitsFromLastLevel = conflict?.lits?.count {
            assignment.level(it) == assignment.decisionLevel
        }

        when (command) {
            is SolverCommand.Solve -> propagatedRequirements

            is SolverCommand.Search -> propagatedRequirements

            is SolverCommand.Propagate -> listOf(
                notUnsat,
                Requirement(conflict == null, "There must be conflict"),
                Requirement(
                    leftToPropagate,
                    "There must be literals left to propagate",
                    wontCauseEffectIfIgnored = true
                )
            )

            is SolverCommand.PropagateOne -> listOf(
                notUnsat,
                Requirement(conflict == null, "There must be no conflict"),
                Requirement(
                    leftToPropagate,
                    "There must be literals left to propagate",
                    wontCauseEffectIfIgnored = true
                )
            )

            is SolverCommand.PropagateUpTo -> listOf(
                notUnsat,
                Requirement(conflict == null, "There must be no conflict"),
                Requirement(leftToPropagate, "There must be literals left to propagate"),
                Requirement(
                    command.trailIndex >= assignment.qhead,
                    "Trail index must be at least the current queue head",
                    obvious = true,
                ),
                Requirement(
                    command.trailIndex < assignment.trail.size,
                    "Trail index must be less than the trail size",
                    obvious = true,
                ),
            )

            is SolverCommand.AnalyzeConflict -> listOf(
                notUnsat,
                Requirement(conflict != null, "There must be a conflict"),
                Requirement(
                    conflictLitsFromLastLevel?.let { it > 1 } ?: false,
                    "There must be more than one literal from " +
                        "the current decision level in the conflicting literals list",
                )
            )

            is SolverCommand.AnalyzeOne -> listOf(
                notUnsat,
                Requirement(conflict != null, "There must be a conflict"),
                Requirement(
                    conflictLitsFromLastLevel?.let { it > 1 } ?: false,
                    "There must be more than one literal from " +
                        "the current decision level in the conflicting literals list",
                )
            )

            is SolverCommand.AnalysisMinimize -> listOf(
                notUnsat,
                Requirement(conflict != null, "There must be a conflict"),
                Requirement(
                    conflictLitsFromLastLevel?.let { it == 1 } ?: false,
                    "There must be exactly one literal from " +
                        "the current decision level in the learnt",
                ),
                Requirement(
                    conflict != null && inner.checkIfLearntCanBeMinimized(conflict!!),
                    "There must be a literal which reason is a subset of literals in the learnt",
                    wontCauseEffectIfIgnored = true
                ),
            )

            is SolverCommand.LearnAndBacktrack -> listOf(
                notUnsat,
                Requirement(conflict != null, "There must be a conflict"),
                Requirement(
                    conflictLitsFromLastLevel?.let { it == 1 } ?: false,
                    "There must be exactly one literal from " +
                        "the current decision level in the learnt",
                )
            )

            is SolverCommand.Backtrack -> listOf(
                notUnsat,
                Requirement(
                    command.level in 0 until assignment.decisionLevel,
                    "Backtrack level must not be the current decision level",
                    obvious = true,
                    wontCauseEffectIfIgnored = true,
                ),
            )

            is SolverCommand.Enqueue -> propagatedRequirements + listOf(
                Requirement(
                    command.lit.variable.index in 0 until assignment.numberOfVariables,
                    "Literal must be in the problem",
                    obvious = true,
                ),
                Requirement(
                    assignment.isActive(command.lit),
                    "Literal must be active",
                    obvious = true
                ),
                Requirement(
                    assignment.isActive(command.lit) &&
                        assignment.value(command.lit) == LBool.UNDEF,
                    "Literal must not be assigned",
                ),
            )
        }
    }

    /**
     * Guesses the next action of the solver. This is used to highlight the next
     * action in the UI.
     *
     * @return The next action of the solver, or null if no action is possible.
     */
    fun guessNextSolverAction(): SolverCommand? = inner.run {
        val conflictLitsFromLastLevel = conflict?.lits?.count {
            assignment.level(it) == assignment.decisionLevel
        }

        when {
            !ok -> null

            conflict != null
                && conflictLitsFromLastLevel?.let { it > 1 } ?: false ->
                SolverCommand.AnalyzeConflict

            conflict != null
                && conflictLitsFromLastLevel?.let { it == 1 } ?: false
                && checkIfLearntCanBeMinimized(conflict!!) ->
                SolverCommand.AnalysisMinimize

            conflict != null
                && conflictLitsFromLastLevel?.let { it == 1 } ?: false ->
                SolverCommand.LearnAndBacktrack

            conflict != null -> error("Unreachable")

            assignment.qhead < assignment.trail.size ->
                SolverCommand.Propagate

            assignment.qhead == assignment.trail.size
                && assignment.trail.size < assignment.numberOfActiveVariables ->
                SolverCommand.Enqueue(run {
                    val activities = vsids.activity
                    var bestVariable: Var? = null
                    for (i in 0 until assignment.numberOfVariables) {
                        val v = Var(i)
                        if (assignment.isActive(v) && assignment.value(v) == LBool.UNDEF) {
                            if (bestVariable == null || activities[v] > activities[bestVariable]) {
                                bestVariable = v
                            }
                        }
                    }

                    if (polarity[bestVariable!!] == LBool.FALSE) {
                        bestVariable.negLit
                    } else {
                        bestVariable.posLit
                    }
                })

            else -> null
        }
    }

    /**
     * A one iteration of outer loop in [CDCL.propagate]. It is used to perform
     * the propagation step by step in the UI.
     *
     * @return The conflict clause, if there is one.
     */
    private fun CDCL.propagateOne(): Clause? {
        var conflict: Clause? = null

        val lit = assignment.dequeue()

        check(value(lit) == LBool.TRUE)
        var j = 0
        val possiblyBrokenClauses = watchers[lit.neg]

        for (clause in possiblyBrokenClauses) {
            if (clause.deleted) continue

            possiblyBrokenClauses[j++] = clause

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
                assignment.uncheckedEnqueue(clause[0], clause)
            } else {
                watchers[clause[firstNotFalse]].add(clause)
                clause.lits.swap(firstNotFalse, 1)
                j--
            }
        }

        watchers[lit.neg].retainFirst(j)

        return conflict
    }

    /**
     * A one iteration of the analysis loop in [CDCL.analyzeConflict]. It is
     * used to perform the conflict analysis step by step in the UI.
     *
     * @param conflict The conflict clause.
     * @return The conflict clause, if there is one.
     */
    private fun CDCL.analyzeOne(conflict: Clause): Clause {
        val lits = conflict.lits.toMutableList()
        lits.sortBy { assignment.trailIndex(it.variable) }
        val replaceWithReason = lits.removeLast()
        for (lit in assignment.reason(replaceWithReason.variable)!!.lits) {
            if (lit == replaceWithReason.neg) continue
            lits.add(lit)
        }
        val orderedLits = LitVec(lits.toSet().sortedByDescending {
            assignment.trailIndex(it.variable)
        })
        return Clause(orderedLits, learnt = true)
    }

    /**
     * Part of [CDCL.propagateAnalyzeBacktrack] loop. It takes a learnt clause
     * and backtracks to the lowest decision level on which the clause can be
     * satisfied. It does not propagate anything yet.
     */
    private fun CDCL.learnAndBacktrack(learnt: Clause) {
        learnt.lits.sortByDescending { assignment.trailIndex(it.variable) }
        val level = if (learnt.size > 1) assignment.level(learnt[1]) else 0
        backtrack(level)
        if (learnt.size == 1) {
            assignment.uncheckedEnqueue(learnt[0], null)
        } else {
            attachClause(learnt)
            assignment.uncheckedEnqueue(learnt[0], learnt)
            db.clauseBumpActivity(learnt)
        }
        vsids.bump(learnt)
        db.clauseDecayActivity()
    }

    /**
     * Checks if the given learnt clause can be minimized. This is used to
     * generate correct requirements for [SolverCommand.AnalysisMinimize].
     */
    private fun CDCL.checkIfLearntCanBeMinimized(learnt: Clause): Boolean {
        return learnt.lits.any { lit ->
            val reason = assignment.reason(lit.variable) ?: return@any false
            reason.lits.all { reasonLit ->
                reasonLit == lit.neg || reasonLit in learnt.lits
            }
        }
    }
}