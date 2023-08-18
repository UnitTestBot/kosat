package sections

import WrapperCommand
import cdclDispatchContext
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
import react.useState
import web.cssom.FontFamily
import web.cssom.Overflow
import web.cssom.number
import web.cssom.pct

/**
 * A section dedicated to inputting a CNF.
 */
val InputSection: FC<Props> = FC("InputSection") {
    val dispatch = useContext(cdclDispatchContext)!!
    var error by useState<String?>(null)
    var errorShown by useState(false)

    var request by useState(
        """
            p cnf 9 13
            -1 2 0
            -1 3 0
            -2 -3 4 0
            -4 5 0
            -4 6 0
            -5 -6 7 0
            -7 1 0
            1 4 7 8 0
            -1 -4 -7 -8 0
            1 4 7 9 0
            -1 -4 -7 -9 0
            8 9 0
            -8 -9 0
        """.trimIndent()
    )

    Box {
        sx {
            overflowY = Overflow.scroll
            flexGrow = number(1.0)
        }

        TextField {
            sx {
                width = 100.pct
            }

            inputProps = jso {
                style = jso {
                    fontFamily = FontFamily.monospace
                }
            }

            value = request
            multiline = true
            onChange = { event -> request = event.target.asDynamic().value as String }
        }
    }

    Button {
        +"Create Solver"
        variant = ButtonVariant.contained
        onClick = {
            run {
                val cnf: CNF
                try {
                    cnf = CNF.fromString(request)
                } catch (e: Exception) {
                    error = e.message
                    errorShown = true
                    return@run
                }
                dispatch(WrapperCommand.Recreate(cnf))
            }
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