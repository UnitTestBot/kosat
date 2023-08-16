package components

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
import react.dom.html.ReactHTML.h3
import react.useContext
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.FlexWrap
import web.cssom.number
import web.cssom.pt

external interface ClauseListProps : Props {
    var clauses: List<Clause>
}

val ClauseList: FC<ClauseListProps> = FC { props ->
    Box {
        sx {
            display = Display.flex
            flexWrap = FlexWrap.wrap
            overflow = auto
        }

        for (clause in props.clauses) {
            ClauseNode {
                key = clause.toString()
                this.clause = clause
            }
        }
    }
}

external interface ClauseDbProps : Props

val ClauseDbNode: FC<ClauseDbProps> = FC {
    val theme = useTheme<Theme>()
    val solver = useContext(cdclWrapperContext)!!

    Box {
        sx {
            display = Display.flex
            flexDirection = FlexDirection.column
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
            }

            Typography {
                sx {
                    color = theme.palette.text.secondary
                    fontSize = 12.pt
                }
                component = h3
                variant = TypographyVariant.subtitle1
                +"Irredundant Clauses"
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
            }

            Typography {
                sx {
                    color = theme.palette.text.secondary
                    fontSize = 12.pt
                }
                component = h3
                variant = TypographyVariant.subtitle1
                +"Redundant Clauses"
            }

            ClauseList {
                clauses = solver.state.inner.db.learnts.filter {
                    !it.deleted
                }
            }
        }
    }
}
