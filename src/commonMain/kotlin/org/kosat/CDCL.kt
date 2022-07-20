package org.kosat

import kotlin.math.abs

// CDCL
fun solveCnf(cnf: CnfRequest): List<Int>? {
    val clauses = (cnf.clauses.map { it.lit }).toMutableList()
    val solver = Kosat(clauses)
    return if (solver.solve()) solver.getModel() else null
}

class CDCL(val clauses: MutableList<MutableList<Int>>) {
    var varsNumber = 0
        private set

    init {
        varsNumber = clauses.flatten().let { if (it.isNotEmpty()) it.maxOf { abs(it) } else 0 }
    }

    enum class VarStatus {
        TRUE, FALSE, UNDEFINED;

        operator fun not(): VarStatus {
            return when (this) {
                TRUE -> FALSE
                FALSE -> TRUE
                UNDEFINED -> UNDEFINED
            }
        }
    }

    private fun getStatus(lit: Int): VarStatus {
        if (vars[litIndex(lit)].status == VarStatus.UNDEFINED) return VarStatus.UNDEFINED
        if (lit < 0) return !vars[-lit].status
        return vars[lit].status
    }

    private fun setStatus(lit: Int, status: VarStatus) {
        if (lit < 0) {
            vars[-lit].status = !status
        } else {
            vars[lit].status = status
        }
    }

    data class VarState(
        var status: VarStatus,
        var clause: Int,
        var level: Int,
    )

    // convert values to a possible satisfying result: if a variable less than 0 it's FALSE, otherwise it's TRUE
    private fun variableValues() = vars
        .mapIndexed { index, v ->
            when (v.status) {
                VarStatus.TRUE -> index
                VarStatus.FALSE -> -index
                else -> index
            }
        }.sortedBy { litIndex(it) }.filter { litIndex(it) > 0 }

    // values of variables
    private val vars: MutableList<VarState> = MutableList(varsNumber + 1) { VarState(VarStatus.UNDEFINED, -1, -1) }

    // all decisions and consequences
    private val trail: MutableList<Int> = mutableListOf()

    // decision level
    private var level: Int = 0

    // two watched literals heuristic
    private val watchers = MutableList(varsNumber + 1) { mutableSetOf<Int>() } // set of clauses watched by literal
    private fun litIndex(lit: Int): Int = abs(lit)

    // list of unit clauses to propagate
    private val units: MutableList<Int> = mutableListOf()

    private var assumptions: List<Int> = emptyList()

    private fun clearTrail() {
        while (trail.isNotEmpty()) {
            delVariable(trail.removeLast())
        }
    }

    fun solveWithAssumptions(currentAssumptions: List<Int> = emptyList()): List<Int>? {
        assumptions = currentAssumptions
        val result = solve()
        assumptions.forEach {
            if (getStatus(it) == VarStatus.FALSE) {
                clearTrail()
                return null
            }
        }
        clearTrail()
        return  result
    }

    fun solve(): List<Int>? {

        countOccurrence()
        updateSig()

        // simplifying given cnf formula
        preprocessing()

        // extremal cases
        if (clauses.isEmpty()) return emptyList()
        if (clauses.any { it.size == 0 }) return null

        buildWatchers()

        // main loop
        while (true) {
            val conflictClause = propagate()
            if (conflictClause != -1) {
                if (level == 0) return null // in case there is a conflict in CNF
                val lemma = analyzeConflict(clauses[conflictClause]) // build new clause by conflict clause

                addClause(lemma)
                backjump(lemma)

                numberOfConflictsAfterRestart++
                // restarting after some number of conflicts
                if (numberOfConflictsAfterRestart >= restartNumber) {
                    numberOfConflictsAfterRestart = 0
                    makeRestart()
                }
                // VSIDS
                numberOfConflicts++
                if (numberOfConflicts == decay) { // update scores
                    numberOfConflicts = 0
                    score.forEachIndexed { ind, _ -> score[ind] /= divisionCoeff }
                    lemma.forEach { lit -> score[litIndex(lit)]++ }
                }

                continue
            }

            // If (the problem is already) SAT, return the current assignment
            if (satisfiable()) {
                return variableValues()
            }

            // try to guess variable
            level++
            val nextVariable = getNextVariable(level)

            // Check that assumption we want to make isn't controversial
            if (wrongAssumption(nextVariable)) {
                return null
            }
            addVariable(-1, nextVariable)
        }
    }

    private fun getNextVariable(level: Int): Int {
        return if (level > assumptions.size) {
            vsids()
        } else {
            return assumptions[level - 1]
        }
    }

    private fun wrongAssumption(lit: Int) = getStatus(lit) == VarStatus.FALSE

