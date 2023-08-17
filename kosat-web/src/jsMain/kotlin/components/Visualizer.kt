package components

import SolverCommand
import cdclDispatchContext
import cdclWrapperContext
import emotion.react.css
import mui.icons.material.Help
import mui.material.Box
import mui.material.Paper
import mui.material.Tooltip
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.FC
import react.Props
import react.PropsWithChildren
import react.create
import react.useContext
import react.useEffect
import react.useEffectOnce
import web.cssom.AlignSelf
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.GridArea
import web.cssom.GridTemplateAreas
import web.cssom.Length
import web.cssom.TextAlign
import web.cssom.array
import web.cssom.fr
import web.cssom.ident
import web.cssom.pct
import web.cssom.pt
import web.cssom.scale
import web.dom.document

external interface SectionPaperProps : PropsWithChildren {
    var gridArea: GridArea
    var title: String
    var description: String?
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

            if (props.description != null) {
                Tooltip {
                    title = Typography.create {
                        variant = TypographyVariant.body1
                        +props.description
                    }

                    Help {
                        sx {
                            transform = scale(0.7)
                        }
                    }
                }
            }
        }

        +props.children
    }
}

external interface VisualizerProps : Props

val Visualizer: FC<VisualizerProps> = FC { _ ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!

    useEffect(solver) {
        document.onkeydown = { event ->
            console.log(event.key)
            when {
                event.ctrlKey && event.key == "z" && solver.canExecute(WrapperCommand.Undo) ->
                    dispatch(WrapperCommand.Undo)
                event.key == "ArrowUp" && solver.canExecute(WrapperCommand.Undo) ->
                    dispatch(WrapperCommand.Undo)
                event.ctrlKey && event.key == "y" && solver.canExecute(WrapperCommand.Redo) ->
                    dispatch(WrapperCommand.Redo)
                event.ctrlKey && event.key == "Z" && solver.canExecute(WrapperCommand.Redo) ->
                    dispatch(WrapperCommand.Redo)
                event.key == "ArrowDown" && solver.canExecute(WrapperCommand.Redo) ->
                    dispatch(WrapperCommand.Redo)
                event.key == " " && solver.canExecute(SolverCommand.Propagate) ->
                    dispatch(SolverCommand.Propagate)
            }
        }

        cleanup {
            document.onkeydown = null
        }
    }

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
                arrayOf(ident("history"), ident("analysis"), ident("actions")),
            )
        }

        SectionPaper {
            gridArea = ident("input")
            title = "Input"

            InputNode {}

            description = """
                Input clauses are parsed from the input text field. 
                The input is parsed as a DIMACS CNF file.
            """.trimIndent()
        }

        SectionPaper {
            gridArea = ident("history")
            title = "Time Travel"
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
        }

        SectionPaper {
            gridArea = ident("trail")
            title = "Trail"
            TrailNode {}
        }

        SectionPaper {
            gridArea = ident("analysis")
            title = "Conflict Analysis"
            AnalysisNode {}
        }

        SectionPaper {
            gridArea = ident("actions")
            title = "Actions"
            ActionsNode {}
        }
    }
}