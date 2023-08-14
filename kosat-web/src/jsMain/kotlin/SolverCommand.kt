import org.kosat.Lit
import org.kosat.cnf.CNF

sealed interface SolverCommand {
    data class Recreate(val cnf: CNF) : SolverCommand
    data object Undo : SolverCommand
    data object Solve : SolverCommand
    data object Propagate : SolverCommand
    data object PropagateOne : SolverCommand
    data object Restart : SolverCommand
    data class Backtrack(val level: Int) : SolverCommand
    data object Learn : SolverCommand
    data class Enqueue(val lit: Lit) : SolverCommand
    data object AnalyzeConflict : SolverCommand
    data object AnalyzeOne : SolverCommand
    data object AnalysisMinimize : SolverCommand
    data object LearnAsIs : SolverCommand
}