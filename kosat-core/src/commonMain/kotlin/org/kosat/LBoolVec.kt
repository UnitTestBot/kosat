package org.kosat

import kotlin.math.max

/**
 * A growable vector of [LBool] values.
 *
 * Similar to [LitVec], it is backed by a [ByteArray] and grows exponentially,
 * allowing for efficient memory usage and direct access to the underlying
 * array.
 */
class LBoolVec private constructor(
    var raw: ByteArray,
    var size: Int = raw.size,
) {
    val capacity: Int get() = raw.size

    constructor() : this(ByteArray(0))

    operator fun get(index: Int): LBool {
        return LBool(raw[index])
    }

    operator fun set(index: Int, value: LBool) {
        raw[index] = value.inner
    }

    fun add(value: LBool) {
        if (size == capacity) {
            raw = raw.copyOf(max(capacity * 2, 16))
        }
        raw[size++] = value.inner
    }

    fun toList(): List<LBool> {
        return raw.take(size).map { LBool(it) }
    }
}

operator fun LBoolVec.get(variable: Var): LBool {
    return this[variable.index]
}

operator fun LBoolVec.set(variable: Var, value: LBool) {
    this[variable.index] = value
}

operator fun LBoolVec.get(lit: Lit): LBool {
    return this[lit.inner]
}

operator fun LBoolVec.set(lit: Lit, value: LBool) {
    this[lit.inner] = value
}
