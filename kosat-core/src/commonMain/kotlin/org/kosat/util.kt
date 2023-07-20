package org.kosat

fun <T> MutableList<T>.swap(i: Int, j: Int) {
    this[i] = this[j].also { this[j] = this[i] }
}

fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}

/**
 * Takes a list of literals, sorts it and removes duplicates in place,
 * then checks if the list contains a literal and its negation
 * and returns true if so.
 */
fun sortDedupAndCheckComplimentary(lits: MutableList<Lit>): Boolean {
    lits.sortBy { it.inner }

    var i = 0
    for (j in 1 until lits.size) {
        if (lits[i] == lits[j].neg) return true
        if (lits[i] != lits[j]) {
            i++
            lits[i] = lits[j]
        }
    }

    while (lits.size > i + 1) {
        lits.removeLast()
    }

    return false
}
