package org.kosat

data class Configuration(
    val flp: FailedLiteralPropagation? = FailedLiteralPropagation(),
    val restarts: Restarts = Restarts.Luby(),
    val clauseDB: ClauseDB = ClauseDB(),
) {
    data class FailedLiteralPropagation(
        val maxProbes: Int = 1000,
        val hyperBinaryResolution: Boolean = true,
    )

    sealed interface Restarts {
        data class Luby(val conflictCountConstant: Double = 50.0) : Restarts
    }

    data class ClauseDB(
        val initialMaxLearntClauses: Int = 6000,
        val maxLearntClauseIncrement: Int = 500,
        val reduceStrategy: ReduceStrategy = ReduceStrategy.LBD,
    ) {
        enum class ReduceStrategy {
            LBD,
        }
    }
}
