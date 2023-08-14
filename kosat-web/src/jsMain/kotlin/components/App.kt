package components

import CdclState
import CdclWrapper
import SolverCommand
import mui.material.CssBaseline
import mui.system.ThemeProvider
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

    ThemeProvider {
        theme = Themes.default

        CssBaseline {}

        cdclWrapperContext.Provider(solver) {
            cdclDispatchContext.Provider(dispatch) {
                Visualizer {}
            }
        }
    }
}

