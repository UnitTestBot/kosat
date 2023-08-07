import csstype.Display
import csstype.FontFamily
import csstype.FontWeight
import csstype.Margin
import csstype.NamedColor
import csstype.Position
import csstype.TextAlign
import csstype.px
import csstype.rgb
import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.LBool
import org.kosat.Lit
import org.kosat.SolveResult
import org.kosat.Var
import org.kosat.cnf.CNF
import react.FC
import react.Props
import react.css.css
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.textarea
import react.dom.html.ReactHTML.tr
import react.useReducer
import react.useState

sealed interface SolverAction {
    data object Solve : SolverAction
    data object Propagate : SolverAction
    data object Restart : SolverAction
    data class Backtrack(val level: Int) : SolverAction
    data class Learn(val learnt: Clause) : SolverAction
    data class Enqueue(val lit: Lit) : SolverAction
    data object AnalyzeConflict : SolverAction
}

class CdclWrapper(initialProblem: CNF) {
    var problem = initialProblem
        set(value) {
            field = value
            inner = CDCL(value)
            conflict = null
            learnt = null
            history.clear()
            // FIXME: workaround
            inner.variableSelector.build(inner.db.clauses)
        }

    var inner: CDCL = CDCL(problem)
    val history: MutableList<SolverAction> = mutableListOf()
    var conflict: Clause? = null
    var learnt: Clause? = null

    val result: SolveResult get() {
        return when {
            !inner.ok -> SolveResult.UNSAT
            !propagated -> SolveResult.UNKNOWN
            inner.assignment.trail.size == inner.assignment.numberOfActiveVariables -> inner.finishWithSatIfAssumptionsOk()
            else -> SolveResult.UNKNOWN
        }
    }

    private val propagated get() = inner.ok
        && conflict == null
        && learnt == null
        && inner.assignment.qhead == inner.assignment.trail.size

