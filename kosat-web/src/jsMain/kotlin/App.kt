import mui.material.CssBaseline
import mui.system.ThemeProvider
import react.FC
import react.Props
import react.StrictMode
import react.createContext
import react.useReducer

/**
 * Universal context for the [CdclWrapper]. This is used to pass the immutable
 * solver wrapper around to components that need it, and it must be provided in
 * the root component, [App].
 */
val cdclWrapperContext = createContext<CdclWrapper>()

/**
 * Context for the solver dispatch function. Interactions with the app go
 * through this function. Similar to [cdclWrapperContext], it is provided in
 * [App]. Both [SolverCommand] and [WrapperCommand] must be dispatched through
 * this context.
 *
 * @see WrapperCommand
 */
val cdclDispatchContext = createContext<(WrapperCommand) -> Unit>()

/**
 * The root component of the application.
 *
 * We define the solver and dispatch contexts here, and wrap the entire app in
 * respective providers for them.
 *
 * @see cdclWrapperContext
 * @see cdclDispatchContext
 */
val App: FC<Props> = FC("App") {
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