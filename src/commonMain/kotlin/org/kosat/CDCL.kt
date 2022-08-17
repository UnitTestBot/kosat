package org.kosat

import org.kosat.heuristics.Preprocessor
import org.kosat.heuristics.Restarter
import org.kosat.heuristics.VariableSelector
import org.kosat.heuristics.VsidsWithoutQueue
import kotlin.math.abs

// CDCL
fun solveCnf(cnf: CnfRequest): List<Int>? {
    val clauses = (cnf.clauses.map { it.lits }).toMutableList()
    return CDCL(clauses.map { Clause(it) }.toMutableList(), cnf.vars).solve()
}

enum class SolverType {
    INCREMENTAL, NON_INCREMENTAL;
}

fun variable(lit: Lit): Int = lit / 2

class CDCL(private val solverType: SolverType = SolverType.INCREMENTAL) {

    // we never store clauses of size 1
    // they are lying at 0 decision level of trail

    // initial constraints + externally added by newClause
    val constraints = mutableListOf<Clause>()

    // learnt from conflicts clauses, once in a while their number halved
    val learnts = mutableListOf<Clause>()
    var numberOfVariables: Int = 0

    // contains current assignment, clause it came from and decision level when it happened
    val vars: MutableList<VarState> = MutableList(numberOfVariables) { VarState(VarValue.UNDEFINED, null, -1) }

    // all decisions and consequences, contains literals
    val trail: MutableList<Int> = mutableListOf()

    // two watched literals heuristic; in watchers[i] set of clauses watched by variable i
    private val watchers = MutableList(numberOfVariables * 2) { mutableListOf<Clause>() }

    var reduceNumber = 6000.0
    var reduceIncrement = 500.0

    // current decision level
    var level: Int = 0

    var qhead = 0

    // minimization lemma in analyze conflicts
    private val minimizeMarks = MutableList(numberOfVariables * 2) { 0 }
    var mark = 0

    /** Heuristics **/

    // branching heuristic
    // private val variableSelector: VariableSelector = VSIDS(numberOfVariables)
    private val variableSelector: VariableSelector = VsidsWithoutQueue(numberOfVariables, this)
    // private val variableSelector: VariableSelector = FixedOrder(this)

    // preprocessing includes deleting subsumed clauses and bve, offed by default
    private var preprocessor: Preprocessor? = null

    // restart search from time to time
    private val restarter = Restarter(this)

    private fun uncheckedEnqueue(lit: Lit, reason: Clause? = null) {
        setValue(lit, VarValue.TRUE)
        val v = variable(lit)
        vars[v].reason = reason
        vars[v].level = level
        trail.add(lit)
    }

    /** Variable states **/
    // odd literals for negative value, even for positive

    // get value of literal
    fun getValue(lit: Lit): VarValue {
        if (vars[variable(lit)].value == VarValue.UNDEFINED) return VarValue.UNDEFINED
        return if (lit % 2 == 1)
                !vars[variable(lit)].value
            else
                vars[variable(lit)].value
    }

    // set value for literal
    private fun setValue(lit: Lit, value: VarValue) {
        if (lit % 2 == 1) {
            vars[variable(lit)].value = !value
        } else {
            vars[variable(lit)].value = value
        }
    }

/*    // TODO: rename
    private fun watchedPos(lit: Int): Int {
        return if (lit < 0) {
            2 * (-lit)
        } else {
            2 * lit - 1
        }
    }*/

    /** Interface **/

    constructor() : this(mutableListOf<Clause>())

    // convert DIMACS format: variables are numbered starting from zero
    // literals - lit = var * 2, -lit = var * 2 + 1
    fun negative(v: Int) = v * 2 + 1
    fun positive(v: Int) = v * 2

    private fun Clause.renumber() = Clause(
        this.lits.map { lit ->
            if (lit < 0) {
                (-lit - 1) * 2 + 1
            } else {
                (lit - 1) * 2
            }}.toMutableList())

