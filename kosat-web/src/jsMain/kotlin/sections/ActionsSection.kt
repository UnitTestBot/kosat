package sections

import SolverCommand
import cdclDispatchContext
import cdclWrapperContext
import components.CommandButton
import js.core.jso
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Dialog
import mui.material.Paper
import mui.material.Stack
import mui.material.Tooltip
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import react.FC
import react.Fragment
import react.Props
import react.create
import react.dom.html.ReactHTML.span
import react.router.useNavigate
import react.useContext
import react.useState
import web.cssom.AlignSelf
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.min
import web.cssom.number
import web.cssom.pct
import web.cssom.pt
import web.cssom.vh

/**
 * Section of the visualizer that contains primary buttons to control the
 * solver, which is currently only the "Search" button, and "Next action"
 * button.
 */
val ActionsSection: FC<Props> = FC("ActionsSection") {
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!
    val navigate = useNavigate()
    var inputShown by useState(false);

    Dialog {
        open = inputShown
        onClose = { _, _ -> inputShown = false }

        Paper {
            elevation = 3

            sx {
                padding = 16.pt
                minWidth = 300.pt
                maxHeight = min(500.pt, 80.vh)
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            Typography {
                sx {
                    alignSelf = AlignSelf.center
                }
                variant = TypographyVariant.h2
                +"Input"
            }

            InputSection {}
        }
    }

    Stack {
        sx {
            height = 100.pct
            overflow = auto
        }

        spacing = responsive(4.pt)

        CommandButton {
            command = SolverCommand.Search
            +"Search"
        }

        Tooltip {
            disableInteractive = true

            title = Fragment.create {
                Typography {
                    +"The next action is: "
                    +(solver.nextAction?.description ?: "none")
                }

                Typography {
                    +"""
                        This is what the most basic CDCL solver with VSIDS 
                        variable selector and without restarts would do next.
                    """.trimIndent()
                }

                Typography {
                    dangerouslySetInnerHTML = jso {
                        // language=html
                        __html = """
                            Hotkey: <kbd>Space</kbd>
                        """.trimIndent()
                    }
                }
            }

            Box {
                component = span
                sx {
                    width = 100.pct
                }
                Button {
                    sx {
                        width = 100.pct
                    }
                    variant = ButtonVariant.contained
                    disabled = solver.nextAction == null
                    onClick = {
                        solver.nextAction?.let { dispatch(it) }
                    }
                    +"Next CDCL action"
                }
            }
        }

        Box {
            sx { flexGrow = number(1.0) }
        }

        Button {
            variant = ButtonVariant.outlined
            onClick = { inputShown = true }
            +"Edit input"
        }

        Button {
            variant = ButtonVariant.outlined
            onClick = {
                navigate("/")
            }
            +"Back to the landing page..."
        }
    }
}
