package org.kosat

import kotlin.jvm.JvmInline
import kotlin.math.abs

/**
 * A (lifted) boolean value in the solver.
 */
@JvmInline
value class LBool internal constructor(val inner: Byte) {
    companion object {
        val FALSE: LBool = LBool(0b01)
        val TRUE: LBool = LBool(0b10)
        val UNDEF: LBool = LBool(0b00)

        fun from(b: Boolean): LBool = from(1 + b.toInt())
        private fun from(i: Int): LBool = LBool(i.toByte())
    }

    operator fun not(): LBool {
        val x = this.inner.toInt()
        return from(0b11 and ((x shl 1) or (x shr 1)))
    }

    infix fun xor(b: Boolean): LBool {
        val x = this.inner.toInt()
        val y = b.toInt()
        return from(0b11 and ((x shl y) or (x shr y)))
    }
}

/**
 * An opaque and safe type representing a literal in the solver, wrapper around [Int].
 * Literal can be used as a list index, using [Lit.inner].
 * @param inner - the inner integer representation of the literal, index in the list
 */
@JvmInline
value class Lit(val inner: Int) {
    init {
        // require(inner >= 0) {
        //     "The internal representation of a literal is a positive integer, " +
        //         "but $inner was provided. " +
        //         "Consider using Lit.fromDimacs(lit) instead."
        // }
    }

    /** A negation of this literal */
    val neg: Lit get() = Lit(inner xor 1)

    /** A variable of this literal */
    val variable: Var get() = Var(inner shr 1)

    /** Is this a positive literal (i.e. not a negation of a variable)? */
    val isPos: Boolean get() = (inner and 1) == 0

    /** Is this a negative literal (i.e. a negation of a variable)? */
    val isNeg: Boolean get() = (inner and 1) == 1

    fun toDimacs(): Int {
        val v = (inner shr 1) + 1 // 1-based variable index
        return if (isPos) v else -v
    }

    infix fun xor(b: Boolean): Lit {
        return Lit(inner xor b.toInt())
    }

    companion object {
        fun fromDimacs(lit: Int): Lit {
            val v = abs(lit) - 1 // 0-based variables index
            val sign = if (lit < 0) 1 else 0 // sign ("is negative")
            return Lit((v shl 1) + sign)
        }
    }
}

operator fun <T> List<T>.get(lit: Lit): T {
    return this[lit.inner]
}

operator fun <T> MutableList<T>.set(lit: Lit, value: T) {
    this[lit.inner] = value
}

operator fun IntArray.get(lit: Lit): Int {
    return this[lit.inner]
}

operator fun IntArray.set(lit: Lit, value: Int) {
    this[lit.inner] = value
}

operator fun BooleanArray.get(lit: Lit): Boolean {
    return this[lit.inner]
}

operator fun BooleanArray.set(lit: Lit, value: Boolean) {
    this[lit.inner] = value
}

operator fun DoubleArray.get(lit: Lit): Double {
    return this[lit.inner]
}

operator fun DoubleArray.set(lit: Lit, value: Double) {
    this[lit.inner] = value
}

operator fun ByteArray.get(lit: Lit): Byte {
    return this[lit.inner]
}

operator fun ByteArray.set(lit: Lit, value: Byte) {
    this[lit.inner] = value
}

/**
 * An opaque and safe type representation of a variable **index** in the solver.
 */
@JvmInline
value class Var(val index: Int) {
    /** A literal of that variable */
    val posLit: Lit get() = Lit(index shl 1)

    /** A literal of negation of that variable */
    val negLit: Lit get() = Lit((index shl 1) or 1)
}

operator fun <T> List<T>.get(variable: Var): T {
    return this[variable.index]
}

operator fun <T> MutableList<T>.set(variable: Var, value: T) {
    this[variable.index] = value
}

operator fun IntArray.get(variable: Var): Int {
    return this[variable.index]
}

operator fun IntArray.set(variable: Var, value: Int) {
    this[variable.index] = value
}

operator fun BooleanArray.get(variable: Var): Boolean {
    return this[variable.index]
}

operator fun BooleanArray.set(variable: Var, value: Boolean) {
    this[variable.index] = value
}

operator fun DoubleArray.get(variable: Var): Double {
    return this[variable.index]
}

operator fun DoubleArray.set(variable: Var, value: Double) {
    this[variable.index] = value
}

operator fun ByteArray.get(variable: Var): Byte {
    return this[variable.index]
}

operator fun ByteArray.set(variable: Var, value: Byte) {
    this[variable.index] = value
}

/**
 * The result of a solver run.
 */
enum class SolveResult {
    UNKNOWN, SAT, UNSAT
}
