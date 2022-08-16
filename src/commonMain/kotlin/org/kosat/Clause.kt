package org.kosat

// TODO: value class
typealias Lit = Int

class Clause(val lits: MutableList<Lit>): MutableList<Lit> by lits {
    var deleted = false
    var lbd = 0
}
