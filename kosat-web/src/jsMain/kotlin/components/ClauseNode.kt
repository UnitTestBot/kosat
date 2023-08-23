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
import react.dom.html.ReactHTML.span
import react.useContext
import web.cssom.AlignItems
import web.cssom.Display
import web.cssom.FontWeight
import web.cssom.JustifyContent
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

        for (lit in clause.lits.take(8)) {
            LitNode {
                key = lit.toString()
                this.lit = lit
            }
        }

        if (clause.lits.size > 8) {
            Tooltip {
                Typography {
                    variant = TypographyVariant.subtitle2
                    sx {
                        fontSize = 16.pt
                        fontWeight = FontWeight.bolder
                        paddingLeft = 4.pt
                    }
                    +"..."
                }

                title = Box.create {
                    +clause.lits.map { it.toDimacs() }.joinToString(separator = " ")
                }
            }
        }
    }
}