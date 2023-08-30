package org.kosat

fun <T> MutableList<T>.swap(i: Int, j: Int) {
    this[i] = this[j].also { this[j] = this[i] }
}

fun IntArray.swap(i: Int, j: Int) {
    this[i] = this[j].also { this[j] = this[i] }
}

fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}

fun Boolean.toInt(): Int {
    return if (this) 1 else 0
}

fun <T> MutableList<T>.retainFirst(n: Int) {
    require(n <= this.size)
    while (this.size > n) {
        this.removeLast()
    }
}

fun ClauseVec.retainFirst(n: Int) {
    require(n <= this.size)
    while (this.size > n) {
        this.removeLast()
    }
}
