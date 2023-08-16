import org.kosat.Lit
import org.kosat.cnf.CNF

sealed interface WrapperCommand {
    data class Recreate(val cnf: CNF) : WrapperCommand
    data object Undo : WrapperCommand
    data object Redo : WrapperCommand
}

sealed interface SolverCommand : WrapperCommand {
    data object Solve : SolverCommand
    data object Propagate : SolverCommand
    data object PropagateOne : SolverCommand
    data class PropagateUpTo(val trailIndex: Int) : SolverCommand
    data object Restart : SolverCommand
    data class Backtrack(val level: Int) : SolverCommand
    data class Enqueue(val lit: Lit) : SolverCommand
    data object AnalyzeConflict : SolverCommand
    data object AnalyzeOne : SolverCommand
    data object AnalysisMinimize : SolverCommand
    data object LearnAndBacktrack : SolverCommand
}