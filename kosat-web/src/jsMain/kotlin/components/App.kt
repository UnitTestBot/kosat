package components

import CdclState
import CdclWrapper
import SolverCommand
import org.kosat.cnf.CNF
import react.FC
import react.Props
import react.createContext
import react.useReducer


external interface AppProps : Props

val cdclWrapperContext = createContext<CdclWrapper>()
val cdclDispatchContext = createContext<(SolverCommand) -> Unit>()

val App = FC<AppProps> {
    val (solver, dispatch) = useReducer({ wrapper: CdclWrapper, command: SolverCommand ->
        wrapper.execute(command)
    }, CdclWrapper(0, CdclState(CNF(emptyList()))))

    cdclWrapperContext.Provider(solver) {
        cdclDispatchContext.Provider(dispatch) {
            Visualizer {}
        }
    }
}

