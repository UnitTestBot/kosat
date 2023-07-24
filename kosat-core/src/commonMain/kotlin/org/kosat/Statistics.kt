package org.kosat

/**
 * A class for tracking statistics of the solver,
 * and for logging them if required.
 */
class Statistics {
    private val allStatistics: MutableList<Statistic> = mutableListOf()

    private fun statistic(logging: Boolean): Statistic {
        val stat = Statistic(logging)
        allStatistics.add(stat)
        return stat
    }

    /** Amount of conflicts occurred */
    var conflicts = statistic(false)

    /** Amount of decisions made */
    var decisions = statistic(false)

    /** Amount of propagations made */
    var propagations = statistic(false)

    /** Amount of literals propagated (can be multiple per propagation) */
    var propagatedLiterals = statistic(false)

    /** Amount of learned clauses */
    var learned = statistic(false)

    /** Amount of deleted clauses (both learned and given) */
    var deleted = statistic(false)

    /** Amount of restarts */
    var restarts = statistic(false)

    /** Amount of clauses shrunk by removing falsified literals */
    var shrunkClauses = statistic(false)

    /** Amount of clauses removed because they are satisfied by level 0 assignment */
    var satisfiedClauses = statistic(false)

    /** Amount of reductions of the clause database */
    var dbReduces = statistic(false)

    /** Amount of probes in failed literal probing tried */
    var flpProbes = statistic(false)

    /** Amount of propagations in FLP */
    var flpPropagations = statistic(false)

    /** Amount of clauses derived by hyper binary resolution */
    var flpHyperBinaries = statistic(false)

    /** Amount of unit clauses derived from failed probes propagated */
    var flpUnitClauses = statistic(false)

    /**
     * Start counting statistics this restart from 0.
     * Also does `restarts.inc` for convenience.
     * This should be done when the solver is restarted.
     */
    fun restart() {
        for (stat in allStatistics) {
            stat.resetThisRestart()
        }

        restarts.inc { "Restarting" }
    }

    /**
     * Start counting statistics this solve from 0.
     *
     * This should be done when the solver is started.
     */
    fun resetThisSolve() {
        for (stat in allStatistics) {
            stat.resetThisSolve()
        }
    }
}

/**
 * A convenience class for tracking a single statistic.
 */
data class Statistic(
    /** Whether to log changes in this statistic */
    val logging: Boolean
) {
    /** Overall value of the statistic, from the creation of the solver */
    var overall: Int = 0

    /** Value of the statistic at the last restart */
    private var valueLastRestart: Int = 0

    /** Value of the statistic at the last call to [CDCL.solve] */
    private var valueLastSolve: Int = 0

    /** Value of the statistic this restart */
    val thisRestart get() = overall - valueLastRestart

    /** Value of the statistic this solve */
    val thisSolve get() = overall - valueLastSolve

    /**
     * Increment the statistic by 1, optionally printing a message.
     *
     * @param message A function that returns a string to log,
     *        or null if nothing should be logged.
     */
    inline fun inc(crossinline message: () -> String?) {
        overall++
        if (logging) message()?.let { println(it) }
    }

    /**
     * Reset the statistic this restart to 0.
     * @see [Statistics.restart]
     */
    fun resetThisRestart() {
        valueLastRestart = overall
    }

    /**
     * Reset the statistic this solve to 0.
     * @see [Statistics.resetThisSolve]
     */
    fun resetThisSolve() {
        resetThisRestart()
        valueLastSolve = overall
    }
}
