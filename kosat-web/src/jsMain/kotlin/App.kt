import js.core.jso
import mui.material.CssBaseline
import mui.system.ThemeProvider
import react.FC
import react.Props
import react.StrictMode
import react.create
import react.createContext
import react.dom.client.createRoot
import react.router.RouterProvider
import react.router.dom.createHashRouter
import react.useReducer
import routes.Solver
import routes.Visualizer
import web.dom.document
import web.html.HTML

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
    val (solver, dispatch) = useReducer(
        { wrapper: CdclWrapper, command: WrapperCommand ->
            wrapper.execute(command)
        },
        CdclWrapper.fromString(
            """
                p cnf 9 13
                -1 2 0
                -1 3 0
                -2 -3 4 0
                -4 5 0
                -4 6 0
                -5 -6 7 0
                -7 1 0
                1 4 7 8 0
                -1 -4 -7 -8 0
                1 4 7 9 0
                -1 -4 -7 -9 0
                8 9 0
                -8 -9 0
        """.trimIndent()
        )
    )

    val router = createHashRouter(
        arrayOf(
            jso {
                path = "/"
                element = Solver.create {}
            },
            jso {
                path = "/visualizer"
                element = Visualizer.create {}
            },
        )
    )

    StrictMode {
        ThemeProvider {
            theme = Themes.default

            CssBaseline {}

            cdclWrapperContext.Provider(solver) {
                cdclDispatchContext.Provider(dispatch) {
                    RouterProvider {
                        this.router = router
                    }
                }
            }
        }
    }
}

fun main() {
    val container = document.createElement(HTML.div)
    document.body.appendChild(container)
    val app = App.create()
    createRoot(container).render(app)
}
