package components

import mui.material.Box
import mui.system.sx
import org.kosat.Clause
import org.kosat.LBool
import react.FC
import react.Props
import react.dom.html.ReactHTML.span
import react.useContext
import web.cssom.AlignItems
import web.cssom.Border
import web.cssom.Display
import web.cssom.JustifyContent
import web.cssom.LineStyle
import web.cssom.NamedColor
import web.cssom.pt

external interface ClauseProps : Props {
    var clause: Clause
}

val ClauseNode: FC<ClauseProps> = FC { props ->
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
        satisfied -> Colors.truth.light
        falsified -> Colors.falsity.light
        almostFalsified -> Colors.almostFalsity.light
        else -> Colors.bg.light
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
            border = Border(1.pt, LineStyle.solid, NamedColor.black)
        }

        for (lit in clause.lits) {
            LitNode {
                key = lit.toString()
                this.lit = lit
            }
        }
    }
}