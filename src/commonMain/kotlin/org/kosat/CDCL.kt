package org.kosat

import kotlin.math.abs

// CDCL
fun solveCnf(cnf: CnfRequest): List<Int>? {
    val clauses = (cnf.clauses.map { it.lit }).toMutableList()
    return CDCL(clauses, cnf.vars).solve()
}


class CDCL(private var clauses: MutableList<MutableList<Int>>, private var varsNumber: Int) {
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

    private fun getTrailIndex(lit: Int): Int {
        return vars[litIndex(lit)].trailIndex
    }

    data class VarState(
        var status: VarStatus,
        var clause: Int,
        var level: Int,
        var trailIndex: Int
    )

    // convert values to a possible satisfying result: if a variable less than 0 it's FALSE, otherwise it's TRUE
    private fun variableValues(): List<Int> {
        println(totalNumberOfConflicts)
        // updating vars for bve
        val oldStatus = oldNumeration.mapIndexed { ind, _ -> vars[ind].status }
        for (ind in 1..varsNumber) {
            vars[ind].status = VarStatus.UNDEFINED
        }
        for (ind in 1..varsNumber) {
            vars[oldNumeration[ind]].status = oldStatus[ind]
            if (vars[oldNumeration[ind]].status == VarStatus.UNDEFINED) {
                vars[oldNumeration[ind]].status = VarStatus.TRUE
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
                    if (getStatus(lit) != VarStatus.FALSE) {
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
                vars[ind].status = VarStatus.FALSE
            } else {
                vars[ind].status = VarStatus.TRUE
            }
        }

        return vars
            .mapIndexed { index, v ->
                when (v.status) {
                    VarStatus.TRUE -> index
                    VarStatus.FALSE -> -index
                    else -> index
                }
            }.sortedBy { litIndex(it) }.filter { litIndex(it) > 0 }
    }

    // values of variables
    private val vars: MutableList<VarState> = MutableList(varsNumber + 1) { VarState(VarStatus.UNDEFINED, -1, -1, -1) }

    // all decisions and consequences
    private val trail: MutableList<Int> = mutableListOf()

    // decision level
    private var level: Int = 0

    // two watched literals heuristic
    private val watchers = MutableList(varsNumber + 1) { mutableListOf<Int>() } // set of clauses watched by literal
    private fun litIndex(lit: Int): Int = abs(lit)

    // list of unit clauses to propagate
    private val units: MutableList<Int> = mutableListOf()

    fun solve(): List<Int>? {
        countOccurrence()
        updateSig()

        // simplifying given cnf formula
        //preprocessing()
        countScore()


        // extremal cases
        if (clauses.isEmpty()) return variableValues()
        if (clauses.any { it.size == 0 }) {
            println(totalNumberOfConflicts)
            return null
        }

        buildWatchers()

        // main loop
        while (true) {
            val conflictClause = propagate()
            if (conflictClause != -1) {
                if (level == 0) {
                    println(totalNumberOfConflicts)
                    return null
                } // in case there is a conflict in CNF
                val lemma = analyzeConflict(clauses[conflictClause]) // build new clause by conflict clause

                addClause(lemma)
                backjump(lemma)

                numberOfConflictsAfterRestart++
                totalNumberOfConflicts++
                // restarting after some number of conflicts
                if (numberOfConflictsAfterRestart >= restartNumber) {
                    println("Conflicts found: $totalNumberOfConflicts. Clauses learned: ${clauses.size}")
                    numberOfConflictsAfterRestart = 0
                    makeRestart()
                }
                // VSIDS
                numberOfConflicts++
                lemma.forEach { lit -> score[litIndex(lit)]++ }
                if (numberOfConflicts == decay) { // update scores
                    numberOfConflicts = 0
                    score.forEachIndexed { ind, _ -> score[ind] /= divisionCoeff }
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
            addVariable(-1, -vsids())
        }
    }

    // run only once in the beginning
    private fun buildWatchers() {
        while (watchers.size > varsNumber + 1) {
            watchers.removeLast()
        }
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
        //val undef = clause.count { getStatus(it) == VarStatus.UNDEFINED }

        var undef = 0
        var falseCounter = 0
        val undefIndex = mutableListOf<Int>()
        var lastInTrail = -1
        var secondLastInTrail = -1

        clause.forEachIndexed { ind, lit ->
            if (getStatus(lit) == VarStatus.UNDEFINED) {
                undef++
                if (undef <= 2) {
                    undefIndex.add(ind)
                }
            } else {
                if (lastInTrail == -1 || getTrailIndex(clause[lastInTrail]) < getTrailIndex(lit)) {
                    secondLastInTrail = lastInTrail
                    lastInTrail = ind
                } else if (secondLastInTrail == -1 || getTrailIndex(clause[secondLastInTrail]) < getTrailIndex(lit)) {
                    secondLastInTrail = ind
                }
                if (getStatus(lit) == VarStatus.FALSE) {
                    falseCounter++
                }
            }
        }

        var a: Int
        var b: Int

        if (undef >= 2) {
            a = undefIndex[0]
            b = undefIndex[1]
            watchers[litIndex(clause[a])].add(index)
            watchers[litIndex(clause[b])].add(index)
        } else if (undef == 1) {
            a = undefIndex[0]
            watchers[litIndex(clause[a])].add(index)
            if (falseCounter == clause.size - 1) {
                units.add(index)
            }
            b = lastInTrail
            watchers[litIndex(clause[b])].add(index)
            //addForLastInTrail(1, clause, index)
        } else {
            // for clauses added by conflict and by newClause if it already controversial
            a = lastInTrail
            b = secondLastInTrail
            watchers[litIndex(clause[a])].add(index)
            watchers[litIndex(clause[b])].add(index)
            //addForLastInTrail(2, clause, index)
        }
        // put watchers on first place
        if (a > b) {
            val tmp = a
            a = b
            b = tmp
        }
        var tmp = clause[b]
        when (a) {
            0 -> {
                clause[b] = clause[1]
                clause[1] = tmp
            }
            1 -> {
                clause[b] = clause[0]
                clause[0] = tmp
            }
            else -> {
                clause[b] = clause[1]
                clause[1] = tmp
                tmp = clause[a]
                clause[a] = clause[0]
                clause[0] = tmp
            }
        }

        //println("${clause[0]} = ${clauses[index][0]} and ${clause[1]} = ${clauses[index][1]}")
        //println("Clause added: $clause. Check: ${clause[0]}, ${clause[1]}")
        //for (i in watchers) println(i)
        //println("_________________")
    }



    // find n last assigned variables from given clause
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

    // simple chose of undefined variable
    private fun MutableList<VarState>.firstUndefined() = this
        .drop(1)
        .indexOfFirst { it.status == VarStatus.UNDEFINED } + 1


    // add a variable to the trail and update watchers of clauses linked to this variable
    private fun addVariable(clause: Int, lit: Int): Boolean {
        if (getStatus(lit) != VarStatus.UNDEFINED) return false
        setStatus(lit, VarStatus.TRUE)
        val v = litIndex(lit)
        vars[v].clause = clause
        vars[v].level = level
        trail.add(v)
        vars[v].trailIndex = trail.lastIndex
        updateWatchers(lit)
        return true
    }

    // update watchers for clauses linked with lit
    private fun updateWatchers(lit: Int) {
        val clausesToRemove = mutableSetOf<Int>()
        watchers[litIndex(lit)].forEach { brokenClause ->
            var undef = 0
            val undefIndex = mutableListOf<Int>()
            var hasTrue = false
            //val undef = clauses[brokenClause].count { getStatus(it) == VarStatus.UNDEFINED }
            //val firstTrue = clauses[brokenClause].firstOrNull { getStatus(it) == VarStatus.TRUE }
            clauses[brokenClause].forEachIndexed { index, it ->
                if (getStatus(it) == VarStatus.UNDEFINED) {
                    undef++
                    if (undef <= 2) {
                        undefIndex.add(index)
                    }
                } else if (getStatus(it) == VarStatus.TRUE) {
                    hasTrue = true
                }
            }
            if (undef > 1) {
                //println("updating: $lit. Current index: ${clauses[brokenClause][0]} and ${clauses[brokenClause][1]}")
                val newWatcherInd = if (undefIndex[0] > 1) undefIndex[0] else undefIndex[1]
                val newWatcher = clauses[brokenClause][newWatcherInd]
                watchers[litIndex(newWatcher)].add(brokenClause)
                if (litIndex(clauses[brokenClause][0]) == litIndex(lit)) {
                    val tmp = clauses[brokenClause][0]
                    clauses[brokenClause][0] = newWatcher
                    clauses[brokenClause][newWatcherInd] = tmp
                } else {
                    val tmp = clauses[brokenClause][1]
                    clauses[brokenClause][1] = newWatcher
                    clauses[brokenClause][newWatcherInd] = tmp
                }
                clausesToRemove.add(brokenClause)
            } else if (undef == 1 && !hasTrue){
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
        vars[v].trailIndex = -1
    }

    // return index of conflict clause, or -1 if there is no conflict clause
    private fun propagate(): Int {

        while (units.size > 0) {
            val clause = units.removeLast()

            if (clauses[clause].any { getStatus(it) == VarStatus.TRUE }) continue

            //require(clauses[clause].any { getStatus(it) != VarStatus.FALSE }) // guarantees that clauses in unit don't become defined incorrect

            val lit = clauses[clause].first { getStatus(it) == VarStatus.UNDEFINED } // it's always index 0 or 1 (so it's fast)
            // check if we get a conflict
            watchers[litIndex(lit)].forEach { brokenClause ->
                if (brokenClause != clause) {
                    // just because we need to check if undef is 1 or >=2 (so we check only watchers) - good acceleration
                    var undef = 0
                    if (getStatus(clauses[brokenClause][0]) == VarStatus.UNDEFINED) {
                        undef++
                    }
                    if (clauses[brokenClause].size > 1 && getStatus(clauses[brokenClause][1]) == VarStatus.UNDEFINED) {
                        undef++
                    }
                    // there is a slow check with .all (takes most time) - ideas to speed up (???)
                    if (undef == 1 && -lit in clauses[brokenClause] && clauses[brokenClause].all { getStatus(it) != VarStatus.TRUE }) {
                        setStatus(lit, VarStatus.TRUE)
                        val v = litIndex(lit)
                        vars[v].clause = clause
                        vars[v].level = level
                        trail.add(v)
                        vars[v].trailIndex = trail.lastIndex
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

        while (trail.size > 0 && vars[trail.last()].level > level) {
            delVariable(trail.removeLast())
        }
        units.clear()
        units.add(clauses.lastIndex) // after backjump it's the only clause to propagate
    }

    // add clause and add watchers to it
    private fun addClause(clause: MutableList<Int>) {
        clauses.add(clause)
        addWatchers(clause, clauses.lastIndex)
        // add clause to litOccurrence
        clause.forEach { lit -> litOccurrence[litIndex(lit)].add(clauses.lastIndex) }
        clauseSig.add(countSig(clauses.lastIndex))
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
        removeSubsumedClauses()
        println("$varsNumber, ${clauses.size}")
        //clauses.forEach { println(it) }
        //removePureLiterals()
    }

    private var restartNumber = 500.0
    private val restartCoeff = 1.1
    private val hash = LongArray(2 * varsNumber + 1) { 1L.shl(it % 64)}
    private var numberOfConflictsAfterRestart = 0
    private var numberOfRestarts = 0
    private var totalNumberOfConflicts = 0

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

    //making restart to remove useless clauses
    private fun makeRestart() {
        numberOfRestarts++
        restartNumber *= restartCoeff
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

    private val clauseLimit = 600

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
                clauses.add(ArrayList(newClause))
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

    private val oldNumeration = MutableList(varsNumber + 1) {index -> index}
    private var startClauses = listOf<MutableList<Int>>()
    private var startOccurrence = listOf<MutableList<Int>>()
    private val deletingOrder = mutableListOf<Int>()
    private val isClauseDeleted = MutableList(clauses.size) { false }

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
                if (isLiteralRemoved[litIndex(lit)]) {
                    deletedClauses.add(ind)
                    break
                }
            }
        }
        startClauses = clauses.map { it }
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
    private val score = mutableListOf<Double>()
    private fun countScore() {
        score.add(0.0)
        for(ind in 1..varsNumber) {
            score.add(clauses.count { clause -> clause.contains(ind) || clause.contains(-ind) }.toDouble())
        }
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