package components

import SolverCommand
import WrapperCommand
import cdclDispatchContext
import cdclWrapperContext
import mui.icons.material.CheckBox
import mui.icons.material.CheckBoxOutlineBlank
import mui.icons.material.SmartToy
import mui.material.Box
import mui.material.Button
import mui.material.ButtonColor
import mui.material.ButtonVariant
import mui.material.IconButton
import mui.material.IconButtonColor
import mui.material.Size
import mui.material.Stack
import mui.material.ToggleButton
import mui.material.ToggleButtonColor
import mui.material.Tooltip
import mui.material.Typography
import mui.material.styles.Theme
import mui.material.styles.TypographyVariant
import mui.material.styles.useTheme
import mui.system.PropsWithSx
import mui.system.responsive
import mui.system.sx
import react.FC
import react.PropsWithChildren
import react.create
import react.dom.html.ReactHTML.span
import react.useContext
import web.cssom.AlignItems
import web.cssom.BoxShadow
import web.cssom.Display
import web.cssom.number
import web.cssom.pct
import web.cssom.pt

external interface ButtonRequirementsProps : PropsWithChildren {
    var command: WrapperCommand
    var descriptionOverride: String?
}

val ButtonRequirements: FC<ButtonRequirementsProps> = FC { props ->
    val solver = useContext(cdclWrapperContext)!!

    val requirements = solver.requirementsFor(props.command)

    Tooltip {
        disableInteractive = true

        title = Stack.create {
            Typography {
                variant = TypographyVariant.body2
                +(props.descriptionOverride ?: props.command.description)
            }

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

                            variant = TypographyVariant.caption

                            +requirement.message
                        }
                    }
                }
            }

            if (solver.nextAction == props.command) {
                Typography {
                    sx {
                        flexGrow = number(1.0)
                        color = Colors.truth.main
                    }

                    variant = TypographyVariant.caption

                    +"This is what a simple CDCL solver would do next"
                }
            }
        }

        +props.children
    }
}

external interface CommandButtonProps : PropsWithChildren, PropsWithSx {
    var command: WrapperCommand
    var size: Size?
    var descriptionOverride: String?
}

val CommandButton = FC<CommandButtonProps> { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!

    val command = props.command
    ButtonRequirements {
        this.command = command
        descriptionOverride = props.descriptionOverride

        Box {
            component = span
            sx = props.sx

            Button {
                sx {
                    width = 100.pct
                    if (command == solver.nextAction) {
                        boxShadow = BoxShadow(0.pt, 0.pt, 4.pt, 4.pt, Colors.truth.light)
                    }
                }

                size = props.size
                variant = ButtonVariant.contained
                disabled = !solver.canExecute(command)
                onClick = { dispatch(command) }
                +props.children
            }
        }
    }
}

val IconCommandButton: FC<CommandButtonProps> = FC { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!

    val command = props.command
    ButtonRequirements {
        this.command = command
        descriptionOverride = props.descriptionOverride

        Box {
            component = span
            sx = props.sx

            IconButton {
                sx {
                    if (command == solver.nextAction) {
                        boxShadow = BoxShadow(0.pt, 0.pt, 4.pt, 4.pt, Colors.truth.light)
                    }
                }
                size = props.size

                disabled = !solver.canExecute(command)
                color = IconButtonColor.primary
                onClick = { dispatch(command) }
                +props.children
            }
        }
    }
}

external interface EagerlyRunButtonProps : PropsWithSx {
    var command: SolverCommand
    var description: String
}

val EagerlyRunButton: FC<EagerlyRunButtonProps> = FC { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!
    val selected = solver.runEagerly.contains(props.command)

    Tooltip {
        disableInteractive = true

        title = Box.create {
            Typography {
                variant = TypographyVariant.body2
                +props.description
            }
        }

        Box {
            component = span
            sx = props.sx

            ToggleButton {
                this.selected = selected
                size = Size.small
                color = ToggleButtonColor.primary
                value = "placeholder"

                onChange = { _, _ ->
                    dispatch(WrapperCommand.SetRunEagerly(props.command, !selected))
                }

                SmartToy {}
            }
        }
    }
}
