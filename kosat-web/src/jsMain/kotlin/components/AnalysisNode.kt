package components

import SolverCommand
import cdclWrapperContext
import mui.material.Box
import mui.material.Stack
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
import web.cssom.number
import web.cssom.pct
import web.cssom.pt

external interface ActionsProps : Props

val AnalysisNode: FC<ActionsProps> = FC {
    val solver = useContext(cdclWrapperContext)!!
    val theme = useTheme<Theme>()

    Box {
        sx {
            padding = 8.pt
            height = 100.pct
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
            gap = 8.pt
            overflow = auto
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

        Stack {
            sx {
                width = 100.pct
            }

            Box {
                sx {
                    display = Display.flex
                    alignItems = AlignItems.center
                    gap = 8.pt
                }

                EagerlyRunButton {
                    command = SolverCommand.AnalyzeConflict
                    description = """
                            Automatically analyze the conflict every time it occurs.
                        """.trimIndent()
                }


                CommandButton {
                    sx {
                        flexGrow = number(1.0)
                    }

                    +"Analyze"
                    command = SolverCommand.AnalyzeConflict
                }

                CommandButton {
                    sx {
                        flexGrow = number(1.0)
                    }

                    +"Analyze One"
                    command = SolverCommand.AnalyzeOne
                }
            }

            Box {
                sx {
                    display = Display.flex
                    alignItems = AlignItems.center
                    gap = 8.pt
                }

                EagerlyRunButton {
                    command = SolverCommand.AnalysisMinimize
                    description = """
                            Automatically minimize the conflict every time it is analyzed.
                        """.trimIndent()
                }

                CommandButton {
                    sx {
                        flexGrow = number(1.0)
                    }

                    +"Minimize"
                    command = SolverCommand.AnalysisMinimize
                }

                CommandButton {
                    sx {
                        flexGrow = number(1.0)
                    }

                    +"Learn and Backtrack"
                    command = SolverCommand.LearnAndBacktrack
                }

                EagerlyRunButton {
                    command = SolverCommand.LearnAndBacktrack
                    description = """
                            Automatically learn and backtrack every time the conflict is analyzed.
                        """.trimIndent()
                }
            }
        }
    }
}