package sections

import SolverCommand
import cdclDispatchContext
import cdclWrapperContext
import components.CommandButton
import js.core.jso
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Stack
import mui.material.Tooltip
import mui.material.Typography
import mui.system.responsive
import mui.system.sx
import react.FC
import react.Fragment
import react.Props
import react.create
import react.dom.html.ReactHTML.span
import react.router.useNavigate
import react.useContext
import web.cssom.number
import web.cssom.pct
import web.cssom.pt

/**
 * Section of the visualizer that contains primary buttons to control the
 * solver, which is currently only the "Search" button, and "Next action"
 * button.
 */
val ActionsSection: FC<Props> = FC("ActionsSection") {
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!
    val navigate = useNavigate()

    Stack {
        sx {
            height = 100.pct
        }

        spacing = responsive(8.pt)

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
            onClick = {
                navigate("/")
            }
            +"Back to the landing page..."
        }
    }
}
