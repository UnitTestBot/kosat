package components

import mui.material.Box
import react.FC
import react.Props
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.useContext

external interface HistoryProps : Props

val History = FC<HistoryProps> { _ ->
    val solver = useContext(cdclWrapperContext)!!

    Box {
        for (command in solver.state.history) {
            Box {
                +command.toString()
            }
        }
    }
}