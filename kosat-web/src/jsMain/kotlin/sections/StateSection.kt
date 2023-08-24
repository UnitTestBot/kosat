package sections

import Colors
import cdclWrapperContext
import components.LabelledNumber
import mui.material.Box
import mui.material.Card
import mui.material.Stack
import mui.material.StackDirection
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import org.kosat.LBool
import org.kosat.SolveResult
import org.kosat.Var
import react.FC
import react.Props
import react.PropsWithChildren
import react.dom.html.ReactHTML
import react.useContext
import web.cssom.AlignItems
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.number
import web.cssom.pt

private external interface StateCardProps : PropsWithChildren {
    var title: String
}

/**
 * A card that displays a single piece of information about the current state
 * of the solver.
 */
private val StateCard: FC<StateCardProps> = FC("StateCard") { props ->
    Card {
        elevation = 3

        sx {
            flexGrow = number(1.0)
            padding = 8.pt
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
        }

        Typography {
            variant = TypographyVariant.h3
            +props.title
        }

        +props.children
    }
}

/**
 * Section for displaying the general information about the current state of the
 * solver, such as the current decision level, the number of unassigned
 * variables, etc.
 */
val StateSection: FC<Props> = FC("StateSection") {
    val solver = useContext(cdclWrapperContext)!!

    Box {
        sx {
            display = Display.flex
            gap = 8.pt
        }

        StateCard {
            title = "Result"

            when (solver.result) {
                SolveResult.SAT -> Typography {
                    variant = TypographyVariant.h1
                    component = ReactHTML.span
                    sx {
                        color = Colors.truth.main
                    }
                    +"SAT"
                }

                SolveResult.UNSAT -> Typography {
                    variant = TypographyVariant.h1
                    component = ReactHTML.span
                    sx {
                        color = Colors.falsity.main
                    }
                    +"UNSAT"
                }

                SolveResult.UNKNOWN -> +"UNKNOWN"
            }
        }

        solver.state.inner.run {
            StateCard {
                title = "Decision Level"

                Typography {
                    variant = TypographyVariant.h1
                    component = ReactHTML.span
                    +assignment.decisionLevel.toString()
                }
            }

            StateCard {
                title = "Variables"

                Stack {
                    direction = responsive(StackDirection.row)
                    spacing = responsive(8.pt)

                    LabelledNumber {
                        label = "Unassigned"
                        value = assignment.value.withIndex().count {
                            assignment.isActive(Var(it.index)) && it.value == LBool.UNDEF
                        }
                    }

                    LabelledNumber {
                        label = "Not fixed"
                        value = assignment.value.indices.count {
                            val v = Var(it)
                            assignment.isActive(v) && assignment.fixed(v) == LBool.UNDEF
                        }
                    }

                    // LabelledNumber {
                    //     label = "Active"
                    //     value = assignment.numberOfActiveVariables
                    // }

                    LabelledNumber {
                        label = "Total"
                        value = assignment.numberOfVariables
                    }
                }
            }
        }
    }
}
