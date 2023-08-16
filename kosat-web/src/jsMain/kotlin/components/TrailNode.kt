package components

import SolverCommand
import mui.icons.material.Download
import mui.icons.material.ExpandLess
import mui.icons.material.ExpandMore
import mui.icons.material.RestartAlt
import mui.material.Box
import mui.material.Collapse
import mui.material.Icon
import mui.material.IconButton
import mui.material.List
import mui.material.ListItem
import mui.material.ListItemIcon
import mui.material.ListItemText
import mui.material.Stack
import mui.material.Typography
import mui.material.styles.Theme
import mui.material.styles.TypographyVariant
import mui.material.styles.useTheme
import mui.system.sx
import react.FC
import react.Props
import react.useContext
import react.useState
import web.cssom.AlignItems
import web.cssom.AlignSelf
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.FontWeight
import web.cssom.number
import web.cssom.pct
import web.cssom.pt
import web.cssom.rgb

external interface TrailLevelProps : Props {
    var level: Int
}

val TrailLevelNode: FC<TrailLevelProps> = FC { props ->
    var opened by useState(true)
    val solver = useContext(cdclWrapperContext)!!
    val assignment = solver.state.inner.assignment
    val theme = useTheme<Theme>()

    val level = props.level

    ListItem {
        key = level.toString() + "level-header"

        ListItemIcon {
            IconCommandButton {
                RestartAlt {}
                command = SolverCommand.Backtrack(level)
            }
        }

        ListItemText {
            +"Level $level"
        }

        IconButton {
            onClick = {
                opened = !opened
            }

            if (opened) {
                ExpandLess {}
            } else {
                ExpandMore {}
            }
        }
    }

    Collapse {
        key = level.toString()
        `in` = opened

        Stack {
            for (i in 0 until assignment.trail.size) {
                val lit = assignment.trail[i]
                if (assignment.level(lit) != level) continue

                Box {
                    sx {
                        display = Display.flex
                        alignItems = AlignItems.center
                        paddingLeft = 16.pt
                        /*
                        if (i == assignment.qhead) {
                            border = Border(3.pt, LineStyle.solid, theme.palette.secondary.main)
                        }
                         */

                        borderRadius = 5.pt

                        if (i % 2 == 0) {
                            backgroundColor = rgb(240, 240, 240)
                        }
                    }

                    IconCommandButton {
                        if (i >= assignment.qhead) {
                            Download {}
                        } else {
                            Icon {}
                        }

                        command = SolverCommand.PropagateUpTo(i)
                    }

                    key = lit.toString()

                    LitNode {
                        this.lit = lit
                    }

                    Box {
                        sx {
                            flexGrow = number(1.0)
                        }

                        if (assignment.reason(lit.variable) != null) {
                            ClauseNode {
                                clause = assignment.reason(lit.variable)!!
                                scale = 0.8
                            }
                        }
                    }

                    /*
                    if (i == assignment.qhead) {
                        Box {
                            sx {
                                display = Display.flex
                                alignSelf = AlignSelf.stretch
                                flexDirection = FlexDirection.column
                            }

                            Typography {
                                variant = TypographyVariant.subtitle2
                                sx {
                                    fontSize = 8.pt
                                    fontWeight = FontWeight.bolder
                                    color = theme.palette.secondary.main
                                }
                                +"qhead"
                            }
                        }
                    }
                     */
                }

                if (i + 1 == assignment.qhead) {
                    Box {
                        sx {
                            alignSelf = AlignSelf.stretch
                            display = Display.flex
                            flexDirection = FlexDirection.column
                            alignItems = AlignItems.end
                        }

                        Box {
                            sx {
                                borderRadius = 2.pt
                                height = 4.pt
                                width = 100.pct
                                backgroundColor = theme.palette.secondary.main
                            }
                        }

                        Typography {
                            variant = TypographyVariant.subtitle2
                            sx {
                                fontSize = 8.pt
                                fontWeight = FontWeight.bolder
                                color = theme.palette.secondary.main
                            }
                            +"Propagated up to here"
                        }
                    }
                }
            }
        }
    }
}

external interface TrailProps : Props

val TrailNode = FC<TrailProps> { _ ->
    val solver = useContext(cdclWrapperContext)!!
    val assignment = solver.state.inner.assignment

    List {
        sx {
            overflow = auto
        }

        for (level in 0..assignment.decisionLevel) {
            TrailLevelNode {
                this.level = level
            }
        }
    }

    /*

    Box {
        sx {
            display = Display.flex
            flexDirection = FlexDirection.column
        }

        for (level in 0..assignment.decisionLevel) {
            Box {
                sx {
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    backgroundColor = if (level % 2 == 0) {
                        colors.evenLevel
                    } else {
                        colors.oddLevel
                    }
                }

                key = level.toString()

                LabelledNumber {
                    label = "Level"
                    value = level
                }

                Box {
                    sx {
                        flexGrow = number(1.0)
                    }

                    for (i in 0 until assignment.trail.size) {
                        val lit = assignment.trail[i]
                        if (assignment.level(lit) != level) continue

                        Box {
                            key = lit.toString()

                            sx {
                                display = Display.flex
                                alignItems = AlignItems.center

                                backgroundColor = when {
                                    level % 2 == 0 && i % 2 == 0 -> colors.evenLevelEvenLit
                                    level % 2 == 0 && i % 2 == 1 -> colors.evenLevelOddLit
                                    level % 2 == 1 && i % 2 == 0 -> colors.oddLevelEvenLit
                                    level % 2 == 1 && i % 2 == 1 -> colors.oddLevelOddLit
                                    else -> null
                                }

                                if (i == assignment.qhead) {
                                    border = Border(3.pt, LineStyle.solid, NamedColor.black)
                                }
                            }

                            LitNode {
                                this.lit = lit
                            }

                            if (assignment.reason(lit.variable) != null) {
                                ClauseNode {
                                    clause = assignment.reason(lit.variable)!!
                                }
                            }

                            if (i == assignment.qhead) {
                                Typography {
                                    sx {
                                        fontStyle = FontStyle.italic
                                        fontSize = 10.pt
                                        margin = 3.pt
                                    }

                                    +"qhead"
                                }
                            }

                            if (i == assignment.qhead) {
                                css {
                                    backgroundColor = rgb(200, 200, 200)
                                }
                                Box {
                                    component = span
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
     */
}