    private fun MutableList<Clause>.renumber() = this.map { it.renumber() }

    constructor(
        initialClauses: MutableList<Clause>,
        initialVarsNumber: Int = 0,
        solverType: SolverType = SolverType.INCREMENTAL
    ) : this(solverType) {
        reserveVars(initialVarsNumber)
        initialClauses.renumber().forEach { newClause(it) }
        polarity = MutableList(numberOfVariables + 1) { VarValue.UNDEFINED } // TODO is phaseSaving adapted for incremental?
    }

    private fun reserveVars(max: Int) {
        while (numberOfVariables < max) {
            addVariable()
        }
    }

    // public function for adding new variables
    fun addVariable(): Int { // TODO simple checks of duplicate variables in newClause
        numberOfVariables++

        variableSelector.addVariable()

        vars.add(VarState(VarValue.UNDEFINED, null, -1))

        watchers.add(mutableListOf())
        watchers.add(mutableListOf())
        minimizeMarks.add(0)
        minimizeMarks.add(0)

        return numberOfVariables
    }

    // public function for adding new clauses
    fun newClause(clause: Clause) {
        require(level == 0)

        // add not mentioned variables from new clause
        val maxVar = clause.maxOfOrNull { variable(it) } ?: 0
        while (numberOfVariables < maxVar) {
            addVariable()
        }

        // don't add clause if it already had true literal
        if (clause.any { getValue(it) == VarValue.TRUE }) {
            return
        }

        // delete every false literal from new clause
        clause.lits.removeAll { getValue(it) == VarValue.FALSE }

        // if clause contains x and -x than it is useless
        if (clause.any { (it xor 1) in clause }) {
            return
        }

        // handling case of clause of size 1
        if (clause.size == 1) {
            uncheckedEnqueue(clause[0])
        } else {
            addConstraint(clause)
        }
    }

    /** Trail: **/

    // delete last variable from the trail
    private fun trailRemoveLast() {
        val lit = trail.removeLast()
        val v = variable(lit)
        polarity[v] = getValue(lit)
        setValue(lit, VarValue.UNDEFINED)
        vars[v].reason = null
        vars[v].level = -1
        variableSelector.backTrack(v)
    }

    // clear trail until given level
    fun clearTrail(until: Int = -1) {
        while (trail.isNotEmpty() && vars[variable(trail.last())].level > until) {
            trailRemoveLast()
        }
    }

    /** Solve with assumptions **/

    // assumptions for incremental sat-solver
    private var assumptions: List<Int> = emptyList()

    // phase saving
    private var polarity: MutableList<VarValue> = mutableListOf()

    fun solve(currentAssumptions: List<Int>): List<Int>? {
        require(solverType == SolverType.INCREMENTAL)

        assumptions = Clause(currentAssumptions.toMutableList()).renumber()
        variableSelector.initAssumptions(assumptions)
        val result = solve()
        if (result == null) {
            assumptions = emptyList()
            return null
        }
        assumptions.forEach { lit ->
            if (result.find { it == lit xor 1 } != null) {
                assumptions = emptyList()
                return null
            }
        }
        assumptions = emptyList()
        return result
    }

    // half of learnt get reduced
    fun reduceDB() {
        learnts.sortByDescending { it.lbd }
        val lim = learnts.size / 2
        var i = 0
        learnts.forEach { clause ->
            if (i < lim) {
                i++
                clause.deleted = true
            }
        }
        learnts.removeAll { it.deleted }
    }

    /** Solve **/

