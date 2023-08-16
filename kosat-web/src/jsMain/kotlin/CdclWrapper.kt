import org.kosat.cnf.CNF

data class CdclWrapper(
    val history: List<SolverCommand>,
    val problem: CNF,
    val state: CdclState,
    val redoHistory: List<SolverCommand>,
    val runEagerly: List<SolverCommand>,
) {
    val result by lazy { state.result }

    constructor() : this(
        history = emptyList(),
        redoHistory = emptyList(),
        problem = CNF(emptyList()),
        state = CdclState(CNF(emptyList())),
        runEagerly = emptyList(),
    )

    fun canExecute(command: WrapperCommand): Boolean {
        return when (command) {
            is WrapperCommand.Recreate -> true
            is WrapperCommand.Undo -> history.isNotEmpty()
            is WrapperCommand.Redo -> redoHistory.isNotEmpty()
            is WrapperCommand.TimeTravel ->
                command.historyIndex in 0 until history.size + redoHistory.size
            is WrapperCommand.SetRunEagerly -> true
            is SolverCommand -> state.canExecute(command)
        }
    }

    fun requirementsFor(command: WrapperCommand): List<Requirement> {
        return when (command) {
            is WrapperCommand.Recreate -> emptyList()

            is WrapperCommand.Undo -> listOf(
                Requirement(
                    history.isNotEmpty(),
                    "There must be something left to undo",
                    obvious = true,
                )
            )

            is WrapperCommand.Redo -> listOf(
                Requirement(
                    redoHistory.isNotEmpty(),
                    "There must be something undone to redo",
                    obvious = true,
                )
            )

            is WrapperCommand.TimeTravel -> listOf(
                Requirement(
                    command.historyIndex in 0 until history.size + redoHistory.size,
                    "The given history index must be valid",
                    obvious = true,
                )
            )

            is WrapperCommand.SetRunEagerly -> emptyList()

            is SolverCommand -> state.requirementsFor(command)
        }
    }

    fun execute(command: WrapperCommand): CdclWrapper {
        when (command) {
            is WrapperCommand.Recreate -> return copy(
                history = emptyList(),
                redoHistory = emptyList(),
                problem = command.cnf,
                state = CdclState(command.cnf),
            )

            is WrapperCommand.Undo -> {
                val newUndoHistory = history.toMutableList()
                val newRedoHistory = redoHistory.toMutableList()

                while (newUndoHistory.lastOrNull() in runEagerly) {
                    newRedoHistory.add(0, newUndoHistory.removeLast())
                }

                if (newUndoHistory.isNotEmpty()) {
                    newRedoHistory.add(0, newUndoHistory.removeLast())
                }

                val newState = CdclState(problem)

                for (commandToRepeat in newUndoHistory) {
                    newState.execute(commandToRepeat)
                }

                return copy(
                    history = newUndoHistory,
                    redoHistory = newRedoHistory,
                    state = newState,
                )
            }

            is WrapperCommand.Redo -> {
                val newRedoHistory = redoHistory.toMutableList()
                val newUndoHistory = history.toMutableList()
                val lastCommand = newRedoHistory.removeFirst()
                state.execute(lastCommand)
                newUndoHistory.add(lastCommand)

                while (newRedoHistory.firstOrNull() in runEagerly) {
                    val commandToRun = newRedoHistory.removeFirst()
                    state.execute(commandToRun)
                    newUndoHistory.add(commandToRun)
                }

                return copy(
                    history = newUndoHistory,
                    redoHistory = newRedoHistory,
                )
            }

            is WrapperCommand.TimeTravel -> {
                if (command.historyIndex < history.size) {
                    val newState = CdclState(problem)

                    for (commandToRepeat in history.take(command.historyIndex)) {
                        newState.execute(commandToRepeat)
                    }

                    return copy(
                        state = newState,
                        history = history.take(command.historyIndex),
                        redoHistory = history.drop(command.historyIndex) + redoHistory,
                    )
                } else {
                    val toTake = command.historyIndex - history.size
                    for (commandToRepeat in redoHistory.take(toTake)) {
                        state.execute(commandToRepeat)
                    }

                    return copy(
                        state = state,
                        history = history + redoHistory.take(toTake),
                        redoHistory = redoHistory.drop(toTake),
                    )
                }
            }

            is WrapperCommand.SetRunEagerly -> {
                val newRunEagerly = if (command.runEagerly) {
                    runEagerly + command.command
                } else {
                    runEagerly - command.command
                }
                return copy(runEagerly = newRunEagerly)
            }

            is SolverCommand -> {
                val newHistory = history.toMutableList()

                state.execute(command)
                newHistory.add(command)

                while (true) {
                    val commandToRun = runEagerly.sortedByDescending {
                        it.priority
                    }.firstOrNull {
                        state.canExecute(it)
                    } ?: break
                    state.execute(commandToRun)
                    newHistory.add(commandToRun)
                }

                return copy(
                    history = newHistory,
                    redoHistory = emptyList(),
                )
            }
        }
    }
}