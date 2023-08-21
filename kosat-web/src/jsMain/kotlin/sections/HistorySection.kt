package sections

import SolverCommand
import WrapperCommand
import bindings.FixedSizeList
import bindings.FixedSizeListItemParams
import cdclDispatchContext
import cdclWrapperContext
import components.CommandButton
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
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.number
import web.cssom.pct
import web.cssom.pt

private external interface HistoryEntryProps : PropsWithStyle {
    var command: SolverCommand
    var historyIndex: Int
    var inFuture: Boolean
}

/**
 * Displays a single command in the history and allows the user to jump to it.
 */
private val HistoryEntry: FC<HistoryEntryProps> = FC("HistoryEntry") { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!
    val theme = useTheme<Theme>()

    ListItemButton {
        style = props.style

        if (props.command in solver.commandsToRunEagerly) {
            ListItemIcon {
                SmartToy {}
            }
        }

        ListItemText {
            sx {
                color = if (props.inFuture) {
                    theme.palette.text.secondary
                } else {
                    theme.palette.text.primary
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
    val dispatch = useContext(cdclDispatchContext)!!

    if (solver.history.size + solver.redoHistory.size < 30) {
        mui.material.List {
            sx {
                flexGrow = number(1.0)
                overflow = auto
            }

            dense = true

            ListItemButton {
                ListItemText {
                    +"Initial state"
                }

                onClick = {
                    dispatch(WrapperCommand.TimeTravel(0))
                }
            }

            for ((i, command) in solver.history.withIndex()) {
                HistoryEntry {
                    key = i.toString()
                    historyIndex = i
                    this.command = command
                    inFuture = false
                }
            }

            for ((i, command) in solver.redoHistory.withIndex()) {
                val index = solver.history.size + i

                HistoryEntry {
                    key = index.toString()
                    historyIndex = index
                    this.command = command
                    inFuture = true
                }
            }
        }
    } else {
        FixedSizeList {
            width = 400
            height = 200
            itemSize = 42
            itemCount = solver.history.size + solver.redoHistory.size + 1

            children = { params: FixedSizeListItemParams ->
                val index = params.index

                if (index == 0) {
                    ListItemButton.create {
                        style = params.style

                        ListItemText {
                            +"Initial state"
                        }

                        onClick = {
                            dispatch(WrapperCommand.TimeTravel(0))
                        }
                    }
                } else if (index - 1 < solver.history.size) {
                    val command = solver.history[index - 1]

                    HistoryEntry.create {
                        style = params.style
                        historyIndex = index - 1
                        this.command = command
                        inFuture = false
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

            command = WrapperCommand.Undo()
            +"Undo"
        }
        CommandButton{
            sx {
                flexGrow = number(1.0)
            }

            command = WrapperCommand.Redo()
            +"Redo"
        }
    }
}