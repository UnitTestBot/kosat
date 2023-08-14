import csstype.AlignItems
import csstype.Border
import csstype.Cursor
import csstype.Display
import csstype.FlexDirection
import csstype.FlexGrow
import csstype.FontFamily
import csstype.FontStyle
import csstype.FontWeight
import csstype.GridArea
import csstype.GridColumn
import csstype.GridTemplateColumns
import csstype.GridTemplateRows
import csstype.JustifyContent
import csstype.LineStyle
import csstype.Margin
import csstype.NamedColor
import csstype.Position
import csstype.Scale
import csstype.TextAlign
import csstype.fr
import csstype.pct
import csstype.pt
import csstype.px
import csstype.rgb
import kotlinext.js.assign
import mui.material.Box
import mui.material.Tooltip
import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.LBool
import org.kosat.Lit
import org.kosat.SolveResult
import org.kosat.Var
import org.kosat.cnf.CNF
import org.kosat.get
import org.kosat.set
import org.kosat.swap
import react.FC
import react.Props
import react.PropsWithChildren
import react.create
import react.createContext
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
import react.key
import react.useContext
import react.useReducer
import react.useState

sealed interface SolverCommand {
    data class Recreate(val cnf: CNF) : SolverCommand
    data object Undo : SolverCommand
    data object Solve : SolverCommand
    data object Propagate : SolverCommand
    data object PropagateOne : SolverCommand
    data object Restart : SolverCommand
    data class Backtrack(val level: Int) : SolverCommand
    data object Learn : SolverCommand
    data class Enqueue(val lit: Lit) : SolverCommand
    data object AnalyzeConflict : SolverCommand
    data object AnalyzeOne : SolverCommand
    data object AnalysisMinimize : SolverCommand
    data object LearnAsIs : SolverCommand
}

class CdclState(initialProblem: CNF) {
    var problem: CNF = initialProblem
    var inner: CDCL = CDCL(initialProblem)
    val history: MutableList<SolverCommand> = mutableListOf()
    var conflict: Clause? = null
    var learnt: Clause? = null

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
            && learnt == null
            && inner.assignment.qhead == inner.assignment.trail.size

