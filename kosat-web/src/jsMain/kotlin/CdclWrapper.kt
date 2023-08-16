import org.kosat.cnf.CNF

data class CdclWrapper(
    val history: List<SolverCommand>,
    val problem: CNF,
    val state: CdclState,
    val redoHistory: List<SolverCommand>,
) {
    val result by lazy { state.result }

    constructor() : this(
        history = emptyList(),
        redoHistory = emptyList(),
        problem = CNF(emptyList()),
        state = CdclState(CNF(emptyList()))
    )

    fun canExecute(command: WrapperCommand): Boolean {
        return when(command) {
            is WrapperCommand.Recreate -> true
            is WrapperCommand.Undo -> history.isNotEmpty()
            is WrapperCommand.Redo -> redoHistory.isNotEmpty()
            is SolverCommand -> state.canExecute(command)
        }
    }

    fun execute(command: WrapperCommand): CdclWrapper {
        when (command) {
            is WrapperCommand.Recreate -> return CdclWrapper(
                history = emptyList(),
                redoHistory = emptyList(),
                problem = command.cnf,
                state = CdclState(command.cnf)
            )

            is WrapperCommand.Undo -> {
                val lastCommand = history.last()
                val historyToRepeat = history.dropLast(1)
                val newState = CdclState(problem)

                for (commandToRepeat in historyToRepeat) {
                    newState.execute(commandToRepeat)
                }

                return CdclWrapper(
                    history = historyToRepeat,
                    redoHistory = redoHistory + lastCommand,
                    problem = problem,
                    state = newState
                )
            }

            is WrapperCommand.Redo -> {
                val lastCommand = redoHistory.last()
                state.execute(lastCommand)

                return CdclWrapper(
                    history = history + lastCommand,
                    redoHistory = redoHistory.dropLast(1),
                    problem = problem,
                    state = state
                )
            }

            is SolverCommand -> {
                state.execute(command)

                return CdclWrapper(
                    history = history + command,
                    redoHistory = emptyList(),
                    problem = problem,
                    state = state,
                )
            }
        }
    }
}