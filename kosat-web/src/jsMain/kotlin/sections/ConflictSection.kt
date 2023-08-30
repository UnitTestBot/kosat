package sections

import SolverCommand
import cdclWrapperContext
import components.ClauseNode
import components.CommandButton
import components.EagerlyRunButton
import components.LitNode
import mui.material.Box
import mui.material.Stack
import mui.material.Typography
import mui.material.styles.Theme
import mui.material.styles.TypographyVariant
import mui.material.styles.useTheme
import mui.system.sx
import react.FC
import react.Props
import react.dom.html.ReactHTML.span
import react.useContext
import web.cssom.AlignItems
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.FontWeight
import web.cssom.JustifyContent
import web.cssom.JustifyItems
import web.cssom.array
import web.cssom.number
import web.cssom.pct
import web.cssom.pt
import web.cssom.scale

/**
 * Section of the visualizer for analyzing the conflict.
 */
val ConflictSection: FC<Props> = FC("ConflictSection") {
    val solver = useContext(cdclWrapperContext)!!
    val theme = useTheme<Theme>()

    val lastLevelLits = solver.state.inner.run {
        solver.state.conflict?.lits?.count {
            assignment.level(it) == assignment.decisionLevel
        } ?: 0
    }

    val backtrackingLevel = solver.state.inner.run {
        if (lastLevelLits != 1 || solver.state.conflict == null) {
            return@run null
        }
        var result = 0
        for (lit in solver.state.conflict!!.lits) {
            val level = assignment.level(lit)
            if (result < level && level < assignment.decisionLevel) {
                result = level
            }
        }
        result
    }

    val lastOnTrail = solver.state.inner.run {
        solver.state.conflict?.lits?.maxBy {
            assignment.trailIndex(it.variable)
        }
    }

    Box {
        sx {
            padding = 8.pt
            margin = (-8).pt
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
                    gap = 8.pt
                    width = 100.pct
                }
                Box {
                    sx {
                        flexGrow = number(1.0)
                        display = Display.flex
                        justifyContent = JustifyContent.center
                        alignItems = AlignItems.center
                        gap = 8.pt
                    }

                    for (lit in solver.state.conflict!!.lits) {
                        Box {
                            sx {
                                display = Display.flex
                                alignItems = AlignItems.center
                                height = 50.pt
                                justifyItems = JustifyItems.end
                                gap = 2.pt
                                flexDirection = FlexDirection.column
                            }
                            Box {
                                sx {
                                    display = Display.flex
                                    alignItems = AlignItems.center
                                    flexDirection = FlexDirection.column
                                }
                                Typography {
                                    sx {
                                        color = theme.palette.text.secondary
                                        fontSize = 8.pt
                                    }
                                    component = span
                                    variant = TypographyVariant.body2
                                    +"trail index: ${solver.state.inner.assignment.trailIndex(lit.variable)}"
                                }
                            }
                            LitNode {
                                this.lit = lit
                            }
                            if (lastLevelLits == 1 && solver.state.inner.assignment.level(lit) == solver.state.inner.assignment.decisionLevel) {
                                Typography {
                                    sx {
                                        color = theme.palette.text.primary
                                        fontSize = 8.pt
                                        fontWeight = FontWeight.bold
                                    }
                                    component = span
                                    variant = TypographyVariant.body2
                                    +"UIP"
                                }
                            } else {
                                Typography {
                                    sx {
                                        color = theme.palette.text.secondary
                                        fontSize = 8.pt
                                    }
                                    component = span
                                    variant = TypographyVariant.body2
                                    +"level: ${solver.state.inner.assignment.level(lit)}"
                                }
                            }
                        }
                    }
                }
                if (lastLevelLits > 1) {
                    Box {
                        sx {
                            display = Display.flex
                            alignItems = AlignItems.center
                            flexDirection = FlexDirection.column
                            transform = scale(0.8)
                            minWidth = 120.pt
                        }

                        val firstLit = solver.state.conflict!!.lits.first()

                        Typography {
                            sx {
                                color = theme.palette.text.secondary
                                margin = 0.pt
                                transform = scale(0.8)
                            }
                            component = span
                            variant = TypographyVariant.body2
                            +"Reason of "
                            LitNode {
                                lit = firstLit.neg
                            }
                            +": "
                        }

                        ClauseNode {
                            clause = solver.state.inner.assignment.reason(firstLit.variable)!!
                        }
                    }
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

                    if (lastLevelLits > 1) {
                        +"Analyze One (replace "
                        Box {
                            component = span
                            sx {
                                transform = scale(0.8)
                                padding = array(4.pt, 7.pt)
                                margin = (-8).pt
                            }
                            LitNode {
                                lit = lastOnTrail!!
                            }
                        }
                        +")"
                    } else {
                        +"Analyze One"
                    }
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

                    if (backtrackingLevel != null) {
                        +" to level $backtrackingLevel"
                    }

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