import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.LBool
import org.kosat.SolveResult
import org.kosat.VSIDS
import org.kosat.Var
import org.kosat.cnf.CNF
import org.kosat.get
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
        inner.variableSelector.build(
            inner.db.clauses +
                Clause(MutableList(inner.assignment.numberOfVariables) { Var(it).posLit })
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
                inner.variableSelector.build(
                    inner.db.clauses +
                        Clause(MutableList(inner.assignment.numberOfVariables) { Var(it).posLit })
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
        val propagatedRequirements = listOf(
            Requirement(ok, "Solver is not in UNSAT state", obvious = true),
            Requirement(conflict == null, "There is no conflict"),
            Requirement(!leftToPropagate, "All literals are propagated")
        )

        val conflictLitsFromLastLevel = conflict?.lits?.count {
            assignment.level(it) == assignment.decisionLevel
        }

        when (command) {
            is SolverCommand.Solve -> propagatedRequirements

            is SolverCommand.Search -> propagatedRequirements

            is SolverCommand.Propagate -> listOf(
                Requirement(ok, "Solver is not in UNSAT state", obvious = true),
                Requirement(conflict == null, "There is no conflict"),
                Requirement(leftToPropagate, "There are literals not yet propagated")
            )

            is SolverCommand.PropagateOne -> listOf(
                Requirement(ok, "Solver is not in UNSAT state", obvious = true),
                Requirement(conflict == null, "There is no conflict"),
                Requirement(leftToPropagate, "There are literals not yet propagated")
            )

            is SolverCommand.PropagateUpTo -> listOf(
                Requirement(ok, "Solver is not in UNSAT state", obvious = true),
                Requirement(conflict == null, "There is no conflict"),
                Requirement(leftToPropagate, "There are literals not yet propagated"),
                Requirement(
                    command.trailIndex >= assignment.qhead,
                    "Trail index is not before the current queue head",
                    obvious = true,
                ),
                Requirement(
                    command.trailIndex < assignment.trail.size,
                    "Trail index is not after the last literal on the trail",
                    obvious = true,
                ),
            )

            is SolverCommand.AnalyzeConflict -> listOf(
                Requirement(ok, "Solver is not in UNSAT state", obvious = true),
                Requirement(conflict != null, "There is a conflict"),
                Requirement(
                    conflictLitsFromLastLevel?.let { it > 1 } ?: false,
                    "There is more than one literal from the current decision level in the conflict",
                )
            )

            is SolverCommand.AnalyzeOne -> listOf(
                Requirement(ok, "Solver is not in UNSAT state", obvious = true),
                Requirement(conflict != null, "There is a conflict"),
                Requirement(
                    conflictLitsFromLastLevel?.let { it > 1 } ?: false,
                    "There is more than one literal from the current decision level in the conflict",
                )
            )

            is SolverCommand.AnalysisMinimize -> listOf(
                Requirement(ok, "Solver is not in UNSAT state", obvious = true),
                Requirement(conflict != null, "There is a conflict"),
                Requirement(
                    conflictLitsFromLastLevel?.let { it == 1 } ?: false,
                    "There is exactly one literal from the current decision level in the conflict",
                ),
                Requirement(
                    conflict != null && inner.checkIfLearntCanBeMinimized(conflict!!),
                    "There is a literal which reason is a subset of the conflict",
                ),
            )

            is SolverCommand.LearnAndBacktrack -> listOf(
                Requirement(ok, "Solver is not in UNSAT state", obvious = true),
                Requirement(conflict != null, "There is a conflict"),
                Requirement(
                    conflictLitsFromLastLevel?.let { it == 1 } ?: false,
                    "There is exactly one literal from the current decision level in the conflict",
                )
            )

            is SolverCommand.Backtrack -> listOf(
                Requirement(ok, "Solver is not in UNSAT state", obvious = true),
                Requirement(
                    command.level in 0 until assignment.decisionLevel,
                    "Backtrack level is between 0 and the current decision level (exclusive)",
                    obvious = true,
                ),
            )

            is SolverCommand.Enqueue -> listOf(
                Requirement(propagated, "All literals are propagated"),
                Requirement(
                    command.lit.variable.index in 0 until assignment.numberOfVariables,
                    "Literal is in the problem",
                    obvious = true,
                ),
                Requirement(
                    assignment.isActive(command.lit),
                    "Literal is active",
                ),
                Requirement(
                    assignment.isActive(command.lit) &&
                        assignment.value(command.lit) == LBool.UNDEF,
                    "Literal is not assigned",
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
                    val activities = (variableSelector as VSIDS).activity
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

        val lit = assignment.dequeue()!!

        check(value(lit) == LBool.TRUE)
        val clausesToKeep = mutableListOf<Clause>()
        val possiblyBrokenClauses = watchers[lit.neg]

        for (clause in possiblyBrokenClauses) {
            if (clause.deleted) continue

            clausesToKeep.add(clause)

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
                clausesToKeep.removeLast()
            }
        }

        watchers[lit.neg] = clausesToKeep

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
        val orderedLits = lits.toSet().sortedByDescending {
            assignment.trailIndex(it.variable)
        }.toMutableList()
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
        variableSelector.update(learnt)
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