package components

import SolverCommand
import mui.material.Box
import mui.material.Tooltip
import mui.material.Typography
import mui.system.sx
import org.kosat.LBool
import org.kosat.Lit
import org.kosat.get
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.span
import react.useContext
import web.cssom.AlignItems
import web.cssom.Border
import web.cssom.Cursor
import web.cssom.Display
import web.cssom.FontStyle
import web.cssom.JustifyContent
import web.cssom.LineStyle
import web.cssom.pct
import web.cssom.pt

external interface LitProps : Props {
    @Suppress("INLINE_CLASS_IN_EXTERNAL_DECLARATION_WARNING")
    var lit: Lit
}

val LitNode: FC<LitProps> = FC { props ->
    val solver = useContext(cdclWrapperContext)!!

    val lit = props.lit

    val data = solver.state.inner.assignment.varData[lit.variable]
    val value = if (!data.active) {
        LBool.UNDEF
    } else {
        solver.state.inner.assignment.value(lit)
    }
    val level0 = data.level == 0

    val fill = when {
        value == LBool.TRUE && level0 -> Colors.truth
        value == LBool.FALSE && level0 -> Colors.falsity
        !data.active -> Colors.inactive
        data.frozen -> Colors.frozen
        else -> Colors.bg
    }

    val borderColor = when {
        value == LBool.TRUE && !level0 -> Colors.truth.main
        value == LBool.FALSE && !level0 -> Colors.falsity.main
        else -> null
    }

    Tooltip {
        title = Box.create {
            component = span

            if (lit.isPos) {
                Box { +"Positive literal" }
            } else {
                Box { +"Negative literal" }
            }

            if (!data.active) Box { +"Inactive (eliminated)" }
            if (data.frozen) Box { +"Frozen" }
            if (value == LBool.TRUE) Box { +"Assigned to TRUE at level ${data.level}" }
            if (value == LBool.FALSE) Box { +"Assigned to FALSE at level ${data.level}" }
            if (data.reason != null) Box {
                +"Reason:"
                Box {
                    ClauseNode {
                        clause = data.reason!!
                    }
                }
            }

            Box {
                CommandButton {
                    command = SolverCommand.Enqueue(lit)
                    +"Enqueue"
                }
            }

            Box {
                CommandButton {
                    command = SolverCommand.Enqueue(lit.neg)
                    +"Enqueue negation"
                }
            }
        }

        Box {
            component = span

            sx {
                width = 24.pt
                height = 24.pt
                borderRadius = 100.pct
                display = Display.inlineFlex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                backgroundColor = fill.main
                cursor = Cursor.pointer
                fontStyle = if (data.frozen) FontStyle.italic else null
                border = borderColor?.let { Border(3.pt, LineStyle.solid, it) }
            }

            Typography {
                sx {
                    fontSize = 14.pt
                    color = fill.contrastText
                }

                +if (lit.isPos) {
                    "${lit.variable.index + 1}"
                } else {
                    "-${lit.variable.index + 1}"
                }
            }
        }
    }
}