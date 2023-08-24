package sections

import CdclWrapper
import SolverCommand
import WrapperCommand
import bindings.FixedSizeList
import bindings.FixedSizeListItemParams
import cdclDispatchContext
import cdclWrapperContext
import components.CommandButton
import js.core.jso
import mui.icons.material.SmartToy
import mui.material.ButtonGroup
import mui.material.ListItemButton
import mui.material.ListItemIcon
import mui.material.ListItemText
import mui.material.styles.Theme
import mui.material.styles.useTheme
import mui.system.sx
import react.FC
import react.Props
import react.PropsWithStyle
import react.create
import react.useContext
import react.useEffect
import react.useRef
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.FontWeight
import web.cssom.number
import web.cssom.pct
import web.cssom.pt
import web.html.HTMLElement
import web.scroll.ScrollBehavior
import web.scroll.ScrollLogicalPosition

private external interface HistoryEntryProps : PropsWithStyle {
    var command: SolverCommand?
    var historyIndex: Int
    var inFuture: Boolean
    var isCurrent: Boolean
}

/**
 * Displays a single command in the history and allows the user to jump to it.
 */
private val HistoryEntry: FC<HistoryEntryProps> = FC("HistoryEntry") { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!
    val theme = useTheme<Theme>()
    val button = useRef<HTMLElement>(null)

    useEffect(props.isCurrent) {
        if (props.isCurrent) {
            button.current?.scrollIntoView(jso {
                behavior = ScrollBehavior.auto
                block = ScrollLogicalPosition.nearest
                inline = ScrollLogicalPosition.nearest
            })
        }
    }

    ListItemButton {
        style = props.style
        ref = button

        sx {
            if (props.isCurrent) {
                backgroundColor = theme.palette.action.selected
            }
        }

        if (props.command in solver.commandsToRunEagerly) {
            ListItemIcon {
                SmartToy {}
            }
        }

        ListItemText {
            this.primaryTypographyProps = jso {
                sx {
                    if (props.inFuture) {
                        color = theme.palette.text.secondary
                    } else {
                        color = theme.palette.text.primary
                        fontWeight = FontWeight.bold
                    }
                }

            }

            +when (val command = props.command) {
                is SolverCommand.Search -> "Search"
                is SolverCommand.Solve -> "Solve"
                is SolverCommand.Propagate -> "Propagate"
                is SolverCommand.PropagateOne -> "Propagate one"
                is SolverCommand.PropagateUpTo -> "Propagate up to ${command.trailIndex}"
                is SolverCommand.Backtrack -> "Backtrack to level ${command.level}"
                is SolverCommand.Enqueue -> "Assign variable ${command.lit.variable.index + 1} to ${command.lit.isPos}"
                is SolverCommand.AnalyzeConflict -> "Analyze conflict fully"
                is SolverCommand.AnalyzeOne -> "Analyze single conflict literal"
                is SolverCommand.AnalysisMinimize -> "Minimize learned clause"
                is SolverCommand.LearnAndBacktrack -> "Learn clause and backtrack"
                null -> "Initial state"
            }
        }

        onClick = {
            dispatch(WrapperCommand.TimeTravel(props.historyIndex + 1))

        }
    }
}

/**
 * Displays a list of all commands that have been run, and allows the user
 * to undo or redo them.
 *
 * @see CdclWrapper.history
 * @see WrapperCommand.TimeTravel
 */
val HistorySection: FC<Props> = FC("HistorySection") { _ ->
    val solver = useContext(cdclWrapperContext)!!
    val listRef = useRef<HTMLElement>(null)

    useEffect(solver.history) {
        listRef.current?.scrollTop = solver.history.size * 42.0
    }

    if (solver.history.size + solver.redoHistory.size < 50) {
        mui.material.List {
            sx {
                flexGrow = number(1.0)
                overflow = auto
            }

            dense = true

            HistoryEntry {
                key = "initial"
                historyIndex = -1
                command = null
                inFuture = false
                isCurrent = solver.history.isEmpty()
            }

            for ((i, command) in solver.history.withIndex()) {
                HistoryEntry {
                    key = i.toString()
                    historyIndex = i
                    this.command = command
                    inFuture = false
                    isCurrent = i == solver.history.size - 1
                }
            }

            for ((i, command) in solver.redoHistory.withIndex()) {
                val index = solver.history.size + i

                HistoryEntry {
                    key = index.toString()
                    historyIndex = index
                    this.command = command
                    inFuture = true
                    isCurrent = false
                }
            }
        }
    } else {
        FixedSizeList {
            width = 400
            height = 200
            itemSize = 42
            itemCount = solver.history.size + solver.redoHistory.size + 1
            outerRef = listRef

            children = { params: FixedSizeListItemParams ->
                val index = params.index

                if (index == 0) {
                    HistoryEntry.create {
                        style = params.style
                        historyIndex = index - 1
                        this.command = null
                        inFuture = false
                        isCurrent = solver.history.isEmpty()
                    }
                } else if (index - 1 < solver.history.size) {
                    val command = solver.history[index - 1]

                    HistoryEntry.create {
                        style = params.style
                        historyIndex = index - 1
                        this.command = command
                        inFuture = false
                        isCurrent = index == solver.history.size
                    }
                } else {
                    val command = solver.redoHistory[index - solver.history.size - 1]

                    HistoryEntry.create {
                        style = params.style
                        historyIndex = index - 1
                        this.command = command
                        inFuture = true
                    }
                }
            }
        }
    }

    ButtonGroup {
        sx {
            width = 100.pct
            display = Display.flex
            gap = 8.pt
        }

        CommandButton {
            sx {
                flexGrow = number(1.0)
            }

            descriptionOverride = "Undo the last command. Hotkey: Ctrl+Z or Arrow Up"
            command = WrapperCommand.Undo()
            +"Undo"
        }
        CommandButton {
            sx {
                flexGrow = number(1.0)
            }

            descriptionOverride = "Redo the last command. Hotkey: Ctrl+Y, Ctrl+Shift+Z or Arrow Down"
            command = WrapperCommand.Redo()
            +"Redo"
        }
    }
}