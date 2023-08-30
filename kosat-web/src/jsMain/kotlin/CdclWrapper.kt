import org.kosat.CNF

/**
 * Immutable wrapper around the [CdclState]. This is the main interface for
 * interacting with the solver from the UI.
 *
 * It provides an immutable interface for interacting with the solver, and
 * implements "Time Travelling" feature, which allows the user to undo and redo
 * commands, and jump to any point in the history.
 *
 * The wrapper is immutable, but can be easily invalidated because it shares the
 * one instance of [CdclState] with all other wrappers. This means that any
 * mutation of the state instance causes all wrappers around it to become
 * invalid. This is overcome by having a wrapper stored in React `useReducer`
 * hook at the root of the application, and passing it down to all components.
 *
 * The only way to interact with the solver is through the [execute] method,
 * which returns a new instance of the wrapper with the given command executed.
 * In React terms, this is a reducer function. It mutates the state of this
 * instance, however, and therefore this instance cannot be reused after.
 * [execute] takes both [SolverCommand] and [WrapperCommand]. The latter is
 * directly passed to [CdclState] (along with executing some commands eagerly,
 * see implementation of [execute] and [commandsToRunEagerly] for more details
 * on that).
 *
 * This class also provides memoized shortcuts for some of the solver state
 * properties.
 */
data class CdclWrapper(
    /**
     * The list of [SolverCommand]s that have been executed so far. This is used
     * for time travelling and making sure the "purity" of wrapper's instance is
     * preserved (that is, instances of [CdclState] can be equal with different
     * histories, but instances of [CdclWrapper] cannot).
     */
    val history: List<SolverCommand> = emptyList(),
    /**
     * The problem that the solver is currently working on.
     */
    val problem: CNF = CNF(emptyList()),
    /**
     * The current (mutable) state of the solver we are wrapping. Note that any
     * mutation of state instance causes all wrappers around it to become
     * invalid. Extra care must be taken to ensure that the wrapper is not
     * reused after a mutation.
     */
    val state: CdclState = CdclState(CNF(emptyList())),
    /**
     * The list of [SolverCommand]s that have been undone so far, in the order
     * from the least recently executed (the most recently undone) to the most
     * recently executed (the least recently undone).
     */
    val redoHistory: List<SolverCommand> = emptyList(),
    /**
     * The list of [SolverCommand]s that should be executed eagerly after each
     * command user executes. This is used to implement a feature with automatic
     * execution of some commands (such as [SolverCommand.Propagate]) which is
     * sometimes too boring to do manually. See implementation in [execute] for
     * details.
     *
     * However, this list should be used with care. **It can easily lead to
     * infinite loops if the command can be executed in a way which does not
     * change the state of the solver in any way.** To overcome this, we
     * introduce additional constrained to [CdclState.requirementsFor], which
     * makes sure every command irreversibly changes the state of the solver,
     * and leads solver to an eventual termination. **If you consider solver to be
     * a deterministic state machine, then this machine must be finite.**
     *
     * @see CdclState.requirementsFor
     * @see SolverCommand.eagerPriority
     */
    val commandsToRunEagerly: List<SolverCommand> = emptyList(),
) {
    /**
     * The result of the solver's execution. This is a memoized shortcut for
     * [CdclState.result]. Note that simple memoization by [lazy] is enough
     * in [CdclWrapper] because it is immutable, but it is not enough in
     * [CdclState].
     */
    val result by lazy { state.result }

    /**
     * The next action that the solver will take. This is a memoized shortcut
     * for [CdclState.guessNextSolverAction].
     */
    val nextAction by lazy { state.guessNextSolverAction() }

    val model by lazy { state.getModel() }

    /**
     * Whether the given command can be executed. Equivalently, whether all
     * [Requirement]s for the command (obtained by [requirementsFor]) are
     * fulfilled.
     */
    val canExecute: Memo<WrapperCommand, Boolean> = { command: WrapperCommand ->
        requirementsFor(command).all { it.fulfilled }
    }.memoized()

    /**
     * The list of [Requirement]s that must be fulfilled for the given command
     * to be executed. It works equivalently to [CdclState.requirementsFor] and
     * has the same properties.
     */
    val requirementsFor: Memo<WrapperCommand, List<Requirement>> = { command: WrapperCommand ->
        when (command) {
            is WrapperCommand.Recreate -> emptyList()

            is WrapperCommand.RecreateAndSolve -> emptyList()

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
    }.memoized()

    /**
     * Executes the given command and returns a new instance of the wrapper with
     * the command executed. This is a reducer function, and it mutates the
     * current state of [CdclWrapper], making its state invalid. This means that
     * the instance of [CdclWrapper] can no longer be used. This requirement is
     * enforced by the way React reducers work, but it worth to keep in mind.
     */
    fun execute(command: WrapperCommand): CdclWrapper {
        when (command) {
            is WrapperCommand.Recreate -> return copy(
                history = emptyList(),
                redoHistory = emptyList(),
                problem = command.cnf,
                state = CdclState(command.cnf),
            )

            is WrapperCommand.RecreateAndSolve -> {
                val state = CdclState(command.cnf)
                state.inner.solve()
                return copy(
                    history = emptyList(),
                    redoHistory = emptyList(),
                    problem = command.cnf,
                    state = state,
                )
            }

            is WrapperCommand.Undo -> {
                val newUndoHistory = history.toMutableList()
                val newRedoHistory = redoHistory.toMutableList()

                if (!command.weak) {
                    while (newUndoHistory.lastOrNull() in commandsToRunEagerly) {
                        newRedoHistory.add(0, newUndoHistory.removeLast())
                    }
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

                if (!command.weak) {
                    while (newRedoHistory.firstOrNull() in commandsToRunEagerly) {
                        val commandToRun = newRedoHistory.removeFirst()
                        state.execute(commandToRun)
                        newUndoHistory.add(commandToRun)
                    }
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
                    commandsToRunEagerly + command.command
                } else {
                    commandsToRunEagerly - command.command
                }
                return copy(commandsToRunEagerly = newRunEagerly)
            }

            is SolverCommand -> {
                val newHistory = history.toMutableList()

                state.execute(command)
                newHistory.add(command)

                while (true) {
                    val commandToRun = commandsToRunEagerly.sortedByDescending {
                        it.eagerPriority
                    }.firstOrNull {
                        state.requirementsFor(it).all { req -> req.fulfilled }
                    } ?: break
                    state.execute(commandToRun)
                    newHistory.add(commandToRun)
                }

                var newRedoHistory = emptyList<SolverCommand>()
                if (newHistory == history + redoHistory.take(newHistory.size - history.size)) {
                    newRedoHistory = redoHistory.drop(newHistory.size - history.size)
                }

                return copy(
                    history = newHistory,
                    redoHistory = newRedoHistory,
                )
            }
        }
    }

    companion object {
        fun fromString(string: String): CdclWrapper {
            val cnf = CNF.fromString(string)
            return CdclWrapper(problem = cnf, state = CdclState(cnf))
        }
    }
}
