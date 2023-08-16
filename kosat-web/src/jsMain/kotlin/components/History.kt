package components

import WrapperCommand
import mui.material.Box
import mui.material.ButtonGroup
import mui.system.sx
import react.FC
import react.Props
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

        for (command in solver.history) {
            Box {
                +command.toString()
            }
        }
    }

    ButtonGroup {
        CommandButton {
            command = WrapperCommand.Undo
            +"Undo"
        }
        CommandButton{
            command = WrapperCommand.Redo
            +"Redo"
        }
    }
}