package components

import mui.material.Stack
import mui.system.responsive
import react.FC
import react.Props
import web.cssom.pt

val ActionsNode: FC<Props> = FC {
    Stack {
        spacing = responsive(8.pt)

        CommandButton {
            command = SolverCommand.Search
            +"Search"
        }
    }
}
