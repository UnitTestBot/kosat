package org.kosat

class Statistics {
    var conflicts = Statistic(true)
    var decisions = Statistic(true)
    var propagations = Statistic(true)
    var learned = Statistic(true)
    var deleted = Statistic(true)

    var flp = object {
        var probes = Statistic(true)
        var propagations = Statistic(true)
        var hyperBinaries = Statistic(true)
        var unitLiterals = Statistic(true)
    }
}

class Statistic(val logging: Boolean, var value: Int = 0) {
    inline fun inc(crossinline lazyMessage: () -> String? = { null }) {
        value++
        if (logging) lazyMessage()?.let { println(it) }
    }

    operator fun inc(): Statistic {
        inc { null }
        return this
    }
}
