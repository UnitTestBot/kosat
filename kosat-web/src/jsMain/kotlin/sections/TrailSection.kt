package sections

import SolverCommand
import bindings.FixedSizeList
import bindings.FixedSizeListItemParams
import cdclWrapperContext
import components.ClauseNode
import components.CommandButton
import components.EagerlyRunButton
import components.IconCommandButton
import components.LitNode
import mui.icons.material.Download
import mui.icons.material.ExpandLess
import mui.icons.material.ExpandMore
import mui.icons.material.RestartAlt
import mui.material.Box
import mui.material.Collapse
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
import react.create
import react.useContext
import react.useEffect
import react.useRef
import react.useState
import web.cssom.AlignItems
import web.cssom.AlignSelf
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.FontWeight
import web.cssom.JustifyContent
import web.cssom.number
import web.cssom.pct
import web.cssom.pt
import web.cssom.rgb
import web.html.HTMLElement

private external interface TrailLevelProps : Props {
    var level: Int
}

/**
 * A level of the trail, when the trail is displayed as a list.
 *
 * If there are too many literals on the trail, a [FixedSizeList] is used
 * instead with a similar representation.
 */
private val TrailLevel: FC<TrailLevelProps> = FC("TrailLevel") { props ->
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

                if (i == assignment.qhead) {
                    Box {
                        sx {
                            alignSelf = AlignSelf.stretch
                            display = Display.flex
                            flexDirection = FlexDirection.column
                            alignItems = AlignItems.end
                        }

                        Typography {
                            variant = TypographyVariant.subtitle2
                            sx {
                                fontSize = 8.pt
                                fontWeight = FontWeight.bolder
                                color = theme.palette.primary.main
                            }
                            +"Propagated up to here"
                        }

                        Box {
                            sx {
                                borderRadius = 2.pt
                                height = 4.pt
                                width = 100.pct
                                backgroundColor = theme.palette.primary.main
                            }
                        }
                    }
                }

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
                        height = 30.pt

                        if (i % 2 == 0) {
                            backgroundColor = rgb(240, 240, 240)
                        }
                    }

                    if (i >= assignment.qhead) {
                        IconCommandButton {
                            Download {}
                            command = SolverCommand.PropagateUpTo(i)
                        }
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
            }
        }
    }
}

/**
 * Section for displaying the trail of the solver, propagating, and
 * backtracking.
 */
val TrailSection: FC<Props> = FC("TrailSection") { _ ->
    val solver = useContext(cdclWrapperContext)!!
    val theme = useTheme<Theme>()
    val assignment = solver.state.inner.assignment
    val listRef = useRef<HTMLElement>(null)

    // FIXME: does not scroll :(
    useEffect(assignment.trail) {
        listRef.current?.scrollTop = listRef.current?.scrollHeight?.toDouble() ?: 0.0
    }

    Box {
        sx {
            display = Display.flex
            gap = 8.pt
            alignItems = AlignItems.center
            overflow = auto
            padding = 8.pt
            margin = (-8).pt
            minHeight = 48.pt
        }

        IconCommandButton {
            Download {}
            command = SolverCommand.PropagateOne
        }

        CommandButton {
            command = SolverCommand.Propagate
            sx {
                flexGrow = number(1.0)
            }
            +"Propagate"
        }

        EagerlyRunButton {
            command = SolverCommand.Propagate
            description = """
                Automatically propagate all literals that can be 
                propagated every time the assignment changes.
            """.trimIndent()
        }
    }

    if (assignment.trail.size < 30) {
        List {
            ref = listRef

            sx {
                overflow = auto
            }

            for (level in 0..assignment.decisionLevel) {
                TrailLevel {
                    this.level = level
                }
            }
        }
    } else {
        val levelZeroIsEmpty = assignment.trail.getOrNull(0)?.let {
            assignment.level(it) != 0
        } ?: true

        FixedSizeList {
            ref = listRef

            width = 300
            height = 600
            itemSize = 42
            itemCount = assignment.trail.size + if (levelZeroIsEmpty) 1 else 0
            children = { params: FixedSizeListItemParams ->
                val litIndex = params.index - if (levelZeroIsEmpty) 1 else 0
                val index = params.index
                val lit = assignment.trail.getOrNull(litIndex)
                val level = lit?.let { assignment.level(it) } ?: 0
                val reason = lit?.let { assignment.reason(it.variable) }

                val firstInLevel = index == 0 || level > 0 && reason == null

                Box.create {
                    sx {
                        display = Display.flex
                        alignItems = AlignItems.center
                        gap = 8.pt
                    }

                    style = params.style

                    if (firstInLevel) {
                        IconCommandButton {
                            RestartAlt {}
                            command = SolverCommand.Backtrack(level)
                        }

                        Typography {
                            variant = TypographyVariant.subtitle2
                            sx {
                                fontSize = 14.pt
                                fontWeight = FontWeight.bolder
                                color = theme.palette.primary.main
                            }
                            +"Level $level"
                        }
                    }

                    if (lit != null) {
                        LitNode {
                            this.lit = lit
                        }
                    }

                    if (reason != null) {
                        ClauseNode {
                            clause = reason
                            scale = 0.8
                        }
                    }
                }
            }
        }
    }

    if (assignment.qhead == assignment.trail.size) {
        Box {
            sx {
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
            }

            Typography {
                variant = TypographyVariant.subtitle1
                sx {
                    fontWeight = FontWeight.bolder
                    color = theme.palette.text.secondary
                }
                +"fully propagated"
            }
        }
    }
}