package components

import SolverCommand
import emotion.react.css
import js.core.jso
import mui.material.Box
import mui.material.Card
import mui.material.Paper
import mui.material.TextField
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import org.kosat.SolveResult
import org.kosat.Var
import org.kosat.cnf.CNF
import react.FC
import react.Props
import react.PropsWithChildren
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
import react.dom.onChange
import react.useContext
import react.useState
import web.cssom.AlignItems
import web.cssom.AlignSelf
import web.cssom.Auto
import web.cssom.Auto.Companion.auto
import web.cssom.CSSMathOperator.Companion.min
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
import web.cssom.atrule.height
import web.cssom.clamp
import web.cssom.fr
import web.cssom.ident
import web.cssom.minmax
import web.cssom.number
import web.cssom.pct
import web.cssom.pt
import web.cssom.px
import web.cssom.rgb

external interface SectionPaperProps : PropsWithChildren {
    var gridArea: GridArea
    var title: String
    var maxHeight: Length?
}

val SectionPaper: FC<SectionPaperProps> = FC { props ->
    Paper {
        elevation = 3

        css {
            padding = 8.pt
            display = Display.flex
            flexDirection = FlexDirection.column
            gridArea = props.gridArea
            gap = 8.pt
            maxHeight = props.maxHeight
        }

        Typography {
            sx {
                alignSelf = AlignSelf.center
                textAlign = TextAlign.center
            }
            variant = TypographyVariant.h2
            +props.title
        }

        +props.children
    }
}

external interface VisualizerProps : Props

val Visualizer: FC<VisualizerProps> = FC { _ ->
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

    Box {
        sx {
            display = Display.grid
            gap = 8.pt
            padding = 8.pt
            gridTemplateColumns = array(3.fr, 50.pct, 2.fr)
            gridTemplateRows = array(130.pt, 250.pt, 120.pt, 190.pt)
            gridTemplateAreas = GridTemplateAreas(
                arrayOf(ident("input"), ident("state"), ident("trail")),
                arrayOf(ident("input"), ident("db"), ident("trail")),
                arrayOf(ident("assignment"), ident("assignment"), ident("trail")),
                arrayOf(ident("history"), ident("actions"), ident("trail")),
            )
        }

        SectionPaper {
            gridArea = ident("input")
            title = "Input"

            TextField {
                sx {
                    overflow = auto
                    flexGrow = number(1.0)
                }

                inputProps = jso {
                    style = jso {
                        fontFamily = FontFamily.monospace
                    }
                }

                value = request
                multiline = true
                onChange = { event -> request = event.target.asDynamic().value as String }
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

        SectionPaper {
            gridArea = ident("history")
            title = "History"
            History {}
        }

        solver.state.inner.run {
            SectionPaper {
                gridArea = ident("state")
                title = "Solver State"
                StateNode {}
            }

            SectionPaper {
                gridArea = ident("db")
                title = "Clause Database"
                ClauseDbNode {}
            }

            SectionPaper {
                gridArea = ident("assignment")
                title = "Assignment"
                AssignmentNode {}
            }

            /*
            div {
                css {
                    gridArea = ident("db")
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
             */
        }

        SectionPaper {
            gridArea = ident("trail")
            title = "Trail"
            TrailNode {}
        }

        SectionPaper {
            gridArea = ident("actions")
            title = "Actions"
            ActionsNode {}
        }
    }
}