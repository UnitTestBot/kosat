package org.kosat

class Statistics {
    var conflicts = Statistic(false)
    var decisions = Statistic(false)
    var propagations = Statistic(false)
    var propagatedLiterals = Statistic(false)
    var learned = Statistic(false)
    var deleted = Statistic(false)
    var restarts = Statistic(false)
    var shrunkClauses = Statistic(false)
    var satisfiedClauses = Statistic(false)
    var dbReduces = Statistic(false)

    var flpProbes = Statistic(false)
    var flpPropagations = Statistic(false)
    var flpHyperBinaries = Statistic(false)
    var flpUnitClauses = Statistic(false)

    fun restart() {
        conflicts.restart()
        decisions.restart()
        propagations.restart()
        propagatedLiterals.restart()
        learned.restart()
        deleted.restart()
        restarts.restart()
        shrunkClauses.restart()
        satisfiedClauses.restart()
        dbReduces.restart()

        flpProbes.restart()
        flpPropagations.restart()
        flpHyperBinaries.restart()
        flpUnitClauses.restart()

        restarts.inc { "Restarting" }
    }
}

data class Statistic(val logging: Boolean) {
    var value: Int = 0
    private var valueLastRestart: Int = 0
    val thisRestart get() = value - valueLastRestart

    inline fun inc(crossinline reasonToLog: () -> String?) {
        value++
        if (logging) reasonToLog()?.let { println(it) }
    }

    fun restart() {
        valueLastRestart = value
    }
}
