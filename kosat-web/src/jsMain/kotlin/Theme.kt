import js.core.jso
import mui.material.styles.PaletteColor
import mui.material.styles.TypographyOptions
import mui.material.styles.TypographyVariant.Companion.body1
import mui.material.styles.TypographyVariant.Companion.h1
import mui.material.styles.TypographyVariant.Companion.h2
import mui.material.styles.TypographyVariant.Companion.h3
import mui.material.styles.createTheme
import web.cssom.Color
import web.cssom.FontWeight
import web.cssom.Margin
import web.cssom.pt
import web.cssom.rem

object Themes {
    val default = createTheme(jso {
        typography = TypographyOptions {
            h1 {
                fontSize = 2.rem
                fontWeight = FontWeight.bold
            }

            h2 {
                fontSize = 1.5.rem
                fontWeight = FontWeight.bold
            }

            h3 {
                fontSize = 1.2.rem
                fontWeight = FontWeight.bold
            }

            body1 {
                margin = Margin(0.5.rem, 0.pt)
            }
        }
    })
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
