import components.HelpTooltip
import js.core.jso
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Card
import mui.material.Paper
import mui.material.TextField
import mui.material.Typography
import mui.material.styles.Theme
import mui.material.styles.TypographyVariant
import mui.material.styles.useTheme
import mui.system.sx
import org.kosat.SolveResult
import react.FC
import react.Props
import react.dom.html.ReactHTML
import react.dom.onChange
import react.useContext
import react.useState
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.FontFamily
import web.cssom.TextAlign
import web.cssom.array
import web.cssom.pct
import web.cssom.pt
import org.kosat.cnf.CNF
import react.dom.html.ReactHTML.span
import react.router.useNavigate
import web.cssom.AlignItems
import web.cssom.JustifyContent
import web.cssom.number

/**
 * Main solver component. This is the page user sees when they open the app.
 *
 * The context for the solver is shared between the solver and the visualizer,
 * so going from the solver to the visualizer is trivial.
 */
val Solver: FC<Props> = FC("Solver") {
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!
    val theme = useTheme<Theme>()
    var problem by useState(solver.problem.toString(includeHeader = true))
    var error by useState<String?>(null)
    var errorShown by useState(false)
    val navigate = useNavigate()

    Typography {
        sx {
            textAlign = TextAlign.center
            margin = 8.pt
        }
        variant = TypographyVariant.h1
        +"KoSAT Solver"
    }

    Box {
        sx {
            display = Display.flex
            flexDirection = FlexDirection.column
            gap = 8.pt
            maxWidth = 600.pt
            width = 100.pct
            margin = array(8.pt, auto)
        }

        Paper {
            elevation = 3
            sx {
                padding = 8.pt
                display = Display.flex
                flexDirection = FlexDirection.column
                gap = 8.pt
            }

            Typography {
                variant = TypographyVariant.h2
                sx {
                    textAlign = TextAlign.center
                }

                +"Input"
                HelpTooltip {
                    // note: duplicated in Visualizer.kt
                    Typography {
                        +"The input is parsed as a DIMACS CNF."
                    }

                    Typography {
                        +"""
                            The file starts with a header, which is a line starting with "p cnf",
                            followed by the number of variables and the number of clauses.
                        """.trimIndent()
                    }

                    Typography {
                        +"""
                            The rest of the file consists of clauses, one per line. Each clause
                            is a list of literals, separated by spaces. A literal is a positive
                            or negative integer, where the absolute value is the variable number,
                            and the sign indicates the polarity of the literal.
                        """.trimIndent()
                    }

                    Typography {
                        +"""
                            For example, the following is a valid input file:
                        """.trimIndent()
                    }

                    Box {
                        component = ReactHTML.pre
                        +"""
                            p cnf 3 2
                            1 -2 3 0
                            2 0
                        """.trimIndent()
                    }
                }
            }

            TextField {
                rows = 25
                fullWidth = true

                inputProps = jso {
                    style = jso {
                        fontFamily = FontFamily.monospace
                        height = 100.pct
                    }
                }

                value = problem
                multiline = true
                onChange = { event -> problem = event.target.asDynamic().value as String }
            }
        }

        fun parseCnf(): CNF? {
            val cnf: CNF
            try {
                cnf = CNF.fromString(problem)
            } catch (e: Exception) {
                error = e.message
                errorShown = true
                return null
            }
            return cnf
        }

        Paper {
            elevation = 3
            sx {
                padding = 8.pt
                display = Display.flex
                flexDirection = FlexDirection.column
                gap = 8.pt
            }

            Button {
                variant = ButtonVariant.contained
                +"Solve"

                onClick = {
                    parseCnf()?.let { cnf ->
                        dispatch(WrapperCommand.RecreateAndSolve(cnf))
                    }
                }
            }

            Button {
                variant = if (problem.length < 200) {
                    ButtonVariant.contained
                } else if (problem.length < 20000) {
                    ButtonVariant.outlined
                } else {
                    ButtonVariant.text
                }
                +"Visualize"

                onClick = {
                    parseCnf()?.let { cnf ->
                        dispatch(WrapperCommand.Recreate(cnf))
                        navigate("/visualizer")
                    }
                }
            }
        }

        Paper {
            elevation = 3
            sx {
                padding = 8.pt
                display = Display.flex
                flexDirection = FlexDirection.column
                gap = 8.pt
                minHeight = 400.pt
            }

            Typography {
                variant = TypographyVariant.h2
                sx {
                    textAlign = TextAlign.center
                }

                +"Output"
            }

            if (solver.result == SolveResult.UNKNOWN) {
                Box {
                    sx {
                        display = Display.flex
                        flexGrow = number(1.0)
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.center
                    }

                    Typography {
                        sx {
                            color = theme.palette.text.disabled
                        }

                        +"No result yet."
                    }
                }
            } else {
                Card {
                    elevation = 6

                    sx {
                        padding = 8.pt
                        display = Display.flex
                        flexDirection = FlexDirection.row
                        gap = 8.pt
                        justifyContent = JustifyContent.center
                        alignItems = AlignItems.center
                    }

                    Typography {
                        variant = TypographyVariant.h3
                        component = span
                        +"Result:"
                    }

                    Typography {
                        variant = TypographyVariant.h1
                        component = span
                        sx {
                            color = when (solver.result) {
                                SolveResult.SAT -> Colors.truth.main
                                SolveResult.UNSAT -> Colors.falsity.main
                                else -> error("Unreachable")
                            }
                        }
                        +" ${solver.result}"
                    }
                }

                if (solver.result == SolveResult.SAT) {
                    Typography {
                        sx {
                            textAlign = TextAlign.center
                            paddingTop = 8.pt
                        }
                        variant = TypographyVariant.h3
                        +"Model"
                    }

                    TextField {
                        fullWidth = true
                        multiline = true
                        rows = 25

                        @Suppress("UnsafeCastFromDynamic")
                        inputProps = jso<dynamic> {
                            this.style = jso {
                                fontFamily = FontFamily.monospace
                                height = 100.pct
                            }

                            this.readOnly = true
                        }
                        value = solver.model.mapIndexed { index, value ->
                            if (value) {
                                index + 1
                            } else {
                                -index - 1
                            }
                        }.joinToString(" ")
                    }
                } else if (solver.result == SolveResult.UNSAT) {
                    Box {
                        sx {
                            display = Display.flex
                            flexGrow = number(1.0)
                            justifyContent = JustifyContent.center
                            alignItems = AlignItems.center
                        }

                        Typography {
                            sx {
                                color = theme.palette.text.disabled
                            }

                            +"Model is only available for SAT instances."
                        }
                    }
                }
            }
        }
    }
}