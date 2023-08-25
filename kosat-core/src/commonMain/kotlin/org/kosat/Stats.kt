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
        /** Number of full failed literal probing attempts performed */
        var rounds: Int = 0

        /** Number of total attempts to probe literal */
        var probes: Int = 0

        /** Number of failed attempts to probe literal,
         * leading to level 0 assignment */
        var probesFailed: Int = 0

        /** Number of hyper-binary-resolvents added */
        var hbrResolvents: Int = 0
    }

    /** Statistics for [CDCL.failedLiteralProbing] */
    val flp = FlpStats()

    class ElsStats {
        /** Number of full equivalent literal substitution attempts performed */
        var rounds: Int = 0

        /** Number of variables eliminated through substitution */
        var substitutions: Int = 0
    }

    /** Statistics for [CDCL.equivalentLiteralSubstitution] */
    val els = ElsStats()

    class BveStats {
        /** Number of full bounded variable elimination attempts performed */
        var rounds: Int = 0

        /** Number of total attempts to eliminate variable */
        var eliminationAttempts: Int = 0

        /** Number of variables eliminated by distribution */
        var eliminatedVariables: Int = 0

        /** Number of resolvents added during elimination */
        var resolventsAdded: Int = 0

        /** Number of resolved clauses deleted during elimination */
        var clausesResolved: Int = 0

        /** Number of units assigned during BVE */
        var unitsAssigned: Int = 0

        /** Total number of clauses added to database */
        var clausesAttached: Int = 0

        /** Total number of clauses deleted from database */
        var clausesDeleted: Int = 0

        /** Number of clauses strengthened during BVE */
        var clausesStrengthened: Int = 0

        /** Number of clauses removed as subsumed during BVE */
        var clausesSubsumed: Int = 0

        /** Number of resolvents ignored as tautological */
        var tautologicalResolvents: Int = 0

        /** Number of failed resolutions due to big resolvents */
        var resolventsTooBig: Int = 0

        /** Number of gates found */
        var gatesFound: Int = 0
    }

    /** Statistics for [CDCL.boundedVariableElimination] */
    val bve = BveStats()

    fun write(sink: BufferedSink) {
        fun line(name: String, value: Any?) {
            sink.writeUtf8("c ${name.padStart(42)}: $value\n")
        }

        fun heading(name: String, value: Any?) {
            sink.writeUtf8("c ${"-".repeat(50)}\n")
            sink.writeUtf8("c ${name.padStart(42)}: $value\n")
        }

        heading("Overall", "")
        line("decisions", decisions)
        line("conflicts", conflicts)
        line("propagations", propagations)
        line("units learned", unitsFound)
        line("restarts", restarts)

        heading("Failed Literal Probing rounds", flp.rounds)
        line("probes", flp.probes)
        line("probes failed", flp.probesFailed)
        line("hyper-binary resolvents", flp.hbrResolvents)

        heading("Equivalent Literal Substitution rounds", els.rounds)
        line("substitutions", els.substitutions)

        heading("Bounded Variable Elimination rounds", bve.rounds)
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

        sink.flush()
    }
}
