package org.kosat

fun <T> MutableList<T>.swap(i: Int, j: Int) {
    this[i] = this[j].also { this[j] = this[i] }
}

fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}

fun <T : Comparable<T>> MutableList<T>.sortAndFilterUnique() {
    this.sort()
    var uniquePrefixSize = 0
    for (i in 1 until this.size) {
        if (this[i] != this[uniquePrefixSize]) {
            this[uniquePrefixSize++] = this[i]
        }
    }
}
