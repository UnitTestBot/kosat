package components

import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import react.useContext
import web.cssom.FontStyle
import web.cssom.pt
import web.cssom.rgb

external interface TrailProps : Props

val TrailNode = FC<TrailProps> { _ ->
    val solver = useContext(cdclWrapperContext)!!
    val assignment = solver.state.inner.assignment

    table {
        tbody {
            for (level in 0..assignment.decisionLevel) {
                tr {
                    key = level.toString()
                    td { +"level $level" }
                    td {
                        for (i in 0 until assignment.trail.size) {
                            val lit = assignment.trail[i]
                            if (assignment.level(lit) != level) continue
                            div {
                                LitNode {
                                    key = lit.toString()
                                    this.lit = lit
                                }
                                if (assignment.reason(lit.variable) != null) {
                                    ClauseNode {
                                        clause = assignment.reason(lit.variable)!!
                                    }
                                }
                                if (i == assignment.qhead) {
                                    css {
                                        backgroundColor = rgb(200, 200, 200)
                                    }
                                    span {
                                        +"qhead"
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
    }
}