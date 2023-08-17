package components

import cdclDispatchContext
import cdclWrapperContext
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Stack
import mui.system.responsive
import react.FC
import react.Props
import react.useContext
import web.cssom.pt

val ActionsNode: FC<Props> = FC {
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
