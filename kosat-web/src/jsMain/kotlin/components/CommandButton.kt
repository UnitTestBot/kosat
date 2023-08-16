package components

import WrapperCommand
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.IconButton
import mui.material.Size
import react.FC
import react.PropsWithChildren
import react.useContext

external interface CommandButtonProps : PropsWithChildren {
    var command: WrapperCommand?
    var size: Size?
}

val CommandButton = FC<CommandButtonProps> { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!

    val command = props.command
    Button {
        size = props.size
        variant = ButtonVariant.contained
        disabled = command == null || !solver.canExecute(command)
        onClick = { dispatch(command!!) }
        props.children?.unaryPlus()
    }
}

val IconCommandButton: FC<CommandButtonProps> = FC { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!

    val command = props.command
    IconButton {
        size = props.size
        disabled = command == null || !solver.canExecute(command)
        onClick = { dispatch(command!!) }
        props.children?.unaryPlus()
    }
}
