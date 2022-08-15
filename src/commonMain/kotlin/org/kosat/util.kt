package org.kosat

fun <T> MutableList<T>.swap(i: Int, j: Int) {
    this[i] = this[j].also{this[j] = this[i]}
}
