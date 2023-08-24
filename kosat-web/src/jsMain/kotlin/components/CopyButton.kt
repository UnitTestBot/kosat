package components

import mui.icons.material.ContentCopy
import mui.material.Box
import mui.material.IconButton
import mui.material.Size
import mui.material.Snackbar
import mui.system.PropsWithSx
import mui.system.sx
import react.FC
import react.Fragment
import react.Props
import react.create
import react.dom.html.ReactHTML.span
import react.useState
import web.cssom.pt
import web.cssom.scale
import web.navigator.navigator

external interface CopyButtonProps : PropsWithSx {
    var lazyText: () -> String
}

val CopyButton: FC<CopyButtonProps> = FC("CopyButton") { props ->
    var open by useState(false)

    Snackbar {
        this.open = open
        onClose = { _, _ -> open = false }
        message = Fragment.create { +"Copied to clipboard" }
        autoHideDuration = 1000
    }

    Box {
        component = span
        sx {
            margin = (-4).pt
            +props.sx
        }
        IconButton {
            sx {
                transform = scale(0.7)
            }
            size = Size.small
            onClick = {
                navigator.clipboard.writeText(props.lazyText()).then {
                    open = true
                }
            }
            ContentCopy {}
        }
    }
}