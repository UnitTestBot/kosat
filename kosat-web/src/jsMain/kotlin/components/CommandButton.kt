package components

import SolverCommand
import mui.material.Button
import mui.material.ButtonVariant
import react.FC
import react.PropsWithChildren
import react.useContext

external interface CommandButtonProps : PropsWithChildren {
    var command: SolverCommand?
}

val CommandButton = FC<CommandButtonProps> { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!

    val command = props.command
    Button {
        variant = ButtonVariant.contained
        disabled = command == null || !solver.canExecute(command)
        onClick = { dispatch(command!!) }
        props.children?.unaryPlus()
    }
}