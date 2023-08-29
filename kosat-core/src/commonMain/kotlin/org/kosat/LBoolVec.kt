package org.kosat

import kotlin.math.max

/**
 * A growable vector of [LBool] values.
 *
 * Similar to [LitVec], it is backed by a [ByteArray] and grows exponentially,
 * allowing for efficient memory usage and direct access to the underlying
 * array.
 */
class LBoolVec(var raw: ByteArray, var size: Int) {
    val capacity: Int get() = raw.size

    constructor() : this(ByteArray(0), 0)

    operator fun get(v: Var): LBool {
        return LBool(raw[v.index])
    }

    operator fun set(v: Var, value: LBool) {
        raw[v.index] = value.inner
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