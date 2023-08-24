package components

import mui.icons.material.Help
import mui.material.Box
import mui.material.MuiTooltip.Companion.tooltip
import mui.material.Tooltip
import mui.system.sx
import react.FC
import react.PropsWithChildren
import react.create
import web.cssom.px
import web.cssom.scale

val HelpTooltip: FC<PropsWithChildren> = FC("HelpTooltip") { props ->
    Tooltip {
        sx {
            // FIXME: this does not work. Why?
            and(tooltip) {
                maxWidth = 500.px
            }
        }

        title = Box.create {
            +props.children
        }

        Help {
            sx {
                transform = scale(0.7)
            }
        }
    }
}