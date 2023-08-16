package components

import SolverCommand
import WrapperCommand
import mui.icons.material.SmartToy
import mui.material.Box
import mui.material.ButtonGroup
import mui.material.ListItemButton
import mui.material.ListItemIcon
import mui.material.ListItemText
import mui.material.Stack
import mui.material.styles.Theme
import mui.material.styles.useTheme
import mui.system.sx
import react.FC
import react.Props
import react.useContext
import web.cssom.Auto.Companion.auto
import web.cssom.Display
import web.cssom.number
import web.cssom.pct
import web.cssom.pt

external interface HistoryEntryProps : Props {
    var command: SolverCommand
    var historyIndex: Int
    var inFuture: Boolean
}

val HistoryEntry = FC<HistoryEntryProps> { props ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!
    val theme = useTheme<Theme>()

    ListItemButton {
        if (props.command in solver.runEagerly) {
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

external interface HistoryProps : Props

val History = FC<HistoryProps> { _ ->
    val solver = useContext(cdclWrapperContext)!!
    val dispatch = useContext(cdclDispatchContext)!!
    val theme = useTheme<Theme>()

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

            command = WrapperCommand.Undo
            +"Undo"
        }
        CommandButton{
            sx {
                flexGrow = number(1.0)
            }

            command = WrapperCommand.Redo
            +"Redo"
        }
    }
}