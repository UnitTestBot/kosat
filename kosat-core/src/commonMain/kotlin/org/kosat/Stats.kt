package org.kosat

import okio.BufferedSink

class Stats {
    var decisions: Int = 0
    var conflicts: Int = 0
    var propagations: Long = 0
    var unitsLearned: Int = 0
    var restarts: Int = 0

    class FlpStats {
        var probes: Int = 0
        var probesFailed: Int = 0
        var hbrResolvents: Int = 0
    }

    val flp = FlpStats()

    class ElsStats {
        var substitutions: Int = 0
    }

    val els = ElsStats()

    class BveStats {
        var eliminationAttempts = 0
        var eliminatedVariables = 0
        var resolventsAdded = 0
        var clausesResolved = 0
        var unitsAssigned = 0
        var clausesAttached = 0
        var clausesDeleted = 0
        var clausesStrengthened = 0
        var clausesSubsumed = 0
        var tautologicalResolvents = 0
        var resolventsTooBig = 0
        var gatesFound = 0
    }

    val bve = BveStats()

    fun dump(sink: BufferedSink) {
        fun line(name: String, value: Any?) {
            sink.writeUtf8("c ${name.padStart(42)}: $value\n")
        }

        line("Solver statistics", "")
        line("decisions", decisions)
        line("conflicts", conflicts)
        line("propagations", propagations)
        line("units learned", unitsLearned)
        line("restarts", restarts)

        line("Failed Literal Probing", "")
        line("probes", flp.probes)
        line("probes failed", flp.probesFailed)
        line("hyper-binary-resolvents", flp.hbrResolvents)

        line("Equivalent Literal Substitution", "")
        line("substitutions", els.substitutions)

        line("Bounded Variable Elimination", "")
        line("variable elimination attempts", bve.eliminationAttempts)
        line("eliminated variables by distribution", bve.eliminatedVariables)
        line("resolvents added", bve.resolventsAdded)
        line("resolved clauses deleted", bve.clausesResolved)
        line("total units assigned", bve.unitsAssigned)
        line("total clause database additions", bve.clausesAttached)
        line("total clause database deletions", bve.clausesDeleted)
        line("clauses strengthened", bve.clausesStrengthened)
        line("clauses removed as subsumed", bve.clausesSubsumed)
        line("resolvents ignored as tautological", bve.tautologicalResolvents)
        line("failed resolutions due to big resolvents", bve.resolventsTooBig)
        line("gates found", bve.gatesFound)
    }
}
