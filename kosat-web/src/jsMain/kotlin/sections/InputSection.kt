package sections

import WrapperCommand
import cdclDispatchContext
import cdclWrapperContext
import js.core.jso
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Dialog
import mui.material.DialogContent
import mui.material.TextField
import mui.system.sx
import org.kosat.cnf.CNF
import react.FC
import react.Props
import react.dom.onChange
import react.useContext
import react.useEffectOnce
import react.useRef
import react.useState
import web.cssom.FontFamily
import web.cssom.Overflow
import web.cssom.number
import web.cssom.pct
import web.html.HTMLDivElement

/**
 * A section dedicated to inputting a CNF.
 */
val InputSection: FC<Props> = FC("InputSection") {
    val dispatch = useContext(cdclDispatchContext)!!
    val solver = useContext(cdclWrapperContext)!!
    var error by useState<String?>(null)
    var errorShown by useState(false)
    val inputField = useRef<HTMLDivElement>(null)

    var problem by useState(solver.problem.toString(includeHeader = true))

    fun recreate() {
        val cnf: CNF
        try {
            cnf = CNF.fromString(problem)
        } catch (e: Exception) {
            error = e.message
            errorShown = true
            return
        }
        dispatch(WrapperCommand.Recreate(cnf))
    }

    useEffectOnce {
        recreate()
    }

    // This event handler serves two purposes:
    // First, it allows the user to press Ctrl+Enter to create a solver.
    // Second, it prevents the input events from propagating further, causing
    // key presses to be registered as input events in the other sections.
    // This is important for time traveling, where arrow keys are used to
    // navigate the history.
    inputField.current?.let {
        it.onkeydown = { event ->
            if (event.ctrlKey && event.key == "Enter") {
                recreate()
            }

            event.stopPropagation()
        }
    }

    Box {
        sx {
            overflowY = Overflow.scroll
            flexGrow = number(1.0)
        }

        TextField {
            ref = inputField
            minRows = 14

            sx {
                width = 100.pct
            }

            inputProps = jso {
                style = jso {
                    fontFamily = FontFamily.monospace
                }
            }

            value = problem
            multiline = true
            onChange = { event -> problem = event.target.asDynamic().value as String }
        }
    }

    Button {
        +"Start"
        variant = ButtonVariant.contained
        onClick = {
            recreate()
        }
    }

    Dialog {
        open = errorShown
        onClose = { _, _ -> errorShown = false }

        DialogContent {
            +"Parsing error: $error"
        }
    }
}