    fun solve(): List<Int>? {

        var totalNumberOfConflicts = 0

        preprocessor = if (solverType == SolverType.NON_INCREMENTAL) {
            Preprocessor(this)
        } else {
            null
        }
        preprocessor?.apply()

        // extreme cases
        if (constraints.isEmpty()) return getModel()
        if (constraints.any { it.isEmpty() }) return null
        if (constraints.any { it.all { lit -> getValue(lit) == VarValue.FALSE } }) return null

        variableSelector.build(constraints)

        // main loop
        while (true) {
            val conflictClause = propagate()
            if (conflictClause != null) {
                // CONFLICT
                totalNumberOfConflicts++

                // in case there is a conflict in CNF and trail is already in 0 state
                if (level == 0) {
                    println("KoSat conflicts:   $totalNumberOfConflicts")
                    return null
                }

                // build new clause by conflict clause
                val lemma = analyzeConflict(conflictClause)

                lemma.lbd = lemma.distinctBy { vars[variable(it)].level }.size

                backjump(lemma)

                // after backjump there is only one clause to propagate
                qhead = trail.size

                // if lemma.size == 1 we already added it to units at 0 level
                if (lemma.size == 1) {
                    uncheckedEnqueue(lemma[0])
                } else {
                    uncheckedEnqueue(lemma[0], lemma)
                    addLearnt(lemma)
                }

                // remove half of learnts
                if (learnts.size > reduceNumber) {
                    reduceNumber += reduceIncrement
                    restarter.restart()
                    reduceDB()
                }
                variableSelector.update(lemma)

                // restart search after some number of conflicts
                restarter.update()
            } else {
                // NO CONFLICT
                require(qhead == trail.size)

                // If (the problem is already) SAT, return the current assignment
                if (trail.size == numberOfVariables) {
                    val model = getModel()
                    reset()
                    println("KoSat conflicts:   $totalNumberOfConflicts")
                    return model
                }


                // try to guess variable
                level++
                var nextDecisionVariable = variableSelector.nextDecision(vars, level)

                if (nextDecisionVariable == -1) {
                    reset()
                    return null
                }

                // phase saving heuristic
                if (level > assumptions.size && polarity[variable(nextDecisionVariable)] == VarValue.FALSE) {
                    nextDecisionVariable = (variable(nextDecisionVariable) * 2) xor 1
                } // TODO move to nextDecisionVariable

                uncheckedEnqueue(nextDecisionVariable)
            }
        }
    }

    private fun reset() {
        level = 0
        clearTrail(0)
        qhead = trail.size
    }

    // return current assignment of variables
    private fun getModel(): List<Int> {
        if (solverType == SolverType.NON_INCREMENTAL) {
            preprocessor?.recoverAnswer()
        }

        return vars.mapIndexed { index, v ->
                when (v.value) {
                    VarValue.TRUE -> index + 1
                    VarValue.FALSE -> -index - 1
                    VarValue.UNDEFINED -> {
                        println(vars)
                        println(trail)
                        throw Exception("Unexpected unassigned variable")
                    }
                }
            }
    }

    /** Two watchers **/

    // add watchers to new clause. Run and addConstraint and addLearnt
    private fun addWatchers(clause: Clause) {
        require(clause.size > 1)
        watchers[clause[0]].add(clause)
        watchers[clause[1]].add(clause)
    }

    /** CDCL functions **/

    // add new constraint, executes only in newClause
    private fun addConstraint(clause: Clause) {
        require(clause.size != 1)
        constraints.add(clause)
        if (clause.isNotEmpty()) {
            addWatchers(clause)
        }

        preprocessor?.addClause(clause)
    }

    // add clause and add watchers to it
    private fun addLearnt(clause: Clause) {
        require(clause.size != 1)
        learnts.add(clause)
        if (clause.isNotEmpty()) {
            addWatchers(clause)
        }
        preprocessor?.addClause(clause)
    }

