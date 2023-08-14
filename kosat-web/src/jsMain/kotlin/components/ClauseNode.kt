package components

import emotion.react.css
import org.kosat.Clause
import org.kosat.LBool
import react.FC
import react.Props
import react.dom.html.ReactHTML.span
import react.useContext
import web.cssom.AlignItems
import web.cssom.Display
import web.cssom.JustifyContent
import web.cssom.Margin
import web.cssom.pt
import web.cssom.rgb

external interface ClauseProps : Props {
    var clause: Clause
}

val ClauseNode = FC<ClauseProps> { props ->
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
        satisfied -> rgb(0, 200, 0)
        falsified -> rgb(200, 0, 0)
        almostFalsified -> rgb(100, 0, 0)
        else -> rgb(150, 150, 150)
    }

    span {
        css {
            display = Display.inlineFlex
            height = 24.pt
            borderRadius = 15.pt
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            backgroundColor = color
            padding = 3.pt
            margin = Margin(0.pt, 3.pt)
        }
        for (lit in clause.lits) {
            LitNode {
                key = lit.toString()
                this.lit = lit
            }
        }
    }
}

fun ClauseNodeFn(): FC<ClauseProps> = ClauseNode