    // add clause and add watchers to it
    fun addClause(clause: MutableList<Int>) {
        clauses.add(clause)
        addWatchers(clause, clauses.lastIndex)
        // add clause to litOccurrence
        clause.forEach { lit -> litOccurrence[litIndex(lit)].add(clauses.lastIndex) }
        clauseSig.add(countSig(clauses.lastIndex))
    }

    fun newVar(): Int {
        varsNumber++
        vars.add(VarState(VarStatus.UNDEFINED, -1, -1))
        return varsNumber
    }

    // run only once in the beginning
    private fun buildWatchers() {
        clauses.forEachIndexed { index, clause ->
            addWatchers(clause, index)
        }
    }

    // add watchers to clause. Run in buildWatchers and addClause
    private fun addWatchers(clause: MutableList<Int>, index: Int) {
        if (clause.size == 1) {
            watchers[litIndex(clause[0])].add(index)
            units.add(index)
            return
        }
        val undef = clause.count { getStatus(it) == VarStatus.UNDEFINED }

        // initial building
        if (undef >= 2) {
            val a = clause.indexOfFirst { getStatus(it) == VarStatus.UNDEFINED }
            val b = clause.drop(a + 1).indexOfFirst { getStatus(it) == VarStatus.UNDEFINED } + a + 1
            watchers[litIndex(clause[a])].add(index)
            watchers[litIndex(clause[b])].add(index)
        } else if (undef == 1) {
            val a = clause.indexOfFirst { getStatus(it) == VarStatus.UNDEFINED }
            watchers[litIndex(clause[a])].add(index)
            addForLastInTrail(1, clause, index)
        } else { // for clauses added by conflict
            addForLastInTrail(2, clause, index)
        }
    }

    private fun addForLastInTrail(n: Int, clause: List<Int>, index: Int) {
        var cnt = 0
        val clauseVars = clause.map { litIndex(it) }
        for (ind in trail.lastIndex downTo 0) { // want to watch on last 2 literals from trail for conflict clause
            if (trail[ind] in clauseVars) {
                cnt++
                watchers[trail[ind]].add(index)
                if (cnt == n) {
                    return
                }
            }
        }
    }

    // check is all clauses satisfied or not
    private fun satisfiable() = clauses.all { clause -> clause.any { lit -> getStatus(lit) == VarStatus.TRUE } }

    // add a variable to the trail and update watchers of clauses linked to this variable
    private fun addVariable(clause: Int, lit: Int): Boolean {
        if (getStatus(lit) != VarStatus.UNDEFINED) return false

        setStatus(lit, VarStatus.TRUE)
        val v = litIndex(lit)
        vars[v].clause = clause
        vars[v].level = level
        trail.add(v)
        updateWatchers(lit)
        return true
    }

    // update watchers for clauses linked with lit
    private fun updateWatchers(lit: Int) {
        val clausesToRemove = mutableSetOf<Int>()
        watchers[litIndex(lit)].forEach { brokenClause ->
            val undef = clauses[brokenClause].count { getStatus(it) == VarStatus.UNDEFINED }
            val firstTrue = clauses[brokenClause].firstOrNull { getStatus(it) == VarStatus.TRUE }
            if (undef > 1) {
                val newWatcher = clauses[brokenClause].first {
                    getStatus(it) == VarStatus.UNDEFINED && brokenClause !in watchers[litIndex(it)]
                }
                watchers[litIndex(newWatcher)].add(brokenClause)
                clausesToRemove.add(brokenClause)
            } else if (undef == 1 && firstTrue == null) {
                units.add(brokenClause)
            }
        }
        watchers[litIndex(lit)].removeAll(clausesToRemove)
    }

    // del a variable from the trail
    private fun delVariable(v: Int) {
        setStatus(v, VarStatus.UNDEFINED)
        vars[v].clause = -1
        vars[v].level = -1
    }

    // return index of conflict clause, or -1 if there is no conflict clause
    private fun propagate(): Int {

        while (units.size > 0) {
            val clause = units.removeLast()

            if (clauses[clause].any { getStatus(it) == VarStatus.TRUE }) continue

            require(clauses[clause].any { getStatus(it) != VarStatus.FALSE }) // guarantees that clauses in unit don't become defined incorrect

            val lit = clauses[clause].first { getStatus(it) == VarStatus.UNDEFINED }
            // check if we get a conflict
            watchers[litIndex(lit)].forEach { brokenClause ->
                if (brokenClause != clause) {
                    val undef = clauses[brokenClause].count { getStatus(it) == VarStatus.UNDEFINED }
                    if (undef == 1 && -lit in clauses[brokenClause] && clauses[brokenClause].all { getStatus(it) != VarStatus.TRUE }) {
                        setStatus(lit, VarStatus.TRUE)
                        val v = litIndex(lit)
                        vars[v].clause = clause
                        vars[v].level = level
                        trail.add(v)
                        return brokenClause
                    }
                }
            }
            addVariable(clause, lit)
        }

        return -1
    }

