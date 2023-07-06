package org.kosat

typealias Lit = Int

// literals - lit = var * 2, -lit = var * 2 + 1
fun negative(v: Int) = v * 2 + 1
fun positive(v: Int) = v * 2

class Clause(val lits: MutableList<Lit>): MutableList<Lit> by lits {
    var deleted = false
    var lbd = 0
}
