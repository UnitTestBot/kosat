package sections

import cdclDispatchContext
import cdclWrapperContext
import components.CommandButton
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Stack
import mui.system.responsive
import react.FC
import react.Props
import react.useContext
import web.cssom.pt

/**
 * Section of the visualizer that contains primary buttons to control the
 * solver, which is currently only the "Search" button, and "Next action"
 * button.
 */
val ActionsSection: FC<Props> = FC("ActionsSection") {
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!

    Stack {
        spacing = responsive(8.pt)

        CommandButton {
            command = SolverCommand.Search
            +"Search"
        }

        Button {
            variant = ButtonVariant.contained
            disabled = solver.nextAction == null
            onClick = {
                solver.nextAction?.let { dispatch(it) }
            }
            +"Do next thing solver would do"
        }
    }
}