    fun execute(action: SolverAction) {
        history.add(action)
        check(canExecute(action))
        when (action) {
            is SolverAction.Solve -> inner.solve()
            is SolverAction.Propagate -> {
                conflict = inner.propagate()
                if (conflict != null && inner.assignment.decisionLevel == 0) {
                    inner.finishWithUnsat()
                }
            }
            is SolverAction.AnalyzeConflict -> learnt = inner.analyzeConflict(conflict!!)
            is SolverAction.Backtrack -> {
                inner.backtrack(action.level)
                learnt = null
                conflict = null
            }
            is SolverAction.Restart -> {
                inner.backtrack(0)
                learnt = null
                conflict = null
            }
            is SolverAction.Learn -> {
                learnt = null
                conflict = null
                val learnt = action.learnt
                inner.run {
                    val level = if (learnt.size > 1) assignment.level(learnt[1].variable) else 0
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
            is SolverAction.Enqueue -> {
                inner.assignment.newDecisionLevel()
                inner.assignment.uncheckedEnqueue(action.lit, null)
            }
        }
    }

    fun canExecute(action: SolverAction): Boolean {
        inner.run {
            return when (action) {
                is SolverAction.Solve -> propagated
                is SolverAction.Propagate -> ok && conflict == null && learnt == null
                is SolverAction.AnalyzeConflict -> ok && conflict != null && learnt == null
                is SolverAction.Learn -> ok && conflict != null && learnt != null
                is SolverAction.Backtrack -> ok && action.level in 0 until assignment.decisionLevel
                is SolverAction.Restart -> ok && assignment.decisionLevel > 0
                is SolverAction.Enqueue -> {
                    propagated
                        && action.lit.variable.index in 0 until assignment.numberOfVariables
                        && assignment.isActive(action.lit)
                        && assignment.value(action.lit) == LBool.UNDEF
                }
            }
        }
    }

    fun undo() {
        val historyToRepeat = history.dropLast(1)
        problem = problem.copy()
        for (action in historyToRepeat) {
            execute(action)
        }
    }
}

external interface WelcomeProps : Props {
    var request: String
}

val Welcome = FC<WelcomeProps> { props ->
    var request by useState(props.request)
    val solver by useState(CdclWrapper(CNF(emptyList())))

    // https://legacy.reactjs.org/docs/hooks-faq.html#is-there-something-like-forceupdate
    val (_, forceUpdateImpl) = useReducer({ x, _: Unit -> x + 1 }, 0)
    val forceUpdate = { forceUpdateImpl(Unit) }

    fun execute(action: SolverAction) {
        solver.execute(action)
        forceUpdate()
    }

    div {
        css {
            padding = 5.px
            backgroundColor = rgb(8, 97, 22)
            color = rgb(56, 246, 137)
            textAlign = TextAlign.center
            marginBottom = 10.px
            fontFamily = FontFamily.monospace
        }
        +"Kotlin-based SAT solver, v0.1"
    }

    div {
        css {
            marginBottom = 20.px
            padding = 5.px
            display = Display.block
            fontFamily = FontFamily.monospace
        }

        label {
            css {
                display = Display.block
                marginBottom = 20.px
                color = rgb(0, 0, 137)
            }
            +"Put your CNF in DIMACS format here"
        }

        textarea {
            css {
                display = Display.block
                marginBottom = 20.px
                backgroundColor = rgb(100, 100, 100)
                color = rgb(56, 246, 137)
            }
            rows = 25
            cols = 80
            value = request
            onChange = { event -> request = event.target.value }
        }

        button {
            css {
                marginBottom = 20.px
                display = Display.block
            }
            onClick = { _ ->
                try {
                    solver.problem = CNF.fromString(request)
                    forceUpdate()
                } catch (e: Exception) {
                    println("Error: ${e.message}")
                }
            }
            +"Create Solver"
        }
    }

    solver.inner.run {
        div {
            h2 { +"Solver State:" }
            table {
                tbody {
                    tr {
                        td { +"ok" }
                        td { +ok.toString() }
                    }
                    tr {
                        td { +"trail" }
                        td { +assignment.trail.map { it.toDimacs() }.toString() }
                    }
                    tr {
                        td { +"decisionLevel" }
                        td { +assignment.decisionLevel.toString() }
                    }
                    tr {
                        td { +"assignment" }
                        td { +assignment.value.toString() }
                    }
                    tr {
                        td { +"irreducible clauses" }
                        td { +db.clauses.filter { !it.deleted }.map { it.toDimacs() }.joinToString() }
                    }
                    tr {
                        td { +"learnts" }
                        td { +db.learnts.filter { !it.deleted }.map { it.toDimacs() }.joinToString() }
                    }
                    tr {
                        td { +"current conflict" }
                        td {
                            +solver.conflict?.toDimacs().toString()
                            button {
                                disabled = !solver.canExecute(SolverAction.AnalyzeConflict)
                                onClick = { _ ->
                                    execute(SolverAction.AnalyzeConflict)
                                }
                                +"Analyze"
                            }
                        }
                    }
                    tr {
                        td { +"current learnt" }
                        td {
                            +solver.learnt?.toDimacs().toString()
                            button {
                                disabled = solver.learnt == null
                                    || !solver.canExecute(SolverAction.Learn(solver.learnt!!))
                                onClick = { _ ->
                                    execute(SolverAction.Learn(solver.learnt!!))
                                }
                                +"Learn"
                            }
                        }
                    }
                    tr {
                        td { +"result" }
                        td {
                            when (solver.result) {
                                SolveResult.SAT -> span { css { color = NamedColor.green }; +"SAT" }
                                SolveResult.UNSAT -> span { css { color = NamedColor.red }; +"UNSAT" }
                                SolveResult.UNKNOWN -> +"UNKNOWN"
                            }
                        }
                    }
                }
            }
        }
    }

    div {
        h2 { +"Actions:" }
        button {
            disabled = !solver.canExecute(SolverAction.Solve)
            onClick = { _ ->
                execute(SolverAction.Solve)
            }
            +"Solve"
        }
        button {
            disabled = !solver.canExecute(SolverAction.Propagate)
            onClick = { _ ->
                execute(SolverAction.Propagate)
            }
            +"Propagate"
        }
        for (i in 0 until solver.inner.assignment.numberOfVariables) {
            button {
                disabled = !solver.canExecute(SolverAction.Enqueue(Var(i).posLit))
                onClick = { _ ->
                    execute(SolverAction.Enqueue(Var(i).posLit))
                }
                +"Enqueue ${i + 1}"
            }
            button {
                disabled = !solver.canExecute(SolverAction.Enqueue(Var(i).negLit))
                onClick = { _ ->
                    execute(SolverAction.Enqueue(Var(i).negLit))
                }
                +"Enqueue -${i + 1}"
            }
        }
        button {
            disabled = !solver.canExecute(SolverAction.Restart)
            onClick = { _ ->
                execute(SolverAction.Restart)
            }
            +"Restart"
        }
        for (level in 1 until solver.inner.assignment.decisionLevel) {
            button {
                disabled = !solver.canExecute(SolverAction.Backtrack(level))
                onClick = { _ ->
                    execute(SolverAction.Backtrack(level))
                }
                +"Backtrack to $level"
            }
        }
    }

    button {
        disabled = solver.history.isEmpty()
        onClick = { _ ->
            solver.undo()
            forceUpdate()
        }
        +"Undo"
    }

    div {
        css {
            position = Position.absolute
            display = Display.block
            marginRight = 0.px
            marginLeft = Margin("auto")

            fontFamily = FontFamily.monospace
            padding = 5.px
            color = rgb(0, 0, 137)
        }
        h2 { +"DIMACS CNF format:" }
        p { +"The number of variables and the number of clauses is defined by the line \"p cnf variables clauses\"." }
        p {
            +"Each of the next lines specifies a clause: a positive literal is denoted by the corresponding number, and a negative literal is denoted by the corresponding negative number. "
            br {}
            +"The last number in a line should be zero. For example:"
        }
        p {
            css {
                fontWeight = FontWeight.bold
            }
            +"p cnf 3 2"
            br {}
            +"1 2 -3 0"
            br {}
            +"-2 3 0"
        }
    }
}
