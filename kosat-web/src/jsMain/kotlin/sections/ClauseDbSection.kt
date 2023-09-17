package sections

import bindings.FixedSizeList
import bindings.FixedSizeListItemParams
import cdclWrapperContext
import components.ClauseNode
import components.CopyButton
import mui.material.Box
import mui.material.Paper
import mui.material.Typography
import mui.material.styles.Theme
import mui.material.styles.TypographyVariant
import mui.material.styles.useTheme
import mui.system.sx
import org.kosat.Clause
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h3
import react.useContext
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.FlexWrap
import web.cssom.number
import web.cssom.pct
import web.cssom.pt

private external interface ClauseListProps : Props {
    var clauses: List<Clause>
}

/**
 * Component for displaying a list of clauses. Falls back to a fixed size list
 * if there are too many clauses.
 */
private val ClauseList: FC<ClauseListProps> = FC("ClauseList") { props ->
    Box {
        sx {
            display = Display.flex
            flexWrap = FlexWrap.wrap
            overflow = auto
            minWidth = 200.pt
        }

        if (props.clauses.size < 20) {
            for (clause in props.clauses) {
                ClauseNode {
                    this.clause = clause
                }
            }
        } else {
            FixedSizeList {
                children = { params: FixedSizeListItemParams ->
                    val clause = props.clauses[params.index]
                    div.create {
                        style = params.style
                        ClauseNode {
                            this.clause = clause
                        }
                    }
                }
                height = 200
                width = 400
                itemSize = 42
                itemCount = props.clauses.size
            }
        }
    }
}

/**
 * Section of the visualizer for displaying the clause database.
 */
val ClauseDbSection: FC<Props> = FC("ClauseDbSection") {
    val theme = useTheme<Theme>()
    val solver = useContext(cdclWrapperContext)!!

    Box {
        sx {
            display = Display.flex
            flexDirection = FlexDirection.row
            gap = 8.pt
            flexGrow = number(1.0)
            overflow = auto
            margin = (-8).pt
            padding = 8.pt
        }

        Paper {
            elevation = 3

            sx {
                padding = 8.pt
                flexGrow = number(1.0)
                minWidth = 30.pct
                overflow = auto
            }

            Typography {
                sx {
                    color = theme.palette.text.secondary
                    fontSize = 12.pt
                }
                component = h3
                variant = TypographyVariant.subtitle1
                +"Irredundant Clauses"
                CopyButton {
                    lazyText = {
                        solver.state.inner.db.clauses.filter {
                            !it.deleted
                        }.joinToString("\n") {
                            it.lits.joinToString(" ", postfix = " 0") { lit ->
                                lit.toDimacs().toString()
                            }
                        }
                    }
                }
            }

            ClauseList {
                clauses = solver.state.inner.db.clauses.filter {
                    !it.deleted
                }
            }
        }

        Paper {
            elevation = 3
            sx {
                padding = 8.pt
                flexGrow = number(1.0)
                minWidth = 30.pct
                overflow = auto
            }

            Typography {
                sx {
                    color = theme.palette.text.secondary
                    fontSize = 12.pt
                }
                component = h3
                variant = TypographyVariant.subtitle1
                +"Redundant Clauses"
                CopyButton {
                    lazyText = {
                        solver.state.inner.db.learnts.filter {
                            !it.deleted
                        }.joinToString("\n") {
                            it.lits.joinToString(" ", postfix = " 0") { lit ->
                                lit.toDimacs().toString()
                            }
                        }
                    }
                }
            }

            ClauseList {
                clauses = solver.state.inner.db.learnts.filter {
                    !it.deleted
                }
            }
        }
    }
}
