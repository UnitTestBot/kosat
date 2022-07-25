package org.kosat

class NonIncremental(initClauses: MutableList<MutableList<Int>>, initNumber: Int = 0) : CDCL(initClauses, initNumber) {

    private val lubySeq: MutableList<Int> = mutableListOf(1)

    private val u = 100.0

    private var curr = 1

    init {
        var pw = 1
        while (lubySeq.size < 1e5) {
            pw *= 2
            lubySeq.addAll(lubySeq)
            lubySeq.add(pw)
        }
    }

    override fun getNextVariable(level: Int): Int = vsids()

    override fun solve(): List<Int>? {

        countOccurrence()
        updateSig()

        // simplifying given cnf formula
        preprocessing()

        countScore()

        // extremal cases
        if (clauses.isEmpty()) return emptyList()
        if (clauses.any { it.size == 0 }) return null

        buildWatchers()

        // main loop
        while (true) {
            val conflictClause = propagate()
            if (conflictClause != -1) {
                if (level == 0) return null // in case there is a conflict in CNF
                addLemma(conflictClause)

                numberOfConflictsAfterRestart++
                // restarting after some number of conflicts
                if (numberOfConflictsAfterRestart >= restartNumber) {
                    numberOfConflictsAfterRestart = 0
                    makeRestart()
                }

                continue
            }

            // If (the problem is already) SAT, return the current assignment
            if (satisfiable()) {
                return variableValues()
            }

            // try to guess variable
            level++
            // addVariable(-1, vars.firstUndefined())
            addVariable(-1, vsids())
        }
    }

    // add clause and add watchers to it
    override fun addClause(clause: MutableList<Int>) {
        clauses.add(clause)
        addWatchers(clause, clauses.lastIndex)

        // add clause to litOccurrence
        clause.forEach { lit -> litOccurrence[litIndex(lit)].add(clauses.lastIndex) }
        clauseSig.add(countSig(clauses.lastIndex))
    }

    // preprocessing
    private fun preprocessing() {
        removeTautologies()
        removeSubsumedClauses()
        bve()
        println("$varsNumber, ${clauses.size}")
        // clauses.forEach { println(it) }
        // removePureLiterals()
    }

    private var restartNumber = 500.0
    //private val restartCoeff = 1.1
    private val hash = LongArray(2 * varsNumber + 1) { 1L.shl(it % 64) }
    private var numberOfConflictsAfterRestart = 0
    private var numberOfRestarts = 0

    // for each literal provides a list of clauses containing it (for 'x' it's in pos x, for 'not x' in pos varsNumber + x)
    // vars.mapIndexed { ind, _ -> clauses.mapIndexed { ind, _ -> ind}.filter { clauses[it].contains(ind) || clauses[it].contains(-ind) }.toMutableList()}
    private var litOccurrence = mutableListOf<MutableList<Int>>()

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
        val lit = clauses[clause].minByOrNull { lit -> litOccurrence[litIndex(lit)].size } ?: 0
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

    // removes subsumed clauses
    private fun removeSubsumedClauses() {
        val uselessClauses = mutableSetOf<Int>()
        val markedClauses = MutableList(clauses.size) { false }

        // going from the end because smaller clauses appear after big one
        for (ind in clauses.lastIndex downTo 0) {
            if (!markedClauses[ind]) {
                findSubsumed(ind).forEach {
                    if (!markedClauses[it]) {
                        markedClauses[it] = true
                        uselessClauses.add(it)
                    }
                }
            }
        }

        clauses.removeAll(uselessClauses.map { clauses[it] })
        countOccurrence()
        updateSig()
    }

    // making restart to remove useless clauses
    private fun makeRestart() {
        numberOfRestarts++
        restartNumber = u * lubySeq[curr++]
        level = 0
        trail.clear()
        units.clear()
        watchers.forEach { it.clear() }
        vars.forEachIndexed { ind, _ ->
            delVariable(ind)
        }

        removeSubsumedClauses()
        countOccurrence()

        updateSig()

        buildWatchers()
    }

    private val clauseLimit = 1000

    private fun addResolvents(ind: Int) {
        for (cl1 in litOccurrence[litPos(ind)]) {
            for (cl2 in litOccurrence[litPos(-ind)]) {
                val newClause = clauses[cl1].toMutableSet()
                newClause.remove(ind)
                // check if clause is tautology
                if (clauses[cl2].any { newClause.contains(-it) }) {
                    continue
                }
                newClause.addAll(clauses[cl2])
                newClause.remove(-ind)
                clauses.add(ArrayList(newClause))
                for (lit in newClause) {
                    litOccurrence[litPos(lit)].add(clauses.lastIndex)
                }
            }
        }
    }

    private fun bve() {
        val isLiteralRemoved = MutableList(varsNumber + 1) { false }
        val newNumeration = MutableList(varsNumber + 1) { 0 }
        var currentInd = 1
        while (clauses.size < clauseLimit && currentInd <= varsNumber) {
            if (litOccurrence[litPos(currentInd)].size * litOccurrence[litPos(-currentInd)].size <= clauseLimit) {
                isLiteralRemoved[currentInd] = true
                addResolvents(currentInd)
            }
            currentInd++
        }
        val deletedClauses = mutableListOf<Int>()
        clauses.forEachIndexed { ind, clause ->
            for (lit in clause) {
                if (isLiteralRemoved[litIndex(lit)]) {
                    deletedClauses.add(ind)
                    break
                }
            }
        }
        clauses.removeAll(deletedClauses.map { clauses[it] })
        var newSize = 0
        for (ind in 1..varsNumber) {
            if (!isLiteralRemoved[ind]) {
                newSize++
                newNumeration[ind] = newSize
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
}
