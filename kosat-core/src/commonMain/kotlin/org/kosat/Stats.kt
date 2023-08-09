package org.kosat

import okio.BufferedSink

class Stats {
    /** Number of decisions made. */
    var decisions: Int = 0
    /** Number of conflicts encountered. */
    var conflicts: Int = 0
    /** Number of propagations made. */
    var propagations: Long = 0
    /** Number of units assigned at level 0. */
    var unitsFound: Int = 0
    /** Number of restarts. */
    var restarts: Int = 0

    class FlpStats {
        /** Number of total attempts to probe literal */
        var probes: Int = 0
        /** Number of failed attempts to probe literal, leading to level 0 assignment */
        var probesFailed: Int = 0
        /** Number of hyper-binary-resolvents added */
        var hbrResolvents: Int = 0
    }

    /** Statistics for [CDCL.failedLiteralProbing] */
    val flp = FlpStats()

    class ElsStats {
        /** Number of variables eliminated through substitution */
        var substitutions: Int = 0
    }

    /** Statistics for [CDCL.equivalentLiteralSubstitution] */
    val els = ElsStats()

    class BveStats {
        /** Number of total attempts to eliminate variable */
        var eliminationAttempts = 0
        /** Number of variables eliminated by distribution */
        var eliminatedVariables = 0
        /** Number of resolvents added during elimination */
        var resolventsAdded = 0
        /** Number of resolved clauses deleted during elimination */
        var clausesResolved = 0
        /** Number of units assigned during BVE */
        var unitsAssigned = 0
        /** Total number of clauses added to database */
        var clausesAttached = 0
        /** Total number of clauses deleted from database */
        var clausesDeleted = 0
        /** Number of clauses strengthened during BVE */
        var clausesStrengthened = 0
        /** Number of clauses removed as subsumed during BVE */
        var clausesSubsumed = 0
        /** Number of resolvents ignored as tautological */
        var tautologicalResolvents = 0
        /** Number of failed resolutions due to big resolvents */
        var resolventsTooBig = 0
        /** Number of gates found */
        var gatesFound = 0
    }

    /** Statistics for [CDCL.boundedVariableElimination] */
    val bve = BveStats()

    fun dump(sink: BufferedSink) {
        fun line(name: String, value: Any?) {
            sink.writeUtf8("c ${name.padStart(42)}: $value\n")
        }

        line("Solver statistics", "")
        line("decisions", decisions)
        line("conflicts", conflicts)
        line("propagations", propagations)
        line("units learned", unitsFound)
        line("restarts", restarts)

        line("Failed Literal Probing", "")
        line("probes", flp.probes)
        line("probes failed", flp.probesFailed)
        line("hyper-binary resolvents", flp.hbrResolvents)

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
