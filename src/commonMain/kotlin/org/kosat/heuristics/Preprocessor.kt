package org.kosat.heuristics

import org.kosat.CDCL
import org.kosat.Clause
import kotlin.math.abs

class Preprocessor(private val clauses: MutableList<Clause>, private var varsNumber: Int) {
    init {
        removeTautologies()
        removeSubsumedClauses()
        bve()
        removeSubsumedClauses()
        println("$varsNumber, ${clauses.size}")
        // clauses.forEach { println(it) }
        // removePureLiterals()
    }

    private val oldNumeration = MutableList(varsNumber + 1) { index -> index }
    private var startClauses = listOf<MutableList<Int>>()
    private var startOccurrence = listOf<MutableList<Int>>()
    private val deletingOrder = mutableListOf<Int>()
    private val isClauseDeleted = MutableList(clauses.size) { false }
    private val clauseLimit = 600
    private val hash = LongArray(2 * varsNumber + 1) { 1L.shl(it % 64) }

    private fun bve() {
        val isLiteralRemoved = MutableList(varsNumber + 1) { false }
        val newNumeration = MutableList(varsNumber + 1) { 0 }
        var currentInd = 1
        while (clauses.size < clauseLimit && currentInd <= varsNumber) {
            if (litOccurrence[litPos(currentInd)].size * litOccurrence[litPos(-currentInd)].size <= clauseLimit) {
                isLiteralRemoved[currentInd] = true
                deletingOrder.add(currentInd)
                addResolvents(currentInd)
            }
            currentInd++
        }
        val deletedClauses = mutableListOf<Int>()
        clauses.forEachIndexed { ind, clause ->
            for (lit in clause) {
                if (isLiteralRemoved[abs(lit)]) { //TODO: litIndex
                    deletedClauses.add(ind)
                    break
                }
            }
        }
        startClauses = clauses.map { it.lits.toMutableList() }
        startOccurrence = litOccurrence.map { it }
        clauses.removeAll(deletedClauses.map { clauses[it] })
        var newSize = 0
        for (ind in 1..varsNumber) {
            if (!isLiteralRemoved[ind]) {
                newSize++
                newNumeration[ind] = newSize
                oldNumeration[newSize] = ind
            }
        }
        for (clause in clauses) {
            clause.forEachIndexed { ind, lit ->
                if (lit > 0) {
                    clause[ind] = newNumeration[lit]
                } else {
                    clause[ind] = -newNumeration[-lit]
                }
            }
        }
        varsNumber = newSize
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
                val newClause = clauses[cl1].toMutableSet()
                newClause.remove(ind)
                // check if clause is tautology
                if (clauses[cl2].any { newClause.contains(-it) }) {
                    continue
                }
                newClause.addAll(clauses[cl2])
                newClause.remove(-ind)
                clauses.add(Clause(newClause.toMutableList()))
                isClauseDeleted.add(false)
                for (lit in newClause) {
                    litOccurrence[litPos(lit)].add(clauses.lastIndex)
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
        val markedClauses = MutableList(clauses.size) { false }

        // going from the end because smaller clauses appear after big one
        for (ind in clauses.lastIndex downTo 0) {
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

        val copiedClauses = duplicateClauses.map { clauses[it] }

        clauses.removeAll(uselessClauses.map { clauses[it] })
        // remove duplicate clauses and leave 1 copy of each
        clauses.removeAll(copiedClauses)
        clauses.addAll(copiedClauses)
        countOccurrence()
        updateSig()
    }

    private fun removeTautologies() {
        val isClauseRemoved = MutableList(clauses.size) { false }
        for (lit in 1..varsNumber) {
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
        clauses.removeAll(deletedClauses.map { clauses[it] })

        countOccurrence()
        updateSig()
    }


    // for each literal provides a list of clauses containing it (for 'x' it's in pos x, for 'not x' in pos varsNumber + x)
    private var litOccurrence = mutableListOf<MutableList<Int>>()
    // vars.mapIndexed { ind, _ -> clauses.mapIndexed { ind, _ -> ind}.filter { clauses[it].contains(ind) || clauses[it].contains(-ind) }.toMutableList()}

    // return position of literal in occurrence array
    private fun litPos(lit: Int): Int {
        return if (lit >= 0) {
            lit
        } else {
            varsNumber - lit
        }
    }

    private fun countOccurrence() {
        litOccurrence.clear()
        for (ind in 1..(2 * varsNumber + 1)) {
            litOccurrence.add(mutableListOf())
        }
        clauses.forEachIndexed { ind, clause ->
            for (lit in clause) {
                if (lit > 0) {
                    litOccurrence[lit].add(ind)
                } else {
                    litOccurrence[varsNumber - lit].add(ind)
                }
            }
        }
    }

    private fun countSig(clause: Int): Long {
        var sz = 0L
        clauses[clause].forEach { lit -> sz = sz.or(hash[litPos(lit)]) }
        return sz
    }

    private fun Int.clauseSize() = clauses[this].size

    private var clauseSig = mutableListOf<Long>()

    private fun updateSig() {
        clauseSig = clauses.mapIndexed { ind, _ -> countSig(ind) }.toMutableList()
    }

    private fun findSubsumed(clause: Int): Set<Int> {
        val lit = clauses[clause].minByOrNull { lit -> litOccurrence[abs(lit)].size } ?: 0 //TODO litIndex
        return litOccurrence[litPos(lit)].filter {
            clause != it && clause.clauseSize() <= it.clauseSize() && subset(clause, it)
        }.toSet()
    }

    private fun subset(cl1: Int, cl2: Int): Boolean {
        return if (clauseSig[cl2].or(clauseSig[cl1]) != clauseSig[cl2]) {
            false
        } else {
            clauses[cl2].containsAll(clauses[cl1])
        }
    }

    // recover answer in terms of initial variables
    fun recoverAnswer(vars: List<CDCL.VarState>) {
        // updating vars for bve
        val oldStatus = oldNumeration.mapIndexed { ind, _ -> vars[ind].status }
        for (ind in 1..varsNumber) {
            vars[ind].status = CDCL.VarStatus.UNDEFINED
        }
        for (ind in 1..varsNumber) {
            vars[oldNumeration[ind]].status = oldStatus[ind]
            if (vars[oldNumeration[ind]].status == CDCL.VarStatus.UNDEFINED) {
                vars[oldNumeration[ind]].status = CDCL.VarStatus.TRUE
            }
        }
        varsNumber += deletingOrder.size
        for (ind in deletingOrder.reversed()) {
            var allTrue = true
            for (clause in startOccurrence[ind]) {
                var isTrue = false
                for (lit in startClauses[clause]) {
                    if (lit == ind) {
                        continue
                    }
                    //FIXME remove
                    fun getStatus(lit: Int): CDCL.VarStatus {
                        if (vars[abs(lit)].status == CDCL.VarStatus.UNDEFINED) return CDCL.VarStatus.UNDEFINED
                        if (lit < 0) return !vars[-lit].status
                        return vars[lit].status
                    }
                    if (getStatus(lit) != CDCL.VarStatus.FALSE) { //FIXME
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
                vars[ind].status = CDCL.VarStatus.FALSE
            } else {
                vars[ind].status = CDCL.VarStatus.TRUE
            }
        }
    }
}
