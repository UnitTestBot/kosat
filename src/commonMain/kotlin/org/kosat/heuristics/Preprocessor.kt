package org.kosat.heuristics

import org.kosat.CDCL
import kotlin.math.abs

class Preprocessor(private val solver: CDCL) {

    private val oldNumeration = MutableList(solver.varsNumber + 1) { index -> index }
    private var startClauses = listOf<MutableList<Int>>()
    private var startOccurrence = listOf<MutableList<Int>>()
    private val deletingOrder = mutableListOf<Int>()
    private val isClauseDeleted = MutableList(solver.clauses.size) { false }
    private val clauseLimit = 600
    private val hash = LongArray(2 * solver.varsNumber + 1) { 1L.shl(it % 64) }

    fun addClause(clause: MutableList<Int>) {
        clause.forEach { lit -> litOccurrence[abs(lit)].add(solver.clauses.lastIndex) } //todo litIndex
        clauseSig.add(countSig(solver.clauses.lastIndex))
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
        println("${solver.varsNumber}, ${solver.clauses.size}")
        // clauses.forEach { println(it) }
        // removePureLiterals()
    }



    private fun bve() {
        val isLiteralRemoved = MutableList(solver.varsNumber + 1) { false }
        val newNumeration = MutableList(solver.varsNumber + 1) { 0 }
        var currentInd = 1
        while (solver.clauses.size < clauseLimit && currentInd <= solver.varsNumber) {
            if (litOccurrence[litPos(currentInd)].size * litOccurrence[litPos(-currentInd)].size <= clauseLimit) {
                isLiteralRemoved[currentInd] = true
                deletingOrder.add(currentInd)
                addResolvernts(currentInd)
            }
            currentInd++
        }
        val deletedClauses = mutableListOf<Int>()
        solver.clauses.forEachIndexed { ind, clause ->
            for (lit in clause) {
                if (isLiteralRemoved[abs(lit)]) { //TODO: litIndex
                    deletedClauses.add(ind)
                    break
                }
            }
        }
        startClauses = solver.clauses.map { it } // TODO: Clauses..
        startOccurrence = litOccurrence.map { it }
        solver.clauses.removeAll(deletedClauses.map { solver.clauses[it] })
        var newSize = 0
        for (ind in 1..solver.varsNumber) {
            if (!isLiteralRemoved[ind]) {
                newSize++
                newNumeration[ind] = newSize
                oldNumeration[newSize] = ind
            }
        }
        for (clause in solver.clauses) {
            clause.forEachIndexed { ind, lit ->
                if (lit > 0) {
                    clause[ind] = newNumeration[lit]
                } else {
                    clause[ind] = -newNumeration[-lit]
                }
            }
        }
        solver.varsNumber = newSize
        countOccurrence()
        updateSig()
    }


    private fun addResolvernts(ind: Int) {
        for (cl1 in litOccurrence[litPos(ind)]) {
            if (isClauseDeleted[cl1]) {
                continue
            }
            for (cl2 in litOccurrence[litPos(-ind)]) {
                if (isClauseDeleted[cl2]) {
                    continue
                }
                val newClause = solver.clauses[cl1].toMutableSet()
                newClause.remove(ind)
                // check if clause is tautology
                if (solver.clauses[cl2].any { newClause.contains(-it) }) {
                    continue
                }
                newClause.addAll(solver.clauses[cl2])
                newClause.remove(-ind)
                solver.clauses.add(newClause.toMutableList())
                isClauseDeleted.add(false)
                for (lit in newClause) {
                    litOccurrence[litPos(lit)].add(solver.clauses.lastIndex)
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

    private fun removeTautologies() {
        val isClauseRemoved = MutableList(solver.clauses.size) { false }
        for (lit in 1..solver.varsNumber) {
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
        solver.clauses.removeAll(deletedClauses.map { solver.clauses[it] })

        countOccurrence()
        updateSig()
    }

    // return position of literal in occurrence array
    private fun litPos(lit: Int): Int {
        return if (lit >= 0) {
            lit
        } else {
            solver.varsNumber - lit
        }
    }

    private fun countOccurrence() {
        litOccurrence = mutableListOf()
        //litOccurrence.clear()
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

    private fun countSig(clause: Int): Long {
        var sz = 0L
        solver.clauses[clause].forEach { lit -> sz = sz.or(hash[litPos(lit)]) }
        return sz
    }

    private fun Int.clauseSize() = solver.clauses[this].size

    private var clauseSig = mutableListOf<Long>()

    private fun updateSig() {
        clauseSig = List(solver.clauses.size) { ind -> countSig(ind) }.toMutableList()
    }

    private fun findSubsumed(clause: Int): Set<Int> {
        val lit = solver.clauses[clause].minByOrNull { lit -> litOccurrence[litPos(lit)].size } ?: 0 //TODO litIndex
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

    // recover answer in terms of initial variables
    fun recoverAnswer() {
        // updating vars for bve
        val oldStatus = List(oldNumeration.size) { ind -> solver.vars[ind].status }
        for (ind in 1..solver.varsNumber) {
            solver.vars[ind].status = CDCL.VarStatus.UNDEFINED
        }
        for (ind in 1..solver.varsNumber) {
            solver.vars[oldNumeration[ind]].status = oldStatus[ind]
            if (solver.vars[oldNumeration[ind]].status == CDCL.VarStatus.UNDEFINED) {
                solver.vars[oldNumeration[ind]].status = CDCL.VarStatus.TRUE
            }
        }
        solver.varsNumber += deletingOrder.size
        for (ind in deletingOrder.reversed()) {
            var allTrue = true
            for (clause in startOccurrence[ind]) {
                var isTrue = false
                for (lit in startClauses[clause]) {
                    if (lit == ind) {
                        continue
                    }
                    if (solver.getStatus(lit) != CDCL.VarStatus.FALSE) {
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
                solver.vars[ind].status = CDCL.VarStatus.FALSE
            } else {
                solver.vars[ind].status = CDCL.VarStatus.TRUE
            }
        }
    }
}
