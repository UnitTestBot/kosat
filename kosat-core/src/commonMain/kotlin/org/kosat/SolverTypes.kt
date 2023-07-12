package org.kosat

import kotlin.jvm.JvmInline
import kotlin.math.absoluteValue

/**
 * A boolean value in the solver
 */
enum class LBool {
    TRUE, FALSE, UNDEFINED;

    operator fun not(): LBool {
        return when (this) {
            TRUE -> FALSE
            FALSE -> TRUE
            UNDEFINED -> UNDEFINED
        }
    }
}

/**
 * A state of the variable in the solver
 * TODO: document invariants of that type properly
 */
data class VarState(
    /** The assumed value of the variable */
    var value: LBool,

    /** A clause which lead to assigning a value to this variable during [CDCL.propagate] */
    var reason: Clause?,

    /** Level of decision on which the variable was assigned a value */
    var level: Int,

    /** Index of the variable in the trail */
    var trailIndex: Int = -1,
)

/**
 * An opaque and safe type representing a literal in the solver, wrapper around [Int].
 * Literal can be used as a list index, using [Lit.ord], unless it is [Lit.UNDEF].
 * @param ord - the inner integer representation of the literal, index in the list
 */
@JvmInline
value class Lit(val ord: Int) {
    companion object {
        val UNDEF = Lit(-1)

        fun fromExternal(lit: Int): Lit {
            return Lit((lit.absoluteValue - 1 shl 1) + if (lit < 0) 1 else 0)
        }
    }

    /** A negation of this literal */
    val neg get() = Lit(ord xor 1)

    /** A variable of this literal */
    val variable get() = Var(ord shr 1)

    /** Is this a positive literal (Not a negation of a variable)? */
    val isPos get() = (ord and 1) == 0

    /** Is this a negative literal (Negation of a variable)? */
    val isNeg get() = (ord and 1) == 1

    /** Is the literal [Lit.UNDEF] or [Var.posLit]/[Var.negLit] of [Var.UNDEF] */
    val isUndef get() = ord < 0
}

operator fun <T> List<T>.get(lit: Lit): T {
    return this[lit.ord]
}

operator fun <T> MutableList<T>.set(lit: Lit, value: T) {
    this[lit.ord] = value
}

/**
 * An opaque and safe type representation of a variable **index** in the solver.
 * @see VarState
 */
@JvmInline
value class Var(val ord: Int) {
    companion object {
        val UNDEF = Var(-1)
    }

    /** A literal of that variable */
    val posLit get() = Lit(ord shl 1)

    /** A literal of negation of that variable */
    val negLit get() = Lit((ord shl 1) or 1)

    /** Is the variable [Var.UNDEF]? */
    val isUndef get() = ord < 0
}

operator fun <T> List<T>.get(variable: Var): T {
    return this[variable.ord]
}

operator fun <T> MutableList<T>.set(variable: Var, value: T) {
    this[variable.ord] = value
}

/**
 * The result of a solver run.
 */
enum class SolveResult {
    UNKNOWN, SAT, UNSAT
}
