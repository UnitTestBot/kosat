package org.kosat.heuristics

import org.kosat.CDCL
import kotlin.math.abs

class Restarter(val solver: CDCL) {
    
    
    private var restartNumber = 500.0
    private val restartCoeff = 1.1

    private var numberOfConflictsAfterRestart = 0
    private var numberOfRestarts = 0

    // for each literal provides a list of clauses containing it (for 'x' it's in pos x, for 'not x' in pos solver.varsNumber + x)
    private var litOccurrence = mutableListOf<MutableList<Int>>()
    // vars.mapIndexed { ind, _ -> clauses.mapIndexed { ind, _ -> ind}.filter { clauses[it].contains(ind) || clauses[it].contains(-ind) }.toMutableList()}

    // return position of literal in occurrence array
    private fun litPos(lit: Int): Int {
        return if (lit >= 0) {
            lit
        } else {
            solver.varsNumber - lit
        }
    }

    fun countOccurrence() {
        litOccurrence.clear()
        for (ind in 1..(2 * solver.varsNumber + 1)) {
            litOccurrence.add(mutableListOf())
        }
        solver.clauses.forEachIndexed { ind, clause ->
            for (lit in clause) {
                if (lit > 0) {
                    litOccurrence[lit].add(ind)
                } else {
                    litOccurrence[solver.varsNumber - lit].add(ind)
                }
            }
        }
    }

    // remove subsumed clauses
    private fun removeSubsumedClauses() {
        val uselessClauses = mutableSetOf<Int>()
        val duplicateClauses = mutableSetOf<Int>()
        val markedClauses = MutableList(solver.clauses.size) { false }

        // going from the end because smaller clauses appear after big one
        for (ind in solver.clauses.lastIndex downTo 0) {
            if (!markedClauses[ind]) {
                findSubsumed(ind).forEach {
                    if (!markedClauses[it]) {
                        if (ind.clauseSize() < it.clauseSize()) {
                            markedClauses[it] = true
                            uselessClauses.add(it)
                        } else if (ind.clauseSize() == it.clauseSize()) {
                            duplicateClauses.add(it)
                        }
                    }
                }
            }
        }

        val copiedClauses = duplicateClauses.map { solver.clauses[it] }

        solver.clauses.removeAll(uselessClauses.map { solver.clauses[it] })
        // remove duplicate clauses and leave 1 copy of each
        solver.clauses.removeAll(copiedClauses)
        solver.clauses.addAll(copiedClauses)
        countOccurrence()
        updateSig()
    }

    private val hash = LongArray(2 * solver.varsNumber + 1) { 1L.shl(it % 64) }

    private fun countSig(clause: Int): Long {
        var sz = 0L
        solver.clauses[clause].forEach { lit -> sz = sz.or(hash[litPos(lit)]) }
        return sz
    }

    private fun Int.clauseSize() = solver.clauses[this].size

    private var clauseSig = mutableListOf<Long>()

    fun updateSig() {
        clauseSig = solver.clauses.mapIndexed { ind, _ -> countSig(ind) }.toMutableList()
    }

    private fun findSubsumed(clause: Int): Set<Int> {
        val lit = solver.clauses[clause].minByOrNull { lit -> litOccurrence[litPos(lit)].size } ?: 0
        return litOccurrence[litPos(lit)].filter {
            clause != it && clause.clauseSize() <= it.clauseSize() && subset(clause, it)
        }.toSet()
    }

    private fun subset(cl1: Int, cl2: Int): Boolean {
        return if (clauseSig[cl2].or(clauseSig[cl1]) != clauseSig[cl2]) {
            false
        } else {
            solver.clauses[cl2].containsAll(solver.clauses[cl1])
        }
    }

    fun update() {
        // Restart after adding a clause to maintain correct watchers
        numberOfConflictsAfterRestart++
        // restarting after some number of conflicts
        if (numberOfConflictsAfterRestart >= restartNumber) {
            numberOfConflictsAfterRestart = 0
            restart()
        }
    }

    fun addClause(clause: MutableList<Int>) {
        clause.forEach { lit -> litOccurrence[abs(lit)].add(solver.clauses.lastIndex) } //todo litIndex
        clauseSig.add(countSig(solver.clauses.lastIndex))
    }

    // making restart to remove useless clauses
    private fun restart() {
        numberOfRestarts++
        restartNumber *= restartCoeff
        solver.level = 0

        solver.units.clear()

        //watchers.forEach { it.clear() }
        //buildWatchers()

        solver.clearTrail(0)

        /*removeSubsumedClauses()
        countOccurrence()

        updateSig()*/
    }
}