package components

import mui.material.Box
import mui.system.sx
import react.FC
import react.Props
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.useContext
import web.cssom.Auto.Companion.auto
import web.cssom.number

external interface HistoryProps : Props

val History = FC<HistoryProps> { _ ->
    val solver = useContext(cdclWrapperContext)!!

    Box {
        sx {
            flexGrow = number(1.0)
            overflow = auto
        }

        for (command in solver.state.history) {
            Box {
                +command.toString()
            }
        }
    }

    CommandButton {
        command = SolverCommand.Undo
        +"Undo"
    }
}