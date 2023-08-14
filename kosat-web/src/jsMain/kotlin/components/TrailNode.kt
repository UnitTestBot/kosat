package components

import emotion.react.css
import mui.material.Box
import mui.material.Typography
import mui.material.styles.Theme
import mui.material.styles.useTheme
import mui.system.sx
import react.FC
import react.Props
import react.dom.html.ReactHTML.span
import react.useContext
import web.cssom.AlignItems
import web.cssom.AlignSelf
import web.cssom.Color
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.FontStyle
import web.cssom.TextAlign
import web.cssom.TextOrientation
import web.cssom.WritingMode
import web.cssom.number
import web.cssom.pt
import web.cssom.rem
import web.cssom.rgb
import web.cssom.translate

external interface TrailProps : Props

val TrailNode = FC<TrailProps> { _ ->
    val solver = useContext(cdclWrapperContext)!!
    val assignment = solver.state.inner.assignment
    val theme = useTheme<Theme>()

    val colors = object {
        val evenLevel = Color("#b5d2e1")
        val evenLevelEvenLit = Color("#cdeeff")
        val evenLevelOddLit = Color("#aac5d3")

        val oddLevel = Color("#e7e7b1")
        val oddLevelEvenLit = Color("#ffffc3")
        val oddLevelOddLit = Color("#d3d3a1")
    }

    Box {
        sx {
            display = Display.flex
            flexDirection = FlexDirection.column
        }

        for (level in 0..assignment.decisionLevel) {
            Box {
                sx {
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    backgroundColor = if (level % 2 == 0) {
                        colors.evenLevel
                    } else {
                        colors.oddLevel
                    }
                    padding = 3.pt
                }

                key = level.toString()

                Typography {
                    sx {
                        alignSelf = AlignSelf.end
                        fontSize = 24.pt
                        width = 30.pt
                        textAlign = TextAlign.center
                        writingMode = WritingMode.verticalLr
                        textOrientation = TextOrientation.mixed
                        transform = translate((-0.5).rem)
                    }

                    +"Level $level"
                }

                Box {
                    sx {
                        flexGrow = number(1.0)
                    }

                    for (i in 0 until assignment.trail.size) {
                        val lit = assignment.trail[i]
                        if (assignment.level(lit) != level) continue

                        Box {
                            key = lit.toString()

                            sx {
                                display = Display.flex
                                alignItems = AlignItems.center

                                backgroundColor = when {
                                    level % 2 == 0 && i % 2 == 0 -> colors.evenLevelEvenLit
                                    level % 2 == 0 && i % 2 == 1 -> colors.evenLevelOddLit
                                    level % 2 == 1 && i % 2 == 0 -> colors.oddLevelEvenLit
                                    level % 2 == 1 && i % 2 == 1 -> colors.oddLevelOddLit
                                    else -> null
                                }
                            }

                            LitNode {
                                this.lit = lit
                            }

                            if (assignment.reason(lit.variable) != null) {
                                ClauseNode {
                                    clause = assignment.reason(lit.variable)!!
                                }
                            }

                            if (i == assignment.qhead) {
                                css {
                                    backgroundColor = rgb(200, 200, 200)
                                }
                                Box {
                                    component = span
                                    css {
                                        fontStyle = FontStyle.italic
                                        margin = 3.pt
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}