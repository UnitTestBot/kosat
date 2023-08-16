import org.kosat.Lit
import org.kosat.cnf.CNF

sealed interface WrapperCommand {
    val description: String

    data class Recreate(val cnf: CNF) : WrapperCommand {
        override val description = "Create a new solver instance"
    }

    data object Undo : WrapperCommand {
        override val description = "Undo the last command"
    }

    data object Redo : WrapperCommand {
        override val description = "Redo the last undone command"
    }

    data class SetRunEagerly(val command: SolverCommand, val runEagerly: Boolean) : WrapperCommand {
        override val description = "this should be overridden in the UI"
    }
}

sealed interface SolverCommand : WrapperCommand {
    data object Solve : SolverCommand {
        override val description = "Solve the given problem using the default algorithm"
    }

    data object Search : SolverCommand {
        override val description = "Run the default CDCL search"
    }

    data object Propagate : SolverCommand {
        override val description = """
            Propagate all literals that can be propagated without making any decisions.
        """.trimIndent().replace("\n", " ")
    }

    data object PropagateOne : SolverCommand {
        override val description = """
            Propagate the next literal on the trail.
        """.trimIndent().replace("\n", " ")
    }

    data class PropagateUpTo(val trailIndex: Int) : SolverCommand {
        override val description = """
            Propagate all literals up to this trail position.
        """.trimIndent().replace("\n", " ")
    }

    data class Backtrack(val level: Int) : SolverCommand {
        override val description = """
            Backtrack to the this level, undoing all decisions made after this level.
        """.trimIndent().replace("\n", " ")
    }

    data class Enqueue(val lit: Lit) : SolverCommand {
        override val description = """
            Assign this literal to be true on the new decision level. 
        """.trimIndent().replace("\n", " ")
    }

    data object AnalyzeConflict : SolverCommand {
        override val description = """
            Analyze the current conflict and minimize the conflict clause.
        """.trimIndent().replace("\n", " ")
    }

    data object AnalyzeOne : SolverCommand {
        override val description = """
            Replace a single literal in the conflict clause with its reason.
        """.trimIndent().replace("\n", " ")
    }

    data object AnalysisMinimize : SolverCommand {
        override val description = """
            Minimize the conflict clause by removing literals 
            that are implied by the remaining literals.
        """.trimIndent().replace("\n", " ")
    }

    data object LearnAndBacktrack : SolverCommand {
        override val description = """
            Learn the conflict clause and backtrack to the decision level where
            the conflict clause will lead to an assignment of a literal.
        """.trimIndent().replace("\n", " ")
    }
}

data class Requirement(
    val fulfilled: Boolean,
    val message: String,
    val obvious: Boolean = false,
)
