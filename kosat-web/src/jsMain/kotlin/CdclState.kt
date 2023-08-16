import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.LBool
import org.kosat.SolveResult
import org.kosat.cnf.CNF
import org.kosat.get
import org.kosat.set
import org.kosat.swap

class CdclState(initialProblem: CNF) {
    var inner: CDCL = CDCL(initialProblem)
    var conflict: Clause? = null

    val result: SolveResult
        get() {
            return when {
                !inner.ok -> SolveResult.UNSAT
                !propagated -> SolveResult.UNKNOWN
                inner.assignment.trail.size == inner.assignment.numberOfActiveVariables ->
                    inner.finishWithSatIfAssumptionsOk()

                else -> SolveResult.UNKNOWN
            }
        }

    init {
        // FIXME: workaround
        inner.variableSelector.build(inner.db.clauses)
    }

    private val propagated
        get() = inner.ok
            && conflict == null
            && inner.assignment.qhead == inner.assignment.trail.size

    fun execute(command: SolverCommand) {
        check(canExecute(command))
        when (command) {
            is SolverCommand.Solve -> inner.solve()

            is SolverCommand.Search -> inner.search()

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

            is SolverCommand.Search -> propagatedRequirements + listOf(
                Requirement(
                    assignment.decisionLevel == 0,
                    "Solver is at decision level 0"
                ),
            )

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

    fun canExecute(command: SolverCommand): Boolean {
        inner.run {
            val conflictLitsFromLastLevel = conflict?.lits?.count {
                assignment.level(it) == assignment.decisionLevel
            } ?: 0

            return when (command) {
                is SolverCommand.Solve ->
                    propagated

                is SolverCommand.Search ->
                    propagated
                        && assignment.decisionLevel == 0

                is SolverCommand.Propagate ->
                    ok
                        && conflict == null
                        && inner.assignment.qhead < inner.assignment.trail.size

                is SolverCommand.PropagateOne ->
                    ok
                        && conflict == null
                        && inner.assignment.qhead < inner.assignment.trail.size

                is SolverCommand.PropagateUpTo ->
                    ok
                        && conflict == null
                        && command.trailIndex >= inner.assignment.qhead
                        && command.trailIndex < inner.assignment.trail.size

                is SolverCommand.AnalyzeConflict ->
                    ok
                        && conflict != null
                        && conflictLitsFromLastLevel > 1

                is SolverCommand.AnalyzeOne ->
                    ok
                        && conflict != null
                        && conflictLitsFromLastLevel > 1

                is SolverCommand.AnalysisMinimize ->
                    ok
                        && conflict != null
                        && conflictLitsFromLastLevel == 1
                        && inner.checkIfLearntCanBeMinimized(conflict!!)

                is SolverCommand.LearnAndBacktrack ->
                    ok
                        && conflict != null
                        && conflictLitsFromLastLevel == 1

                is SolverCommand.Backtrack ->
                    ok
                        && command.level in 0 until assignment.decisionLevel

                is SolverCommand.Enqueue ->
                    propagated
                        && command.lit.variable.index in 0 until assignment.numberOfVariables
                        && assignment.isActive(command.lit)
                        && assignment.value(command.lit) == LBool.UNDEF
            }
        }
    }

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

    private fun CDCL.analyzeOne(conflict: Clause): Clause {
        val lits = conflict.lits.toMutableList()
        lits.sortBy { assignment.trailIndex(it.variable) }
        val replaceWithReason = lits.removeLast()
        for (lit in assignment.reason(replaceWithReason.variable)!!.lits) {
            if (lit == replaceWithReason.neg) continue
            lits.add(lit)
        }
        return Clause(lits.toSet().toMutableList(), learnt = true)
    }

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

    private fun CDCL.checkIfLearntCanBeMinimized(learnt: Clause): Boolean {
        return learnt.lits.any { lit ->
            val reason = assignment.reason(lit.variable) ?: return@any false
            reason.lits.all { reasonLit ->
                reasonLit == lit.neg || reasonLit in learnt.lits
            }
        }
    }
}