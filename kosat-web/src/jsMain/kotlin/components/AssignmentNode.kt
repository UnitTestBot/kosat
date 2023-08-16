package components

import SolverCommand
import mui.icons.material.Add
import mui.icons.material.Remove
import mui.material.Box
import mui.material.Size
import mui.material.Stack
import mui.material.StackDirection
import mui.system.responsive
import mui.system.sx
import org.kosat.Var
import react.FC
import react.Props
import react.useContext
import web.cssom.AlignItems
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.number
import web.cssom.pt

external interface AssignmentProps : Props

val AssignmentNode: FC<AssignmentProps> = FC {
    val solver = useContext(cdclWrapperContext)!!
    val assignment = solver.state.inner.assignment

    Box {
        sx {
            display = Display.flex
            flexGrow = number(1.0)
            // justifyContent = JustifyContent.center
            overflow = auto
        }

        Stack {
            sx {
                margin = auto
            }

            direction = responsive(StackDirection.row)
            spacing = responsive(8.pt)

            for (varIndex in 0 until assignment.numberOfVariables) {
                val v = Var(varIndex)

                Stack {
                    key = varIndex.toString()
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
                            Add {}
                        }

                        IconCommandButton {
                            size = Size.small
                            command = SolverCommand.Enqueue(v.posLit)
                            Remove {}
                        }
                    }
                }
            }
        }
    }
}
