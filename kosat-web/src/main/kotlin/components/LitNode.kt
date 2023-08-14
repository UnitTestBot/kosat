package components

import SolverCommand
import csstype.AlignItems
import csstype.Border
import csstype.Cursor
import csstype.Display
import csstype.FontStyle
import csstype.JustifyContent
import csstype.LineStyle
import csstype.Scale
import csstype.pct
import csstype.pt
import csstype.rgb
import mui.material.Box
import mui.material.Tooltip
import org.kosat.LBool
import org.kosat.Lit
import org.kosat.get
import react.FC
import react.Props
import react.create
import react.css.css
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.useContext

external interface LitProps : Props {
    @Suppress("INLINE_CLASS_IN_EXTERNAL_DECLARATION_WARNING")
    var lit: Lit
}

val LitNode = FC<LitProps> { props ->
    val solver = useContext(cdclWrapperContext)
    val lit = props.lit

    val data = solver.state.inner.assignment.varData[lit.variable]
    val value = if (!data.active) {
        LBool.UNDEF
    } else {
        solver.state.inner.assignment.value(lit)
    }
    val level0 = data.level == 0

    val fill = when {
        value == LBool.TRUE && level0 -> rgb(0, 255, 0)
        value == LBool.FALSE && level0 -> rgb(255, 0, 0)
        !data.active -> rgb(128, 128, 128)
        data.frozen -> rgb(128, 128, 255)
        else -> rgb(200, 200, 200)
    }

    val borderColor = when {
        value == LBool.TRUE && !level0 -> rgb(0, 255, 0)
        value == LBool.FALSE && !level0 -> rgb(255, 0, 0)
        else -> null
    }

    Tooltip {
        title = ReactHTML.span.create {
            if (lit.isPos) {
                div { +"Positive literal" }
            } else {
                div { +"Negative literal" }
            }

            if (!data.active) div { +"Inactive (eliminated)" }
            if (data.frozen) div { +"Frozen" }
            if (value == LBool.TRUE) div { +"Assigned to TRUE at level ${data.level}" }
            if (value == LBool.FALSE) div { +"Assigned to FALSE at level ${data.level}" }
            if (data.reason != null) div {
                +"Reason:"
                Box {
                    css {
                        scale = Scale(0.3)
                    }
                    // FIXME: this is a hack
                    (ClauseNodeFn()) {
                        clause = data.reason!!
                    }
                }
            }

            div {
                CommandButton {
                    command = SolverCommand.Enqueue(lit)
                    +"Enqueue"
                }
            }

            div {
                CommandButton {
                    command = SolverCommand.Enqueue(lit.neg)
                    +"Enqueue negation"
                }
            }
        }

        span {
            css {
                width = if (borderColor == null) 24.pt else 18.pt
                height = if (borderColor == null) 24.pt else 18.pt
                borderRadius = 100.pct
                display = Display.inlineFlex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                backgroundColor = fill
                cursor = Cursor.pointer
                fontStyle = if (data.frozen) FontStyle.italic else null
                border = borderColor?.let { Border(3.pt, LineStyle.solid, it) }
            }

            onMouseOver
            +if (lit.isPos) {
                "${lit.variable.index + 1}"
            } else {
                "-${lit.variable.index + 1}"
            }
        }
    }
}