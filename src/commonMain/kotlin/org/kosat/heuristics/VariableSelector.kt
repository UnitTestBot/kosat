package org.kosat.heuristics

import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.Lit
import org.kosat.VarState
import org.kosat.VarValue
import org.kosat.variable

abstract class VariableSelector {
    protected var assumptions: List<Lit> = emptyList()

    fun initAssumptions(assumptions: List<Lit>) {
        this.assumptions = assumptions
    }

    abstract fun build(clauses: List<Clause>)
    abstract fun nextDecision(vars: List<VarState>, level: Int): Int
    abstract fun addVariable()
    abstract fun update(lemma: Clause)
    abstract fun backTrack(variable: Int)
}

class VSIDS(private var numberOfVariables: Int = 0) : VariableSelector() {
    private val decay = 50
    private val multiplier = 2.0
    private var numberOfConflicts = 0
    private var activityInc = 1.0
    private var incLimit = 1e100

    // list of activity for variables
    private val activity = mutableListOf<Double>()

    // priority queue of activity of undefined variables
    private var activityPQ = PriorityQueue()

    override fun update(lemma: Clause) {
        lemma.forEach { lit ->
            val v = variable(lit)
            if (activityPQ.order[v] != -1) {
                activityPQ.increaseActivity(v, activityInc)
            }
            activity[v] += activityInc

        }

        numberOfConflicts++
        if (numberOfConflicts == decay) {
            activityInc *= multiplier
            // update activity
            numberOfConflicts = 0
            if (activityInc > incLimit) {
                activity.forEachIndexed { ind, value ->
                    activity[ind] = value / activityInc
                }
                activityPQ.divideAllElements(activityInc)
                activityInc = 1.0
            }
        }
    }

    override fun addVariable() {
        activity.add(0.0)
        numberOfVariables++
    }

    override fun build(clauses: List<Clause>) {
        while (activity.size < numberOfVariables) {
            activity.add(0.0)
        }
        clauses.forEach { clause ->
            clause.forEach { lit ->
                activity[variable(lit)] += activityInc
            }
        }
        activityPQ.buildHeap(activity)
    }

    override fun nextDecision(vars: List<VarState>, level: Int): Int {
        return if (level > assumptions.size) {
            getMaxActivityVariable(vars)
        } else {
            assumptions[level - 1]
        }
    }

    override fun backTrack(variable: Int) {
        if (activityPQ.order[variable] == -1) {
            activityPQ.addValue(Pair(activity[variable], variable))
        }
    }

    // Looks for index of undefined variable with max activity
    private fun getMaxActivityVariable(vars: List<VarState>): Int {
        var v: Int
        while (true) {
            require(activityPQ.size > 0)
            v = activityPQ.getMax().second
            activityPQ.deleteMax()
            if (vars[v].value == VarValue.UNDEFINED) {
                break
            }
        }
        return v
    }
}

class FixedOrder(val solver: CDCL): VariableSelector() {
    override fun build(clauses: List<Clause>) {

    }

    override fun nextDecision(vars: List<VarState>, level: Int): Int {
        for (i in 1..vars.lastIndex) {
            if (vars[i].value == VarValue.UNDEFINED) return i
        }
        return -1
    }

    override fun addVariable() {

    }

    override fun update(lemma: Clause) {

    }

    override fun backTrack(variable: Int) {

    }

}

class VsidsWithoutQueue(private var numberOfVariables: Int = 0, private val solver: CDCL) : VariableSelector() {
    private val decay = 50
    private val multiplier = 2.0
    private var numberOfConflicts = 0
    private var activityInc = 1.0
    private var incLimit = 1e100

    // list of activity for variables
    private val activity = mutableListOf<Double>()

    override fun update(lemma: Clause) {
        lemma.forEach { lit ->
            val v = variable(lit)
            activity[v] += activityInc
        }

        numberOfConflicts++
        if (numberOfConflicts == decay) {
            activityInc *= multiplier
            // update activity
            numberOfConflicts = 0
            if (activityInc > incLimit) {
                activity.forEachIndexed { ind, value ->
                    activity[ind] = value / activityInc
                }
                activityInc = 1.0
            }
        }
    }

    override fun addVariable() {
        activity.add(0.0)
        numberOfVariables++
    }

    override fun build(clauses: List<Clause>) {
        while (activity.size < numberOfVariables) {
            activity.add(0.0)
        }
        clauses.forEach { clause ->
            clause.forEach { lit ->
                activity[variable(lit)] += activityInc
            }
        }
    }

    override fun nextDecision(vars: List<VarState>, level: Int): Lit {
        if (assumptions.any { solver.getValue(it) == VarValue.FALSE }) {
            return -1
        }
        // if there is undefined assumption pick it, other way pick best choice
        return assumptions.firstOrNull { solver.getValue(it) == VarValue.UNDEFINED } ?: getMaxActivityVariable(vars)
    }

    override fun backTrack(variable: Int) {

    }

    // Looks for index of undefined variable with max activity
    private fun getMaxActivityVariable(vars: List<VarState>): Lit {
        var v: Int = -1
        var max = -1.0
        (0 until numberOfVariables).forEach { i ->
            if (vars[i].value == VarValue.UNDEFINED && max < activity[i]) {
                v = i * 2
                max = activity[i]
            }
        }
        return v
    }
}
