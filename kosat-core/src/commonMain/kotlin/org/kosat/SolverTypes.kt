package org.kosat

import kotlin.jvm.JvmInline
import kotlin.math.absoluteValue

/**
 * A boolean value in the solver
 */
enum class LBool {
    FALSE, TRUE, UNDEF;

    operator fun not(): LBool = when (this) {
        FALSE -> TRUE
        TRUE -> FALSE
        UNDEF -> UNDEF
    }

    infix fun xor(b: Boolean): LBool = when (this) {
        FALSE -> from(b) // if (b) TRUE else FALSE
        TRUE -> from(!b) // if (b) FALSE else TRUE
        UNDEF -> UNDEF
    }

    fun toBool(): Boolean = when (this) {
        FALSE -> false
        TRUE -> true
        UNDEF -> error("LBool.UNDEF")
    }

    companion object {
        fun from(b: Boolean): LBool = if (b) TRUE else FALSE
    }
}

/**
 * An opaque and safe type representing a literal in the solver, wrapper around [Int].
 * Literal can be used as a list index, using [Lit.inner], unless it is [Lit.UNDEF].
 * @param inner - the inner integer representation of the literal, index in the list
 */
@JvmInline
value class Lit(val inner: Int) {
    companion object {
        val UNDEF = Lit(-1)

        fun fromExternal(lit: Int): Lit {
            return Lit((lit.absoluteValue - 1 shl 1) + if (lit < 0) 1 else 0)
        }
    }

    /** A negation of this literal */
    val neg: Lit get() = Lit(inner xor 1)

    /** A variable of this literal */
    val variable: Var get() = Var(inner shr 1)

    /** Is this a positive literal (Not a negation of a variable)? */
    val isPos: Boolean get() = (inner and 1) == 0

    /** Is this a negative literal (Negation of a variable)? */
    val isNeg: Boolean get() = (inner and 1) == 1

    /** Is the literal [Lit.UNDEF] or [Var.posLit]/[Var.negLit] of [Var.UNDEF] */
    val isUndef: Boolean get() = inner < 0
}

operator fun <T> List<T>.get(lit: Lit): T {
    return this[lit.inner]
}

operator fun <T> MutableList<T>.set(lit: Lit, value: T) {
    this[lit.inner] = value
}

/**
 * An opaque and safe type representation of a variable **index** in the solver.
 * @see VarState
 */
@JvmInline
value class Var(val index: Int) {
    companion object {
        val UNDEF = Var(-1)
    }

    /** A literal of that variable */
    val posLit: Lit get() = Lit(index shl 1)

    /** A literal of negation of that variable */
    val negLit: Lit get() = Lit((index shl 1) or 1)

    /** Is the variable [Var.UNDEF]? */
    val isUndef: Boolean get() = index < 0
}

operator fun <T> List<T>.get(variable: Var): T {
    return this[variable.index]
}

operator fun <T> MutableList<T>.set(variable: Var, value: T) {
    this[variable.index] = value
}

/**
 * The result of a solver run.
 */
enum class SolveResult {
    UNKNOWN, SAT, UNSAT
}
