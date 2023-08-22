import org.kosat.Lit
import org.kosat.cnf.CNF
import org.kosat.CDCL

/**
 * A command to the [CdclWrapper] or [CdclState].
 *
 * From the UI perspective, this is the command which is dispatched to the
 * [cdclDispatchContext] and executed by the [CdclWrapper], which updates the
 * state of the solver. It represents an action on the [CdclWrapper], maybe one
 * that is not yet executed (or cannot be executed).
 */
sealed interface WrapperCommand {
    /**
     * A description of the command, used for tooltips.
     */
    val description: String

    /**
     * Creates a new [CdclWrapper] instance with the given CNF as the problem.
     * This is used to reset the solver.
     */
    data class Recreate(val cnf: CNF) : WrapperCommand {
        override val description = "Create a new solver instance"
    }

    /**
     * Creates a new [CdclWrapper] instance with the given CNF as the problem,
     * and solves it. This is used in the landing page to simply solve the
     * problem without visualization.
     */
    data class RecreateAndSolve(val cnf: CNF) : WrapperCommand {
        override val description = "Create a new solver instance and solve it"
    }

    /**
     * [SolverCommand]s can be undone by creating a new solver instance and
     * replaying all commands up to the point where the command was executed.
     * We use this to undo commands. From [CdclWrapper], this action is
     * represented by [Undo] command.
     *
     * @param weak if true, do not undo the commands which were run eagerly
     *   (or would be run eagerly with the current settings).
     *   Equivalently, whether to undo commands from
     *   [CdclWrapper.commandsToRunEagerly] before undoing the last command
     *   as well.
     */
    data class Undo(val weak: Boolean = false) : WrapperCommand {
        override val description = "Undo the last command"
    }

    /**
     * [SolverCommand]s can be redone applying undone commands on a solver
     * state instance. This command is used to undo the [Undo] command.
     *
     * @param weak if true, do not redo the commands which were run eagerly
     *    after the last undone command. Equivalently, whether to redo commands
     *    from [CdclWrapper.commandsToRunEagerly] after redoing the last command
     *    as well.
     */
    data class Redo(val weak: Boolean = false) : WrapperCommand {
        override val description = "Redo the last undone command"
    }

    /**
     * Time travel to the given point in time by undoing and redoing commands.
     *
     * Travelling backward in time is done by resetting the solver and replaying
     * all commands up to the given point in time. Travelling forward in time is
     * simply done by replaying all commands up to the given point in time.
     *
     * This does not have a "weak" functionality (see [Undo.weak]), and all time
     * travels are assumed to be weak by default, that is, it completely ignores
     * commands from [CdclWrapper.commandsToRunEagerly].
     */
    data class TimeTravel(val historyIndex: Int) : WrapperCommand {
        override val description = """
            Time travel to the given point in time by undoing and redoing commands
        """.trimIndent()
    }

    /**
     * Set the given command to be run eagerly or not.
     *
     * If set to true, it adds it to [CdclWrapper.commandsToRunEagerly], and
     * if set to false, it removes it from there.
     */
    data class SetRunEagerly(val command: SolverCommand, val runEagerly: Boolean) : WrapperCommand {
        override val description = "this should be overridden in the UI"
    }
}

/**
 * This a subtype of [WrapperCommand] commands which are executed by the
 * [CdclState.execute]. Every command can also be used in
 * [CdclWrapper.commandsToRunEagerly] to be run eagerly. As noted there,
 * transitive closure of repeatedly applying any sequence of valid commands to
 * the solver state (valid command is the one, all [CdclState.requirementsFor]
 * of which are fulfilled) must terminate.
 */
sealed interface SolverCommand : WrapperCommand {
    /**
     * Eager priority of the command. This is used to determine the order in
     * which the commands are run eagerly. Commands with higher priority are
     * run before commands with lower priority. This is useful for, for example,
     * enforcing that [AnalysisMinimize] is run before [LearnAndBacktrack],
     * because the latter depends on the former.
     */
    val eagerPriority: Int get() = 0

    /**
     * Run the default solving algorithm on the given problem. This is a wrapper
     * around [CDCL.solve] which updates [CdclState].
     */
    data object Solve : SolverCommand {
        override val description = "Solve the given problem using the default algorithm"
    }

    /**
     * Run the default search algorithm on the given problem. This is a wrapper
     * around [CDCL.search] which updates [CdclState].
     */
    data object Search : SolverCommand {
        override val description = "Run the default CDCL search"
    }

