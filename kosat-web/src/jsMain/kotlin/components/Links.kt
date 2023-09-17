package components

import kotlinx.browser.window
import mui.icons.material.GitHub
import mui.icons.material.YouTube
import mui.material.Box
import mui.material.IconButton
import mui.material.Size
import mui.system.sx
import react.FC
import react.Props
import web.cssom.Display
import web.cssom.NamedColor
import web.cssom.Position
import web.cssom.integer
import web.cssom.pt

val Links: FC<Props> = FC("Links") {
    Box {
        sx {
            position = Position.absolute
            display = Display.flex
            top = (-0).pt
            right = 8.pt
            zIndex = integer(1)
        }

        IconButton {
            size = Size.large
            onClick = { _ ->
                window.open("placeholder", "_blank")
            }
            YouTube {
                sx {
                    color = NamedColor.black
                }
            }
        }

        IconButton {
            size = Size.large
            onClick = { _ ->
                window.open("https://github.com/UnitTestBot/kosat", "_blank")
            }
            GitHub {
                sx {
                    color = NamedColor.black
                }
            }
        }
    }
}
