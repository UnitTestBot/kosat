package components

import SolverCommand
import emotion.react.css
import org.kosat.SolveResult
import org.kosat.Var
import org.kosat.cnf.CNF
import react.FC
import react.Props
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
import react.useContext
import react.useState
import web.cssom.Auto
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.FlexGrow
import web.cssom.FontFamily
import web.cssom.FontWeight
import web.cssom.GridArea
import web.cssom.GridTemplateAreas
import web.cssom.GridTemplateColumns
import web.cssom.Length
import web.cssom.Margin
import web.cssom.NamedColor
import web.cssom.Position
import web.cssom.TextAlign
import web.cssom.array
import web.cssom.fr
import web.cssom.ident
import web.cssom.number
import web.cssom.pct
import web.cssom.pt
import web.cssom.px
import web.cssom.rgb

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

    val solver = useContext(cdclWrapperContext)!!

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
            gridTemplateColumns = array(30.pct, 50.pct, 20.pct)
            gridTemplateRows = array(400.pt, 200.pt, 100.pt)
            gridTemplateAreas = GridTemplateAreas(
                arrayOf(ident("input"), ident("state"), ident("trail")),
                arrayOf(ident("history"), ident("actions"), ident("trail")),
                arrayOf(ident("assignment"), ident("assignment"), ident("trail")),
            )
        }

        div {
            css {
                marginBottom = 20.px
                padding = 5.px
                display = Display.flex
                flexDirection = FlexDirection.column
                fontFamily = FontFamily.monospace
                gridArea = ident("input")
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
                    flexGrow = number(1.0)
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
                gridArea = ident("history")
            }

            History {}
        }

        solver.state.inner.run {
            div {
                css {
                    gridArea = ident("state")
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
                                    command = SolverCommand.AnalyzeConflict
                                }
                                CommandButton {
                                    +"Analyze One"
                                    command = SolverCommand.AnalyzeOne
                                }
                                CommandButton {
                                    +"Minimize"
                                    command = SolverCommand.AnalysisMinimize
                                }
                                CommandButton {
                                    +"Learn As Is"
                                    command = SolverCommand.LearnAsIs
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
                gridArea = ident("trail")
            }
            TrailNode {}
        }

        div {
            css {
                gridArea = ident("actions")
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
            marginLeft = auto

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