    // return conflict clause, or null if there is no conflict clause
    private fun propagate(): Clause? {
        var conflict: Clause? = null
        while (qhead < trail.size) {
            val lit = trail[qhead++]
            if (getValue(lit) == VarValue.FALSE) {
                return vars[variable(lit)].reason
            }

            val clausesToRemove = mutableSetOf<Clause>()
            for (brokenClause in watchers[lit xor 1]) {
                if (!brokenClause.deleted) {
                    if (variable(brokenClause[0]) == variable(lit)) {
                        brokenClause.swap(0, 1)
                    }
                    // if second watcher is true skip clause
                    if (getValue(brokenClause[0]) != VarValue.TRUE) {
                        var firstNotFalse = -1
                        for (ind in 2 until brokenClause.size) {
                            if (getValue(brokenClause[ind]) != VarValue.FALSE) {
                                firstNotFalse = ind
                                break
                            }
                        }
                        if (firstNotFalse == -1 && getValue(brokenClause[0]) == VarValue.FALSE) {
                            conflict = brokenClause
                            break
                        } else if (firstNotFalse == -1) {
                            uncheckedEnqueue(brokenClause[0], brokenClause)
                        } else {
                            watchers[brokenClause[firstNotFalse]].add(brokenClause)
                            brokenClause.swap(firstNotFalse, 1)
                            clausesToRemove.add(brokenClause)
                        }
                    }
                }
            }
            watchers[lit xor 1].removeAll(clausesToRemove)
            if (conflict != null) break
        }
        return conflict
    }

    // change level, undefine variables, clear units (if clause.size == 1 we backjump to 0 level)
    // Pre-conditions: second element in clause should have first max level except current one
    private fun backjump(clause: Clause) {
        level = if (clause.size > 1) vars[variable(clause[1])].level else 0
        clearTrail(level)
    }

    // deleting lits that have ancestor in implication graph in reason
    private fun minimize(clause: Clause): Clause {
        mark++
        clause.forEach { minimizeMarks[it] = mark }
        return Clause(clause.filterNot { lit ->
            vars[variable(lit)].reason?.all {
                minimizeMarks[it] == mark
            } ?: false
        }.toMutableList())
    }

    // analyze conflict and return new clause
    /** Post-condition:
     *      - first element in clause has max (current) propagate level
     *      - second element in clause has second max propagate level
     */
    private fun analyzeConflict(conflict: Clause): Clause {

        val seen = MutableList(numberOfVariables) { false }

        fun updateLemma(lemma: MutableSet<Int>, lit: Int) {
            lemma.add(lit)
        }

        var numberOfActiveVariables = 0
        val lemma = mutableSetOf<Int>()

        conflict.forEach { lit ->
            if (vars[variable(lit)].level == level) {
                seen[variable(lit)] = true
                numberOfActiveVariables++
            } else {
                updateLemma(lemma, lit)
            }
        }
        var ind = trail.lastIndex


        while (numberOfActiveVariables > 1) {

            val v = variable(trail[ind--])
            if (!seen[v]) continue

            vars[v].reason?.forEach { u ->
                val current = variable(u)
                if (vars[current].level != level) {
                    updateLemma(lemma, u)
                } else if (current != v && !seen[current]) {
                    seen[current] = true
                    numberOfActiveVariables++
                }
            }
            seen[v] = false
            numberOfActiveVariables--
        }

        var newClause: Clause

        trail.last { seen[variable(it)] }.let { lit ->
            val v = variable(lit)
            updateLemma(lemma, if (getValue(v * 2) == VarValue.TRUE) (v * 2) + 1 else v * 2)
            newClause = minimize(Clause(lemma.toMutableList()))
            val uipIndex = newClause.indexOfFirst { variable(it) == v }
            // fancy swap (move UIP vertex to 0 position)
            newClause.swap(uipIndex, 0)
            seen[v] = false
        }
        // move last defined literal to 1 position
        if (newClause.size > 1) {
            val secondMax = newClause.drop(1).indices.maxByOrNull { vars[variable(newClause[it + 1])].level } ?: 0
            newClause.swap(1, secondMax + 1)
        }
        return newClause
    }
}
