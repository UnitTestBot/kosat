package components

import WrapperCommand
import mui.icons.material.CheckBox
import mui.icons.material.CheckBoxOutlineBlank
import mui.icons.material.CheckBoxOutlined
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.IconButton
import mui.material.ListItem
import mui.material.ListItemIcon
import mui.material.ListItemText
import mui.material.Size
import mui.material.Stack
import mui.material.Tooltip
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import react.FC
import react.Props
import react.PropsWithChildren
import react.create
import react.dom.html.ReactHTML.span
import react.useContext
import web.cssom.AlignItems
import web.cssom.Display
import web.cssom.number
import web.cssom.pt

external interface ButtonRequirementsProps : PropsWithChildren {
    var command: WrapperCommand
}

val ButtonRequirements: FC<ButtonRequirementsProps> = FC { props ->
    val solver = useContext(cdclWrapperContext)!!

    val requirements = solver.requirementsFor(props.command)

    Tooltip {
        disableInteractive = true

        title = Stack.create {
            spacing = responsive(4.pt)

            requirements.forEach { requirement ->
                if (!requirement.obvious || !requirement.fulfilled) {
                    Box {
                        sx {
                            display = Display.flex
                            alignItems = AlignItems.center
                            gap = 8.pt
                        }

                        if (requirement.fulfilled) {
                            CheckBox {}
                        } else {
                            CheckBoxOutlineBlank {}
                        }

                        Typography {
                            sx {
                                flexGrow = number(1.0)
                            }

                            variant = TypographyVariant.body2

                            +requirement.message
                        }
                    }
                }
            }
        }

        Box {
            component = span
            +props.children
        }
    }
}

external interface CommandButtonProps : PropsWithChildren {
    var command: WrapperCommand
    var size: Size?
}

val CommandButton = FC<CommandButtonProps> { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!

    val command = props.command
    ButtonRequirements {
        this.command = command

        Button {
            size = props.size
            variant = ButtonVariant.contained
            disabled = !solver.canExecute(command)
            onClick = { dispatch(command) }
            +props.children
        }
    }
}

val IconCommandButton: FC<CommandButtonProps> = FC { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!

    val command = props.command
    ButtonRequirements {
        this.command = command

        IconButton {
            size = props.size
            disabled = !solver.canExecute(command)
            onClick = { dispatch(command) }
            +props.children
        }
    }
}
