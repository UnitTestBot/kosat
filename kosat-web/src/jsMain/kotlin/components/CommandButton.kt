package components

import CdclWrapper
import Colors
import SolverCommand
import WrapperCommand
import cdclDispatchContext
import cdclWrapperContext
import mui.icons.material.Check
import mui.icons.material.CheckBox
import mui.icons.material.CheckBoxOutlineBlank
import mui.icons.material.Close
import mui.icons.material.SmartToy
import mui.material.Box
import mui.material.Button
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
import web.cssom.FontWeight
import web.cssom.number
import web.cssom.pct
import web.cssom.pt

private external interface ButtonRequirementsProps : PropsWithChildren {
    var command: WrapperCommand
    var descriptionOverride: String?
}

/**
 * Displays a tooltip with a description of a command, and the requirements
 * to run it with a checkbox for each requirement.
 */
private val ButtonRequirements: FC<ButtonRequirementsProps> = FC("ButtonRequirements") { props ->
    val solver = useContext(cdclWrapperContext)!!
    val theme = useTheme<Theme>()

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
                            if (!requirement.fulfilled) {
                                color = Colors.falsity.light
                                fontWeight = FontWeight.bold
                            }
                        }

                        if (requirement.fulfilled) {
                            Check {}
                        } else {
                            Close {}
                        }

                        Typography {
                            sx {
                                flexGrow = number(1.0)
                                if (!requirement.fulfilled) {
                                    fontWeight = FontWeight.bold
                                }
                            }

                            variant = TypographyVariant.caption

                            +requirement.message
                        }
                    }
                }
            }

            if (!requirements.all { it.fulfilled }) {
                if (requirements.all { it.fulfilled || it.wontCauseEffectIfIgnored }) {
                    Typography {
                        variant = TypographyVariant.caption

                        +"Technically, this can be executed, but it won't have any effect."
                    }
                } else {
                    Typography {
                        variant = TypographyVariant.caption

                        +"This cannot be executed because not all requirements are fulfilled."
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

/**
 * Displays a button that dispatches a command when clicked, and automatically
 * disables the button when the command cannot be executed.
 *
 * The tooltip is attached to the button, and displays the command description
 * and the requirements to run it.
 *
 * @see IconCommandButton
 * @see WrapperCommand
 */
val CommandButton: FC<CommandButtonProps> = FC("CommandButton") { props ->
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

/**
 * Displays an icon button that dispatches a command when clicked, and
 * automatically disables the button when the command cannot be executed.
 *
 * The tooltip is attached to the button, and displays the command description
 * and the requirements to run it.
 *
 * @see CommandButton
 * @see WrapperCommand
 */
val IconCommandButton: FC<CommandButtonProps> = FC("IconCommandButton") { props ->
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

/**
 * Displays a toggle button that toggles eagerly running a command when clicked.
 *
 * @see CdclWrapper.commandsToRunEagerly
 */
val EagerlyRunButton: FC<EagerlyRunButtonProps> = FC("EagerlyRunButton") { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!
    val selected = solver.commandsToRunEagerly.contains(props.command)

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
