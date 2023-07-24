package org.kosat

/**
 * A global external configuration of the [CDCL] solver.
 */
data class Configuration(
    /**
     * The restart strategy.
     */
    val restarts: Restarts = Restarts.Luby(),

    /**
     * Configuration of the clause database
     */
    val clauseDbStrategy: ClauseDbStrategy = ClauseDbStrategy.LBD(),

    /**
     * Failed literal probing configuration.
     */
    val flp: FailedLiteralPropagation? = FailedLiteralPropagation(),

    /**
     * The DRAT proof builder.
     */
    val dratBuilder: AbstractDratBuilder = NoOpDratBuilder(),

    /**
     * The function that determines if the solver should terminate because
     * it ran out of time, reached the maximum number of conflicts, or
     * used all its budget according to some other custom metric.
     * @see CDCL.search
     */
    val shouldTerminate: (Statistics) -> Boolean = { true },
) {
    /**
     * Configuration of the failed literal probing.
     * @see CDCL.failedLiteralProbing
     */
    data class FailedLiteralPropagation(
        /**
         * The maximum number of probes allowed to propagate.
         * @see CDCL.generateProbes
         */
        val maxProbes: Int = 1000,

        /**
         * Whether to use perform hyper-binary resolution on
         * the failed literal and learn binary clauses.
         * @see CDCL.hyperBinaryResolve
         */
        val hyperBinaryResolution: Boolean = true,
    )

    /**
     * The restart strategy.
     * @see Restarter
     */
    sealed class Restarts {
        /**
         * The Luby restart strategy.
         * @see Restarter
         */
        data class Luby(
            /**
             * The constant luby sequence is multiplied to.
             * @see Restarter.restartIfNeeded
             */
            val conflictCountConstant: Double = 50.0,
        ) : Restarts()
    }

    sealed class ClauseDbStrategy(
        /**
         * The initial maximum number of learnt clauses.
         * @see ClauseDatabase.reduceIfNeeded
         */
        val maxLearntsBeforeReduceInitial: Int,

        /**
         * The maximum number of learnt clauses increment.
         * @see ClauseDatabase.reduceIfNeeded
         */
        val maxLearntsBeforeReduceIncrement: Int,
    ) {
        /**
         * Use the clause activity to determine which clauses to remove.
         * @see ClauseDatabase.reduceBasedOnActivity
         */
        class Activity(
            maxLearntsBeforeReduceInitial: Int = 6000,
            maxLearntsBeforeReduceIncrement: Int = 500,
            val decay: Double = 0.999,
        ) : ClauseDbStrategy(
            maxLearntsBeforeReduceInitial,
            maxLearntsBeforeReduceIncrement,
        )

        /**
         * Use the LBD to determine which clauses to remove.
         * @see ClauseDatabase.reduceBasedOnLBD
         */
        class LBD(
            maxLearntsBeforeReduceInitial: Int = 6000,
            maxLearntsBeforeReduceIncrement: Int = 500,
        ) : ClauseDbStrategy(
            maxLearntsBeforeReduceInitial,
            maxLearntsBeforeReduceIncrement,
        )
    }
}
