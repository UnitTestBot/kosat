package org.kosat.heuristics

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

        } // todo litIndex

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
        while (activity.size < numberOfVariables + 1) {
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
            v = activityPQ.getMax().second
            activityPQ.deleteMax()
            if (vars[v].value == VarValue.UNDEFINED) {
                break
            }
        }
        return v
    }
}
