package components

import WrapperCommand
import js.core.jso
import mui.material.Box
import mui.material.TextField
import mui.system.sx
import org.kosat.cnf.CNF
import react.FC
import react.Props
import react.dom.onChange
import react.useState
import web.cssom.Auto
import web.cssom.FontFamily
import web.cssom.Length
import web.cssom.Overflow
import web.cssom.number
import web.cssom.pct
import web.cssom.pt

external interface InputProps : Props

val InputNode: FC<InputProps> = FC {
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

    val cnf: CNF?

    try {
        cnf = CNF.fromString(request)
    } finally {
    }

    if (cnf != null) {
        CommandButton {
            +"Recreate"
            command = WrapperCommand.Recreate(cnf)
        }
    }
}