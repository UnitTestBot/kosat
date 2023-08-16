import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.LBool
import org.kosat.SolveResult
import org.kosat.cnf.CNF
import org.kosat.get
import org.kosat.set
import org.kosat.swap
import web.prompts.alert


class CdclState(initialProblem: CNF) {
    var problem: CNF = initialProblem
    var inner: CDCL = CDCL(initialProblem)
    val history: MutableList<SolverCommand> = mutableListOf()
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

    private val propagated
        get() = inner.ok
            && conflict == null
            && inner.assignment.qhead == inner.assignment.trail.size

    fun execute(command: SolverCommand) {
        history.add(command)
        check(canExecute(command))
        when (command) {
            is SolverCommand.Recreate -> {
                problem = command.cnf
                inner = CDCL(problem)
                conflict = null
                history.clear()
                // FIXME: workaround
                inner.variableSelector.build(inner.db.clauses)
            }

            is SolverCommand.Undo -> {
                val historyToRepeat = history.dropLast(2)
                // FIXME: Too many hacks
                execute(SolverCommand.Recreate(problem))
                for (commandToRepeat in historyToRepeat) {
                    execute(commandToRepeat)
                }
            }

            is SolverCommand.Solve -> inner.solve()

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
                conflict = inner.analyzeConflict(conflict!!)
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

            is SolverCommand.Restart -> {
                inner.backtrack(0)
                conflict = null
            }

            is SolverCommand.Enqueue -> {
                inner.assignment.newDecisionLevel()
                inner.assignment.uncheckedEnqueue(command.lit, null)
            }
        }
    }

    fun canExecute(command: SolverCommand): Boolean {
        inner.run {
            val conflictLitsFromLastLevel = conflict?.lits?.count {
                assignment.level(it) == assignment.decisionLevel
            } ?: 0

            return when (command) {
                is SolverCommand.Recreate -> true
                is SolverCommand.Undo -> history.isNotEmpty()
                is SolverCommand.Solve -> propagated
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

                is SolverCommand.AnalyzeConflict -> ok && conflict != null
                is SolverCommand.AnalyzeOne ->
                    ok
                        && conflict != null
                        && conflictLitsFromLastLevel > 1

                is SolverCommand.AnalysisMinimize ->
                    ok
                        && conflict != null
                        && conflictLitsFromLastLevel == 1

                is SolverCommand.LearnAndBacktrack ->
                    ok
                        && conflict != null
                        && conflictLitsFromLastLevel == 1

                is SolverCommand.Backtrack -> ok && command.level in 0 until assignment.decisionLevel
                is SolverCommand.Restart -> ok && assignment.decisionLevel > 0
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
}