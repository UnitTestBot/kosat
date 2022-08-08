package org.kosat.heuristics

import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.VarStatus
import kotlin.math.abs

class Preprocessor(private val solver: CDCL) {

    private val oldNumeration = MutableList(solver.numberOfVariables + 1) { index -> index }
    private var startClauses = listOf<Clause>()
    private var startOccurrence = listOf<MutableList<Int>>()
    private val deletingOrder = mutableListOf<Int>()
    private val isClauseDeleted = MutableList(solver.constraints.size) { false }
    private val clauseLimit = 600
    private val hash = LongArray(2 * solver.numberOfVariables + 1) { 1L.shl(it % 64) }

    fun addClause(clause: Clause) {
        clause.forEach { lit -> litOccurrence[abs(lit)].add(solver.constraints.lastIndex) } // todo litIndex
        clauseSig.add(countSig(solver.constraints.lastIndex))
    }

    // for each literal provides a list of clauses containing it (for 'x' it's in pos x, for 'not x' in pos varsNumber + x)
    private var litOccurrence: MutableList<MutableList<Int>> = mutableListOf()

    init {
        countOccurrence()
        updateSig()
        removeTautologies()
        removeSubsumedClauses()
        bve()
        removeSubsumedClauses()
        println("${solver.numberOfVariables}, ${solver.constraints.size}")
        // clauses.forEach { println(it) }
        // removePureLiterals()
    }


    private fun bve() {
        val isLiteralRemoved = MutableList(solver.numberOfVariables + 1) { false }
        val newNumeration = MutableList(solver.numberOfVariables + 1) { 0 }
        var currentInd = 1
        while (solver.constraints.size < clauseLimit && currentInd <= solver.numberOfVariables) {
            if (litOccurrence[litPos(currentInd)].size * litOccurrence[litPos(-currentInd)].size <= clauseLimit) {
                isLiteralRemoved[currentInd] = true
                deletingOrder.add(currentInd)
                addResolvents(currentInd)
            }
            currentInd++
        }
        val deletedClauses = mutableListOf<Int>()
        solver.constraints.forEachIndexed { ind, clause ->
            for (lit in clause) {
                if (isLiteralRemoved[abs(lit)]) { // TODO: litIndex
                    deletedClauses.add(ind)
                    break
                }
            }
        }
        startClauses = solver.constraints.map { it } // TODO: Clauses..
        startOccurrence = litOccurrence.map { it }
        solver.constraints.removeAll(deletedClauses.map { solver.constraints[it] })
        var newSize = 0
        for (ind in 1..solver.numberOfVariables) {
            if (!isLiteralRemoved[ind]) {
                newSize++
                newNumeration[ind] = newSize
                oldNumeration[newSize] = ind
            }
        }
        for (clause in solver.constraints) {
            clause.forEachIndexed { ind, lit ->
                if (lit > 0) {
                    clause[ind] = newNumeration[lit]
                } else {
                    clause[ind] = -newNumeration[-lit]
                }
            }
        }
        solver.numberOfVariables = newSize
        countOccurrence()
        updateSig()
    }


    private fun addResolvents(ind: Int) {
        for (cl1 in litOccurrence[litPos(ind)]) {
            if (isClauseDeleted[cl1]) {
                continue
            }
            for (cl2 in litOccurrence[litPos(-ind)]) {
                if (isClauseDeleted[cl2]) {
                    continue
                }
                val newClause = solver.constraints[cl1].toMutableSet()
                newClause.remove(ind)
                // check if clause is tautology
                if (solver.constraints[cl2].any { newClause.contains(-it) }) {
                    continue
                }
                newClause.addAll(solver.constraints[cl2])
                newClause.remove(-ind)
                solver.constraints.add(Clause(newClause.toMutableList()))
                isClauseDeleted.add(false)
                for (lit in newClause) {
                    litOccurrence[litPos(lit)].add(solver.constraints.lastIndex)
                }
            }
        }
        for (clause in litOccurrence[litPos(ind)]) {
            isClauseDeleted[clause] = true
        }
        for (clause in litOccurrence[litPos(-ind)]) {
            isClauseDeleted[clause] = true
        }
    }