    // change level, undefine variables, clear units
    private fun backjump(clause: MutableList<Int>) {
        level = clause.map { vars[litIndex(it)].level }.sortedDescending().firstOrNull { it != level } ?: 0

        while (trail.isNotEmpty() && vars[trail.last()].level > level) {
            delVariable(trail.removeLast())
        }
        units.clear()
        units.add(clauses.lastIndex) // after backjump it's the only clause to propagate
    }

    // add a literal to lemma if it hasn't been added yet
    private fun updateLemma(lemma: MutableList<Int>, lit: Int) {
        if (lit !in lemma) {
            lemma.add(lit)
        }
    }

    // analyze conflict and return new clause
    private fun analyzeConflict(conflict: MutableList<Int>): MutableList<Int> {

        val active = MutableList(varsNumber + 1) { false }
        val lemma = mutableListOf<Int>()

        conflict.forEach { lit ->
            if (vars[litIndex(lit)].level == level) {
                active[litIndex(lit)] = true
            } else {
                updateLemma(lemma, lit)
            }
        }
        var ind = trail.size - 1
        while (active.count { it } > 1) {

            val v = trail[ind--]
            if (!active[v]) continue

            clauses[vars[v].clause].forEach { u ->
                val current = litIndex(u)
                if (vars[current].level != level) {
                    updateLemma(lemma, u)
                } else if (current != v) {
                    active[current] = true
                }
            }
            active[v] = false
        }
        active.indexOfFirst { it }.let { v ->
            if (v != -1) {
                updateLemma(lemma, if (getStatus(v) == VarStatus.TRUE) -v else v)
            }
        }
        return lemma
    }

    // preprocessing
    private fun preprocessing() {
        removeTautologies()
        removeSubsumedClauses()
        bve()
        removeTautologies()
        //removePureLiterals()
    }

    private var restartNumber = 500.0
    private val restartCoeff = 1.1
    private val hash = LongArray(2 * varsNumber + 1) { 1L.shl(it % 64)}
    private var numberOfConflictsAfterRestart = 0
    private var numberOfRestarts = 0

    // for each literal provides a list of clauses containing it (for 'x' it's in pos x, for 'not x' in pos varsNumber + x)
    private var litOccurrence = mutableListOf<MutableList<Int>>()//vars.mapIndexed { ind, _ -> clauses.mapIndexed { ind, _ -> ind}.filter { clauses[it].contains(ind) || clauses[it].contains(-ind) }.toMutableList()}

    // return position of literal in occurrence array
    private fun litPos(lit : Int): Int {
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
        clauses[clause].forEach { lit -> sz = sz.or(hash[litPos(lit)])}
        return sz
    }

    private fun Int.clauseSize() = clauses[this].size

    private var clauseSig = mutableListOf<Long>()

    private fun updateSig() {
        clauseSig = clauses.mapIndexed { ind, _ -> countSig(ind) }.toMutableList()
    }

    private fun findSubsumed(clause: Int): Set<Int> {
        val lit = clauses[clause].minByOrNull { lit -> litOccurrence[litIndex(lit)].size } ?: 0
        return litOccurrence[litPos(lit)].filter { clause != it && clause.clauseSize() <= it.clauseSize() && subset(clause, it) }.toSet()
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

    //making restart to remove useless clauses
    private fun makeRestart() {
        numberOfRestarts++
        restartNumber *= restartCoeff
        level = 0
        trail.clear()
        units.clear()
        watchers.forEach {it.clear()}
        vars.forEachIndexed { ind, _ ->
            delVariable(ind)
        }

        removeSubsumedClauses()
        countOccurrence()

        updateSig()

        buildWatchers()
    }

    private val clauseLimit = 1200

    private fun addResolvents(ind: Int) {
        for (cl1 in litOccurrence[litPos(ind)]) {
            for (cl2 in litOccurrence[litPos(-ind)]) {
                //println("$ind, ----, $cl1, $cl2")
                val newClause = clauses[cl1].toMutableSet()
                newClause.addAll(clauses[cl2])
                newClause.remove(ind)
                newClause.remove(-ind)
                clauses.add(ArrayList(newClause))
                //println(newClause)
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
            clause.forEachIndexed {ind, lit ->
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

        println("$varsNumber, ${clauses.size}")
        //clauses.forEach { println(it) }
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

    // VSIDS
    private val score = MutableList(varsNumber + 1) {
        clauses.count { clause -> clause.contains(it) || clause.contains(-it) }.toDouble()
    }
    private val decay = 50
    private val divisionCoeff = 2.0
    private var numberOfConflicts = 0

    private fun vsids(): Int {
        var ind = -1
        for (i in 1..varsNumber) {
            if (vars[i].status == VarStatus.UNDEFINED && (ind == -1 || score[ind] < score[i])) {
                ind = i
            }
        }
        return ind
    }
}
