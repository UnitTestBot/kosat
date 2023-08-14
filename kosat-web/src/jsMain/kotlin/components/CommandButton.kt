package components

import SolverCommand
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.button
import react.useContext

external interface CommandButtonProps : PropsWithChildren {
    var command: SolverCommand?
}

val CommandButton = FC<CommandButtonProps> { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!

    val command = props.command
    button {
        disabled = command == null || !solver.canExecute(command)
        onClick = { dispatch(command!!) }
        props.children?.unaryPlus()
    }
}