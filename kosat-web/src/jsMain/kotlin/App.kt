import mui.material.CssBaseline
import mui.system.ThemeProvider
import react.FC
import react.Props
import react.StrictMode
import react.createContext
import react.useReducer


external interface AppProps : Props

val cdclWrapperContext = createContext<CdclWrapper>()
val cdclDispatchContext = createContext<(WrapperCommand) -> Unit>()

val App = FC<AppProps> {
    val (solver, dispatch) = useReducer({ wrapper: CdclWrapper, command: WrapperCommand ->
        wrapper.execute(command)
    }, CdclWrapper())

    StrictMode {
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
}