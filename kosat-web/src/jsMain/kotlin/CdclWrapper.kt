data class CdclWrapper(val version: Int, val state: CdclState) {
    fun canExecute(command: SolverCommand): Boolean {
        return state.canExecute(command)
    }

    fun execute(command: SolverCommand): CdclWrapper {
        state.execute(command)
        return copy(version = version + 1)
    }

    val result by lazy { state.result }
}