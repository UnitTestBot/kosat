package components

import Colors
import cdclWrapperContext
import mui.material.Box
import mui.material.Tooltip
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import org.kosat.Clause
import org.kosat.LBool
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.span
import react.useContext
import web.cssom.AlignItems
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.FontWeight
import web.cssom.JustifyContent
import web.cssom.array
import web.cssom.pt
import web.cssom.scale

external interface ClauseProps : Props {
    var clause: Clause
    var scale: Double?
}

/**
 * Component for displaying a single clause.
 */
val ClauseNode: FC<ClauseProps> = FC("ClauseNode") { props ->
    val clause = props.clause
    val solver = useContext(cdclWrapperContext)!!

    val values = clause.lits.map {
        if (!solver.state.inner.assignment.isActive(it)) {
            LBool.UNDEF
        } else {
            solver.state.inner.assignment.value(it)
        }
    }

    val satisfied = values.any { it == LBool.TRUE }
    val falsified = values.all { it == LBool.FALSE }
    val almostFalsified = values.count { it == LBool.FALSE } == values.size - 1
    val color = when {
        satisfied -> Colors.truth.main
        falsified -> Colors.falsity.main
        almostFalsified -> Colors.almostFalsity.main
        else -> Colors.bg.main
    }

    val maxLitsToShow = 8

    Tooltip {
        disableInteractive = true
        title = Box.create {
            sx {
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
            }
            Typography {
                variant = TypographyVariant.subtitle2
                +"Clause of ${clause.lits.size} literals"
            }
            if (clause.lits.size > maxLitsToShow) {
                Typography {
                    variant = TypographyVariant.subtitle2
                    sx {
                        fontWeight = FontWeight.bold
                    }
                    +"(only the first $maxLitsToShow are shown)"
                    br {}
                    +"Full clause:"
                }
                Typography {
                    variant = TypographyVariant.subtitle1
                    +clause.lits.map { it.toDimacs() }.joinToString(" ")
                }

            }
            if (satisfied) {
                val satLit = clause.lits.first { solver.state.inner.assignment.value(it) == LBool.TRUE }
                Typography {
                    variant = TypographyVariant.subtitle2
                    sx {
                        fontWeight = FontWeight.bold
                        this.color = Colors.truth.light
                    }
                    +"Satisfied because "
                    Box {
                        component = span
                        sx {
                            transform = scale(0.4)
                            padding = array(0.pt, 4.pt)
                        }
                        LitNode {
                            lit = satLit
                            showTooltip = false
                        }
                    }
                    +"is assigned to true."
                }
            } else if (almostFalsified) {
                val lastUnassigned = clause.lits.first { solver.state.inner.assignment.value(it) == LBool.UNDEF }
                Typography {
                    variant = TypographyVariant.subtitle2
                    sx {
                        fontWeight = FontWeight.bold
                        this.color = Colors.almostFalsity.light
                    }
                    +"Almost falsified. "
                    Box {
                        component = span
                        sx {
                            transform = scale(0.4)
                            padding = array(0.pt, 4.pt)
                        }
                        LitNode {
                            lit = lastUnassigned
                            showTooltip = false
                        }
                    }
                    +"is the only unassigned literal. It will be assigned the next propagation."
                }
            } else if (falsified) {
                Typography {
                    variant = TypographyVariant.subtitle2
                    sx {
                        fontWeight = FontWeight.bold
                        this.color = Colors.falsity.light
                    }
                    +"Falsified. All literals are assigned to false."
                }
            }
        }

        Box {
            component = span
            sx {
                display = Display.inlineFlex
                height = 30.pt
                borderRadius = 15.pt
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                backgroundColor = color
                padding = 3.pt
                margin = 3.pt
                if (props.scale != null) {
                    transform = scale(props.scale!!)
                }
            }

            for (lit in clause.lits.take(maxLitsToShow)) {
                LitNode {
                    key = lit.toString()
                    this.lit = lit
                    showTooltip = false
                }
            }

            if (clause.lits.size > maxLitsToShow) {
                Typography {
                    variant = TypographyVariant.subtitle2
                    sx {
                        fontSize = 16.pt
                        fontWeight = FontWeight.bolder
                        paddingLeft = 4.pt
                    }
                    +"..."
                }
            }
        }
    }
}