    fun execute(command: SolverCommand) {
        history.add(command)
        check(canExecute(command))
        when (command) {
            is SolverCommand.Recreate -> {
                problem = command.cnf
                inner = CDCL(problem)
                conflict = null
                learnt = null
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

            is SolverCommand.AnalyzeConflict -> {
                learnt = inner.analyzeConflict(conflict!!)
            }

            is SolverCommand.AnalyzeOne -> {
                inner.analyzeOne()
            }

            is SolverCommand.AnalysisMinimize -> {
                learnt = inner.analyzeConflict(conflict!!)
            }

            is SolverCommand.LearnAsIs -> {
                val learnt = conflict!!
                this.learnt = null
                this.conflict = null
                inner.run {
                    learnt.lits.sortByDescending { assignment.level(it) }
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

            is SolverCommand.Backtrack -> {
                inner.backtrack(command.level)
                learnt = null
                conflict = null
            }

            is SolverCommand.Restart -> {
                inner.backtrack(0)
                learnt = null
                conflict = null
            }

            is SolverCommand.Learn -> {
                val learnt = learnt!!
                this.learnt = null
                conflict = null
                inner.run {
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

            is SolverCommand.Enqueue -> {
                inner.assignment.newDecisionLevel()
                inner.assignment.uncheckedEnqueue(command.lit, null)
            }
        }
    }

    fun canExecute(command: SolverCommand): Boolean {
        inner.run {
            return when (command) {
                is SolverCommand.Recreate -> true
                is SolverCommand.Undo -> history.isNotEmpty()
                is SolverCommand.Solve -> propagated
                is SolverCommand.Propagate ->
                    ok
                        && conflict == null
                        && learnt == null
                        && inner.assignment.qhead < inner.assignment.trail.size
                is SolverCommand.PropagateOne ->
                    ok
                        && conflict == null
                        && learnt == null
                        && inner.assignment.qhead < inner.assignment.trail.size
                is SolverCommand.AnalyzeConflict -> ok && conflict != null && learnt == null
                is SolverCommand.AnalyzeOne ->
                    ok
                        && conflict != null
                        && learnt == null
                        && conflict!!.lits.count { assignment.level(it) == assignment.decisionLevel } > 1
                is SolverCommand.AnalysisMinimize ->
                    ok
                        && conflict != null
                        && learnt == null
                        && conflict!!.lits.count { assignment.level(it) == assignment.decisionLevel } == 1
                is SolverCommand.LearnAsIs ->
                    ok
                        && conflict != null
                        && learnt == null
                        && conflict!!.lits.count { assignment.level(it) == assignment.decisionLevel } == 1
                is SolverCommand.Learn -> ok && conflict != null && learnt != null
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

    private fun CDCL.analyzeOne() {
        val lits = conflict!!.lits.toMutableList()
        lits.sortBy { assignment.trailIndex(it.variable) }
        val replaceWithReason = lits.removeLast()
        for (lit in assignment.reason(replaceWithReason.variable)!!.lits) {
            if (lit == replaceWithReason.neg) continue
            lits.add(lit)
        }
        conflict = Clause(lits.toSet().toMutableList(), learnt = true)
    }
}

data class CdclWrapper(val version: Int, val state: CdclState) {
    fun canExecute(command: SolverCommand): Boolean {
        return state.canExecute(command)
    }

    fun execute(command: SolverCommand): CdclWrapper {
        state.execute(command)
        return copy(version = version + 1)
    }

    val result get() = state.result
}


external interface VisualizerProps : Props

val cdclWrapperContext = createContext<CdclWrapper>()
val cdclDispatchContext = createContext<(SolverCommand) -> Unit>()

val Visualizer = FC<VisualizerProps> {
    val (solver, dispatch) = useReducer({ wrapper: CdclWrapper, command: SolverCommand ->
        wrapper.execute(command)
    }, CdclWrapper(0, CdclState(CNF(emptyList()))))

    cdclWrapperContext.Provider(solver) {
        cdclDispatchContext.Provider(dispatch) {
            Welcome {}
        }
    }
}


external interface LiteralProps : Props {
    @Suppress("INLINE_CLASS_IN_EXTERNAL_DECLARATION_WARNING")
    var lit: Lit
}

val Literal = FC<LiteralProps> { props ->
    val solver = useContext(cdclWrapperContext)
    val lit = props.lit

    val data = solver.state.inner.assignment.varData[lit.variable]
    val value = if (!data.active) {
        LBool.UNDEF
    } else {
        solver.state.inner.assignment.value(lit)
    }
    val level0 = data.level == 0

    val fill = when {
        value == LBool.TRUE && level0 -> rgb(0, 255, 0)
        value == LBool.FALSE && level0 -> rgb(255, 0, 0)
        !data.active -> rgb(128, 128, 128)
        data.frozen -> rgb(128, 128, 255)
        else -> rgb(200, 200, 200)
    }

    val borderColor = when {
        value == LBool.TRUE && !level0 -> rgb(0, 255, 0)
        value == LBool.FALSE && !level0 -> rgb(255, 0, 0)
        else -> null
    }

    Tooltip {
        title = span.create {
            if (lit.isPos) {
                div { +"Positive literal" }
            } else {
                div { +"Negative literal" }
            }

            if (!data.active) div { +"Inactive (eliminated)" }
            if (data.frozen) div { +"Frozen" }
            if (value == LBool.TRUE) div { +"Assigned to TRUE at level ${data.level}" }
            if (value == LBool.FALSE) div { +"Assigned to FALSE at level ${data.level}" }
            if (data.reason != null) div {
                +"Reason:"
                Box {
                    css {
                        scale = Scale(0.3)
                    }
                    // FIXME: this is a hack
                    (ClauseNodeFn()) {
                        clause = data.reason!!
                    }
                }
            }

            div {
                CommandButton {
                    command = SolverCommand.Enqueue(lit)
                    +"Enqueue"
                }
            }

            div {
                CommandButton {
                    command = SolverCommand.Enqueue(lit.neg)
                    +"Enqueue negation"
                }
            }
        }

        span {
            css {
                width = if (borderColor == null) 24.pt else 18.pt
                height = if (borderColor == null) 24.pt else 18.pt
                borderRadius = 100.pct
                display = Display.inlineFlex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                backgroundColor = fill
                cursor = Cursor.pointer
                fontStyle = if (data.frozen) FontStyle.italic else null
                border = borderColor?.let { Border(3.pt, LineStyle.solid, it) }
            }

            onMouseOver
            +if (lit.isPos) {
                "${lit.variable.index + 1}"
            } else {
                "-${lit.variable.index + 1}"
            }
        }
    }
}

external interface ClauseProps : Props {
    var clause: Clause
}

val ClauseNode = FC<ClauseProps> { props ->
    val clause = props.clause
    val solver = useContext(cdclWrapperContext)

    val values = clause.lits.map {
        if (!solver.state.inner.assignment.isActive(it)) {
            LBool.UNDEF
        } else {
            solver.state.inner.assignment.value(it)
        }
    }

    val satisfied = values.any { it == LBool.TRUE }
    val falsified = values.all { it == LBool.FALSE }
    val almostFalsified = values.count { it == LBool.FALSE } == values.size - 1
    val color = when {
        satisfied -> rgb(0, 200, 0)
        falsified -> rgb(200, 0, 0)
        almostFalsified -> rgb(100, 0, 0)
        else -> rgb(150, 150, 150)
    }

    span {
        css {
            display = Display.inlineFlex
            height = 24.pt
            borderRadius = 15.pt
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            backgroundColor = color
            padding = 3.pt
            margin = Margin(0.pt, 3.pt)
        }
        for (lit in clause.lits) {
            Literal {
                key = lit.toString()
                this.lit = lit
            }
        }
    }
}

fun ClauseNodeFn(): FC<ClauseProps> = ClauseNode

external interface ActionButtonProps : PropsWithChildren {
    var command: SolverCommand?
}

val CommandButton = FC<ActionButtonProps> { props ->
    val solver = useContext(cdclWrapperContext)
    val dispatch = useContext(cdclDispatchContext)

    val command = props.command
    button {
        disabled = command == null || !solver.canExecute(command)
        onClick = { dispatch(command!!) }
        props.children?.unaryPlus()
    }
}

external interface HistoryProps : Props

val History = FC<TrailProps> { _ ->
    val solver = useContext(cdclWrapperContext)

    div {
        for (command in solver.state.history) {
            span {
                +command.toString()
            }
            br {}
        }
    }
}

external interface TrailProps : Props

val TrailNode = FC<TrailProps> { _ ->
    val solver = useContext(cdclWrapperContext)
    val assignment = solver.state.inner.assignment

    table {
        tbody {
            for (level in 0..assignment.decisionLevel) {
                tr {
                    key = level.toString()
                    td { +"level $level" }
                    td {
                        for (i in 0 until assignment.trail.size) {
                            val lit = assignment.trail[i]
                            if (assignment.level(lit) != level) continue
                            div {
                                Literal {
                                    key = lit.toString()
                                    this.lit = lit
                                }
                                if (assignment.reason(lit.variable) != null) {
                                    ClauseNode {
                                        clause = assignment.reason(lit.variable)!!
                                    }
                                }
                                if (i == assignment.qhead) {
                                    css {
                                        backgroundColor = rgb(200, 200, 200)
                                    }
                                    span {
                                        +"qhead"
                                        css {
                                            fontStyle = FontStyle.italic
                                            margin = 3.pt
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

external interface WelcomeProps : Props

val Welcome = FC<WelcomeProps> { _ ->
    var request by useState(
        """
        p cnf 9 13
        -1 2 0
        -1 3 0
        -2 -3 4 0
        -4 5 0
        -4 6 0
        -5 -6 7 0
        -7 1 0
        1 4 7 8 0
        -1 -4 -7 -8 0
        1 4 7 9 0
        -1 -4 -7 -9 0
        8 9 0
        -8 -9 0
    """.trimIndent()
    )

    val solver = useContext(cdclWrapperContext)

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
            display = Display.grid
            // gridTemplateColumns = GridTemplateColumns(30.pct, 40.pct, 20.pct)
            gridTemplateColumns = GridTemplateColumns(30.pct, 50.pct, 20.pct)
            gridTemplateRows = GridTemplateRows(2.fr, 1.fr, 100.pt)
        }

        div {
            css {
                marginBottom = 20.px
                padding = 5.px
                display = Display.flex
                flexDirection = FlexDirection.column
                fontFamily = FontFamily.monospace
                gridArea = GridArea("1 / 1 / 1 / 1")
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
                    flexGrow = FlexGrow(1.0)
                }
                rows = 25
                value = request
                onChange = { event -> request = event.target.value }
            }

            CommandButton {
                +"Recreate"
                command = run {
                    try {
                        SolverCommand.Recreate(CNF.fromString(request))
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }

        div {
            css {
                display = Display.flex
                gridArea = GridArea("2 / 1 / 2 / 1")
            }

            History {}
        }

        solver.state.inner.run {
            div {
                css {
                    gridArea = GridArea("1 / 2 / 1 / 2")
                }

                h2 { +"Solver State:" }
                table {
                    tbody {
                        tr {
                            td { +"ok" }
                            td { +ok.toString() }
                        }
                        tr {
                            td { +"decisionLevel" }
                            td { +assignment.decisionLevel.toString() }
                        }
                        tr {
                            td { +"assignment" }
                            td {
                                for (varIndex in 0 until assignment.numberOfVariables) {
                                    Literal {
                                        key = varIndex.toString()
                                        lit = Var(varIndex).posLit
                                    }
                                }
                            }
                        }
                        tr {
                            td { +"irreducible clauses" }
                            td {
                                db.clauses.withIndex().filter { !it.value.deleted }.map {
                                    ClauseNode {
                                        key = it.index.toString()
                                        clause = it.value
                                    }
                                }
                            }
                        }
                        tr {
                            td { +"learnts" }
                            td {
                                db.learnts.withIndex().filter { !it.value.deleted }.map {
                                    ClauseNode {
                                        key = it.index.toString()
                                        clause = it.value
                                    }
                                }
                            }
                        }
                        tr {
                            td { +"current conflict" }
                            td {
                                if (solver.state.conflict != null) {
                                    ClauseNode {
                                        clause = solver.state.conflict!!
                                    }
                                }
                                CommandButton {
                                    +"Analyze"
                                    command = solver.state.conflict?.let { SolverCommand.AnalyzeConflict }
                                }
                                CommandButton {
                                    +"Analyze One"
                                    command = solver.state.conflict?.let { SolverCommand.AnalyzeOne }
                                }
                                CommandButton {
                                    +"Minimize"
                                    command = solver.state.conflict?.let { SolverCommand.AnalysisMinimize }
                                }
                                CommandButton {
                                    +"Learn As Is"
                                    command = solver.state.conflict?.let { SolverCommand.LearnAsIs }
                                }
                            }
                        }
                        tr {
                            td { +"current learnt" }
                            td {
                                if (solver.state.learnt != null) {
                                    ClauseNode {
                                        clause = solver.state.learnt!!
                                    }
                                }
                                CommandButton {
                                    +"Learn"
                                    command = solver.state.learnt?.let { SolverCommand.Learn }
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
            css {
                gridArea = GridArea("1 / 3 / 3 / 3")
            }
            TrailNode {}
        }

        div {
            css {
                gridArea = GridArea("2 / 2 / 2 / 2")
            }

            h2 { +"Actions:" }
            CommandButton {
                +"Solve"
                command = SolverCommand.Solve
            }
            CommandButton {
                +"Propagate"
                command = SolverCommand.Propagate
            }
            CommandButton {
                +"Propagate One"
                command = SolverCommand.PropagateOne
            }
            for (i in 0 until solver.state.inner.assignment.numberOfVariables) {
                CommandButton {
                    +"Enqueue "
                    Literal { lit = Var(i).posLit }
                    command = SolverCommand.Enqueue(Var(i).posLit)
                }
                CommandButton {
                    +"Enqueue "
                    Literal { lit = Var(i).negLit }
                    command = SolverCommand.Enqueue(Var(i).negLit)
                }
            }
            CommandButton {
                +"Restart"
                command = SolverCommand.Restart
            }
            for (level in 1 until solver.state.inner.assignment.decisionLevel) {
                CommandButton {
                    +"Backtrack to $level"
                    command = SolverCommand.Backtrack(level)
                }
            }

            CommandButton {
                command = SolverCommand.Undo
                +"Undo"
            }
        }
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
