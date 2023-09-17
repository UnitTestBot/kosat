package sections

import SolverCommand
import bindings.FixedSizeGrid
import bindings.FixedSizeGridItemParams
import cdclWrapperContext
import components.CopyButton
import components.IconCommandButton
import components.LitNode
import mui.icons.material.Add
import mui.icons.material.Remove
import mui.material.Box
import mui.material.Size
import mui.material.Stack
import mui.material.StackDirection
import mui.system.responsive
import mui.system.sx
import org.kosat.LBool
import org.kosat.Var
import react.FC
import react.Props
import react.PropsWithStyle
import react.create
import react.useContext
import web.cssom.AlignItems
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.Position
import web.cssom.array
import web.cssom.number
import web.cssom.pct
import web.cssom.pt

private external interface AssignmentItemProps : PropsWithStyle {
    @Suppress("INLINE_CLASS_IN_EXTERNAL_DECLARATION_WARNING")
    var variable: Var
}

/**
 * Component for displaying a single variable in the assignment.
 */
private val AssignmentItem: FC<AssignmentItemProps> = FC("AssigmentItem") { props ->
    val v = props.variable
    val varIndex = v.index

    Stack {
        key = varIndex.toString()
        style = props.style

        sx {
            alignItems = AlignItems.center
        }

        LitNode {
            lit = v.posLit
        }

        Stack {
            direction = responsive(StackDirection.row)

            IconCommandButton {
                size = Size.small
                command = SolverCommand.Enqueue(v.posLit)
                descriptionOverride = """
                                Assign this variable to true on a new decision level.
                            """.trimIndent()
                Add {}
            }

            IconCommandButton {
                size = Size.small
                command = SolverCommand.Enqueue(v.negLit)
                descriptionOverride = """
                                Assign this variable to false on a new decision level.
                            """.trimIndent()
                Remove {}
            }
        }
    }
}

/**
 * Section of the visualizer for displaying the current assignment and assigning
 * values to variables.
 */
val AssignmentSection: FC<Props> = FC("AssignmentSection") {
    val solver = useContext(cdclWrapperContext)!!
    val assignment = solver.state.inner.assignment

    Box {
        sx {
            display = Display.flex
            flexGrow = number(1.0)
            // justifyContent = JustifyContent.center
            overflowX = auto
        }

        CopyButton {
            sx {
                position = Position.absolute
                top = 8.pt
                right = 8.pt

            }
            lazyText = {
                (0 until assignment.numberOfVariables).joinToString(" ") { varIndex ->
                    val v = Var(varIndex)
                    when (assignment.value(v)) {
                        LBool.TRUE -> v.posLit.toDimacs().toString()
                        LBool.FALSE -> v.negLit.toDimacs().toString()
                        else -> "0"
                    }
                }
            }
        }

        Stack {
            sx {
                margin = auto
            }

            direction = responsive(StackDirection.row)
            spacing = responsive(0.pt)

            // We fall back to a fixed size grid if there are too many variables
            if (assignment.numberOfVariables < 30) {
                for (varIndex in 0 until assignment.numberOfVariables) {
                    val v = Var(varIndex)

                    AssignmentItem {
                        variable = v
                    }
                }
            } else {
                FixedSizeGrid {
                    columnCount = assignment.numberOfVariables
                    columnWidth = 64
                    height = 100
                    rowCount = 1
                    rowHeight = 64
                    width = 1200
                    children = { params: FixedSizeGridItemParams ->
                        val varIndex = params.columnIndex
                        val v = Var(varIndex)

                        AssignmentItem.create {
                            style = params.style
                            variable = v
                        }
                    }
                }
            }
        }
    }
}
