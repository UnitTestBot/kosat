package components

import SolverCommand
import csstype.Display
import csstype.FlexDirection
import csstype.FlexGrow
import csstype.FontFamily
import csstype.FontWeight
import csstype.GridArea
import csstype.GridTemplateColumns
import csstype.GridTemplateRows
import csstype.Margin
import csstype.NamedColor
import csstype.Position
import csstype.TextAlign
import csstype.fr
import csstype.pct
import csstype.pt
import csstype.px
import csstype.rgb
import org.kosat.SolveResult
import org.kosat.Var
import org.kosat.cnf.CNF
import react.FC
import react.Props
import react.css.css
import react.dom.html.ReactHTML.br
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
import react.useState

external interface VisualizerProps : Props

val Visualizer = FC<VisualizerProps> { _ ->
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
                                    LitNode {
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
                    LitNode { lit = Var(i).posLit }
                    command = SolverCommand.Enqueue(Var(i).posLit)
                }
                CommandButton {
                    +"Enqueue "
                    LitNode { lit = Var(i).negLit }
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