package org.kosat

/**
 * A class for tracking statistics of the solver,
 * and for logging them if required.
 */
class Statistics {
    /** Amount of conflicts occurred */
    var conflicts = Statistic(false)

    /** Amount of decisions made */
    var decisions = Statistic(false)

    /** Amount of propagations made */
    var propagations = Statistic(false)

    /** Amount of literals propagated (can be multiple per propagation) */
    var propagatedLiterals = Statistic(false)

    /** Amount of learned clauses */
    var learned = Statistic(false)

    /** Amount of deleted clauses (both learned and given) */
    var deleted = Statistic(false)

    /** Amount of restarts */
    var restarts = Statistic(false)

    /** Amount of clauses shrunk by removing falsified literals */
    var shrunkClauses = Statistic(false)

    /** Amount of clauses removed because they are satisfied by level 0 assignment */
    var satisfiedClauses = Statistic(false)

    /** Amount of reductions of the clause database */
    var dbReduces = Statistic(false)

    /** Amount of probes in failed literal probing tried */
    var flpProbes = Statistic(false)

    /** Amount of propagations in FLP */
    var flpPropagations = Statistic(false)

    /** Amount of clauses derived by hyper binary resolution */
    var flpHyperBinaries = Statistic(false)

    /** Amount of unit clauses derived from failed probes propagated */
    var flpUnitClauses = Statistic(false)

    /**
     * Start counting statistics this restart from 0.
     * Also does `restarts.inc` for convenience.
     * This should be done when the solver is restarted.
     */
    fun restart() {
        conflicts.resetThisRestart()
        decisions.resetThisRestart()
        propagations.resetThisRestart()
        propagatedLiterals.resetThisRestart()
        learned.resetThisRestart()
        deleted.resetThisRestart()
        restarts.resetThisRestart()
        shrunkClauses.resetThisRestart()
        satisfiedClauses.resetThisRestart()
        dbReduces.resetThisRestart()

        flpProbes.resetThisRestart()
        flpPropagations.resetThisRestart()
        flpHyperBinaries.resetThisRestart()
        flpUnitClauses.resetThisRestart()

        restarts.inc { "Restarting" }
    }

    /**
     * Start counting statistics this solve from 0.
     *
     * This should be done when the solver is started.
     */
    fun resetThisSolve() {
        conflicts.resetThisSolve()
        decisions.resetThisSolve()
        propagations.resetThisSolve()
        propagatedLiterals.resetThisSolve()
        learned.resetThisSolve()
        deleted.resetThisSolve()
        restarts.resetThisSolve()
        shrunkClauses.resetThisSolve()
        satisfiedClauses.resetThisSolve()
        dbReduces.resetThisSolve()

        flpProbes.resetThisSolve()
        flpPropagations.resetThisSolve()
        flpHyperBinaries.resetThisSolve()
        flpUnitClauses.resetThisSolve()
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
     * @param reasonToLog A function that returns a string to log,
     *        or null if nothing should be logged.
     */
    inline fun inc(crossinline reasonToLog: () -> String?) {
        overall++
        if (logging) reasonToLog()?.let { println(it) }
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