    // remove subsumed clauses
    private fun removeSubsumedClauses() {
        val uselessClauses = mutableSetOf<Int>()
        val duplicateClauses = mutableSetOf<Int>()
        val markedClauses = MutableList(solver.constraints.size) { false }

        // going from the end because smaller clauses appear after big one
        for (ind in solver.constraints.lastIndex downTo 0) {
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

        val copiedClauses = duplicateClauses.map { solver.constraints[it] }

        solver.constraints.removeAll(uselessClauses.map { solver.constraints[it] })
        // remove duplicate clauses and leave 1 copy of each
        solver.constraints.removeAll(copiedClauses)
        solver.constraints.addAll(copiedClauses)
        countOccurrence()
        updateSig()
    }

    private fun removeTautologies() {
        val isClauseRemoved = MutableList(solver.constraints.size) { false }
        for (lit in 1..solver.numberOfVariables) {
            val sz1 = litOccurrence[litPos(lit)].size
            val sz2 = litOccurrence[litPos(-lit)].size
            if (sz1 == 0 || sz2 == 0) {
                continue
            }
            var ind1 = 0
            var ind2 = 0
            while (ind2 < sz2) {
                while (ind1 < sz1 && litOccurrence[litPos(lit)][ind1] < litOccurrence[litPos(-lit)][ind2]) {
                    ind1++
                }
                if (ind1 == sz1) {
                    break
                }
                if (litOccurrence[litPos(lit)][ind1] == litOccurrence[litPos(-lit)][ind2]) {
                    isClauseRemoved[litOccurrence[litPos(lit)][ind1]] = true
                }
                ind2++
            }
        }
        val deletedClauses = mutableListOf<Int>()
        isClauseRemoved.forEachIndexed { ind, isDeleted ->
            if (isDeleted) {
                deletedClauses.add(ind)
            }
        }
        solver.constraints.removeAll(deletedClauses.map { solver.constraints[it] })

        countOccurrence()
        updateSig()
    }

    // return position of literal in occurrence array
    private fun litPos(lit: Int): Int {
        return if (lit >= 0) {
            lit
        } else {
            solver.numberOfVariables - lit
        }
    }

    private fun countOccurrence() {
        litOccurrence = mutableListOf()
        // litOccurrence.clear()
        for (ind in 1..(2 * solver.numberOfVariables + 1)) {
            litOccurrence.add(mutableListOf())
        }
        solver.constraints.forEachIndexed { ind, clause ->
            for (lit in clause) {
                if (lit > 0) {
                    litOccurrence[lit].add(ind)
                } else {
                    litOccurrence[solver.numberOfVariables - lit].add(ind)
                }
            }
        }
    }

    private fun countSig(clause: Int): Long {
        var sz = 0L
        solver.constraints[clause].forEach { lit -> sz = sz.or(hash[litPos(lit)]) }
        return sz
    }

    private fun Int.clauseSize() = solver.constraints[this].size

    private var clauseSig = mutableListOf<Long>()

    private fun updateSig() {
        clauseSig = List(solver.constraints.size) { ind -> countSig(ind) }.toMutableList()
    }

    // TODO: add docs
    private fun findSubsumed(clause: Int): Set<Int> {
        val lit = solver.constraints[clause].minByOrNull { lit -> litOccurrence[litPos(lit)].size } ?: 0 // TODO litIndex
        return litOccurrence[litPos(lit)].filter {
            clause != it && clause.clauseSize() <= it.clauseSize() && isSubset(clause, it)
        }.toSet()
    }

    private fun isSubset(cl1: Int, cl2: Int): Boolean {
        return if (clauseSig[cl2].or(clauseSig[cl1]) != clauseSig[cl2]) {
            false
        } else {
            solver.constraints[cl2].containsAll(solver.constraints[cl1])
        }
    }

    // recover answer in terms of initial variables
    fun recoverAnswer() {
        // updating vars for bve
        val oldStatus = List(oldNumeration.size) { ind -> solver.vars[ind].status }
        for (ind in 1..solver.numberOfVariables) {
            solver.vars[ind].status = VarStatus.UNDEFINED
        }
        for (ind in 1..solver.numberOfVariables) {
            solver.vars[oldNumeration[ind]].status = oldStatus[ind]
            if (solver.vars[oldNumeration[ind]].status == VarStatus.UNDEFINED) {
                solver.vars[oldNumeration[ind]].status = VarStatus.TRUE
            }
        }
        solver.numberOfVariables += deletingOrder.size
        for (ind in deletingOrder.reversed()) {
            var allTrue = true
            for (clause in startOccurrence[ind]) {
                var isTrue = false
                for (lit in startClauses[clause]) {
                    if (lit == ind) {
                        continue
                    }
                    if (solver.getStatus(lit) != VarStatus.FALSE) {
                        isTrue = true
                        break
                    }
                }
                if (!isTrue) {
                    allTrue = false
                    break
                }
            }
            if (allTrue) {
                solver.vars[ind].status = VarStatus.FALSE
            } else {
                solver.vars[ind].status = VarStatus.TRUE
            }
        }
    }
}
