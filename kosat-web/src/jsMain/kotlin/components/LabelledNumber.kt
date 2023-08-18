package components

import mui.material.Box
import mui.system.sx
import react.FC
import react.Props
import react.dom.html.ReactHTML
import web.cssom.AlignItems
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.pt

external interface LabelledNumberProps : Props {
    var label: String
    var value: Int
}

/**
 * Displays a number with a label above it.
 */
val LabelledNumber: FC<LabelledNumberProps> = FC("LabelledNumber") { props ->
    Box {
        sx {
            alignItems = AlignItems.center
            display = Display.flex
            flexDirection = FlexDirection.column
        }

        Box {
            component = ReactHTML.span
            sx {
                fontSize = 10.pt
                marginBottom = (-8).pt
            }

            +props.label
        }

        Box {
            component = ReactHTML.span
            sx {
                fontSize = 24.pt
                marginBottom = (-6).pt
            }

            +props.value.toString()
        }
    }
}
