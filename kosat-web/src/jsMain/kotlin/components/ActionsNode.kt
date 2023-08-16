package components

import SolverCommand
import mui.material.Box
import mui.material.ButtonGroup
import mui.material.Paper
import mui.material.Typography
import mui.material.styles.Theme
import mui.material.styles.TypographyVariant
import mui.material.styles.useTheme
import mui.system.sx
import react.FC
import react.Props
import react.useContext
import web.cssom.AlignItems
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.JustifyItems
import web.cssom.TextAlign
import web.cssom.array
import web.cssom.fr
import web.cssom.number
import web.cssom.pct
import web.cssom.pt

external interface ActionsProps : Props

val ActionsNode: FC<ActionsProps> = FC {
    val solver = useContext(cdclWrapperContext)!!
    val theme = useTheme<Theme>()

    Box {
        sx {
            display = Display.grid
            gridTemplateColumns = array(1.fr, 1.fr)
            gridTemplateRows = array(100.pct)
            flexGrow = number(1.0)
            gap = 8.pt
        }

        Box {
            CommandButton {
                +"Solve"
                command = SolverCommand.Solve
            }

            CommandButton {
                +"Search"
                command = SolverCommand.Search
            }
        }

        Paper {
            elevation = 3

            sx {
                padding = 8.pt
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = AlignItems.center
                gap = 8.pt
                overflow = auto
            }

            Typography {
                sx {
                    textAlign = TextAlign.center
                }
                variant = TypographyVariant.h3
                +"Conflict Analysis"
            }

            if (solver.state.conflict == null) {
                Box {
                    sx {
                        flexGrow = number(1.0)
                        display = Display.flex
                        justifyItems = JustifyItems.center
                        alignItems = AlignItems.center
                    }

                    Typography {
                        variant = TypographyVariant.body1
                        sx {
                            color = theme.palette.text.disabled
                        }
                        +"No conflict!"
                    }
                }
            } else {
                Box {
                    sx {
                        flexGrow = number(1.0)
                        display = Display.flex
                        justifyItems = JustifyItems.center
                        alignItems = AlignItems.center
                    }

                    ClauseNode {
                        clause = solver.state.conflict!!
                    }
                }
            }

            ButtonGroup {
                CommandButton {
                    +"Analyze"
                    command = SolverCommand.AnalyzeConflict
                }
                CommandButton {
                    +"Analyze One"
                    command = SolverCommand.AnalyzeOne
                }
            }

            ButtonGroup {
                CommandButton {
                    +"Minimize"
                    command = SolverCommand.AnalysisMinimize
                }
                CommandButton {
                    +"Learn and Backtrack"
                    command = SolverCommand.LearnAndBacktrack
                }
            }
        }
    }
}