    /**
     * A propagate procedure. This, and similar commands ([PropagateOne],
     * [PropagateUpTo]) must only be executed when at least one literal is not
     * propagated.
     *
     * @see CDCL.propagate
     */
    data object Propagate : SolverCommand {
        override val eagerPriority: Int get() = 1000

        override val description = """
            Propagate all literals that can be propagated.
        """.trimIndent().replace("\n", " ")
    }

    /**
     * A one iteration of the propagate procedure (outer loop, propagate a
     * single literal).
     *
     * @see Propagate
     */
    data object PropagateOne : SolverCommand {
        override val description = """
            Propagate the first not propagated literal on the trail.
        """.trimIndent().replace("\n", " ")
    }

    /**
     * Propagate literals until the given trail index is reached. To enforce the
     * termination requirement (see [SolverCommand] docs) for this command, we
     * enforce that the trail index is greater than current qhead, and not
     * greater than the trail size.
     *
     * @see Propagate
     */
    data class PropagateUpTo(val trailIndex: Int) : SolverCommand {
        override val description = """
            Propagate all literals up to this trail position.
        """.trimIndent().replace("\n", " ")
    }

    /**
     * Backtrack to the given layer, undoing all decisions made after this
     * layer. The layer number must be at least 0 and less than the current
     * decision level, no-op backtracks are not allowed due to the termination
     * requirement (see [SolverCommand] docs).
     */
    data class Backtrack(val level: Int) : SolverCommand {
        override val description = """
            Backtrack to the this level, undoing all decisions made after this level.
        """.trimIndent().replace("\n", " ")
    }

    /**
     * Create a new decision level, assign a literal to a value, and enqueue to
     * the trail.
     */
    data class Enqueue(val lit: Lit) : SolverCommand {
        override val description = """
            Assign this literal to be true on the new decision level. 
        """.trimIndent().replace("\n", " ")
    }

    /**
     * Analyze the current conflict. This does not minimize the conflict clause.
     *
     * This command requires at least two literals from the last decision level
     * to be in the conflict clause.
     *
     * @see CDCL.analyzeConflict
     */
    data object AnalyzeConflict : SolverCommand {
        override val description = """
            Analyze the current conflict without minimizing the conflict clause.
        """.trimIndent().replace("\n", " ")
    }

    /**
     * Does one iteration of the [CDCL.analyzeConflict] procedure. This is used
     * to visualize the conflict analysis and has the same requirements as
     * [AnalyzeConflict].
     */
    data object AnalyzeOne : SolverCommand {
        override val description = """
            Replace a single literal in the conflict clause with its reason.
        """.trimIndent().replace("\n", " ")
    }

    /**
     * Minimize the conflict clause by removing literals that are implied by the
     * remaining literals. This requires exactly one literal from the last
     * decision level to be in the conflict clause, and at least one literal
     * to be implied by the remaining literals.
     */
    data object AnalysisMinimize : SolverCommand {
        override val eagerPriority: Int get() = 100

        override val description = """
            Minimize the conflict clause by removing literals 
            that are implied by the remaining literals.
        """.trimIndent().replace("\n", " ")
    }

    /**
     * Learn the conflict clause and backtrack to the decision level where the
     * conflict clause will lead to an assignment of a literal. Does not
     * propagate. Requires a conflict clause with exactly one literal from the
     * last decision level.
     */
    data object LearnAndBacktrack : SolverCommand {
        override val description = """
            Learn the conflict clause and backtrack to the decision level where
            the conflict clause will lead to an assignment of a literal.
        """.trimIndent().replace("\n", " ")
    }
}

/**
 * A requirement for a command to be executed. This is used to determine whether
 * a command can be executed or not, and to display a tooltip with the
 * requirements for a command.
 * @see CdclState.requirementsFor
 * @see CdclWrapper.requirementsFor
 */
data class Requirement(
    /**
     * Whether the requirement is fulfilled or not.
     */
    val fulfilled: Boolean,
    /**
     * A message to display in the tooltip.
     */
    val message: String,
    /**
     * Obvious requirements are not displayed in the tooltip if they are
     * fulfilled. This is useful to avoid cluttering the tooltip with obvious
     * requirements, such as "Solver is not in UNSAT" state for every command.
     */
    val obvious: Boolean = false,
)
