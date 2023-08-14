import js.core.jso
import mui.material.styles.PaletteColor
import mui.material.styles.createTheme
import web.cssom.Color

object Themes {
    val default = createTheme()
}

private fun augment(color: Color): PaletteColor {
    return Themes.default.palette.augmentColor(jso {
        this.color = jso {
            main = color
        }
    })
}

// "#42dd42"
object Colors {
    val truth = augment(Color("#42dd42"))
    val falsity = augment(Color("#dd5942"))
    val almostFalsity = augment(Color("#e19111"))
    val inactive = augment(Color("#c8c8c8"))
    val frozen = augment(Color("#68D3F0"))
    val bg = augment(Color("#ddd"))
}
