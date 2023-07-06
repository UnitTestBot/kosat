package org.kosat.heuristics

import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.LBool
import org.kosat.Lit
import org.kosat.Var
import org.kosat.VarState
import org.kosat.get
import org.kosat.set

abstract class VariableSelector {
    protected var assumptions: List<Lit> = emptyList()

    fun initAssumptions(assumptions: List<Lit>) {
        this.assumptions = assumptions
    }

    abstract fun build(clauses: List<Clause>)
    abstract fun nextDecision(vars: List<VarState>, level: Int): Lit
    abstract fun addVariable()
    abstract fun update(lemma: Clause)
    abstract fun backTrack(variable: Var)
}

class VSIDS(private var numberOfVariables: Int = 0, private val solver: CDCL) : VariableSelector() {
    private val multiplier = 1.1
    private var numberOfConflicts = 0
    private var activityInc = 1.0
    private var activityLimit = 1e100

    // list of activity for variables
    private val activity = mutableListOf<Double>()

    // priority queue of activity of undefined variables
    private var activityPQ = PriorityQueue(activity)

    override fun update(lemma: Clause) {
        lemma.forEach { lit ->
            val v = lit.variable
            activity[v] += activityInc
            if (activityPQ.index[v] != -1) {
                activityPQ.siftUp(activityPQ.index[v])
            }
            if (activity[v] > activityLimit) {
                activity.forEachIndexed { ind, value ->
                    activity[ind] = value / activityLimit
                }
                activityInc /= activityLimit
            }
        }

        activityInc *= multiplier
        numberOfConflicts++
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
                activity[lit.variable] += activityInc
            }
        }
        activityPQ.buildHeap(activity)
    }

    // returns the literal as the assumptions give information about the value of the variable
    override fun nextDecision(vars: List<VarState>, level: Int): Lit {
        if (assumptions.any { solver.getValue(it) == LBool.FALSE }) {
            return Lit.UNDEF
        }
        // if there is undefined assumption pick it, other way pick best choice
        return assumptions.firstOrNull { solver.getValue(it) == LBool.UNDEFINED }
            ?: getMaxActivityVariable(vars).posLit
    }

    override fun backTrack(variable: Var) {
        if (activityPQ.index[variable] == -1) {
            activityPQ.insert(variable.ord)
        }
    }

    // Looks for index of undefined variable with max activity
    private fun getMaxActivityVariable(vars: List<VarState>): Var {
        while (true) {
            require(activityPQ.size > 0)
            val v = activityPQ.pop()
            if (vars[v].value == LBool.UNDEFINED) {
                return Var(v)
            }
        }
    }
}

class FixedOrder(val solver: CDCL) : VariableSelector() {
    override fun build(clauses: List<Clause>) {
    }

    override fun nextDecision(vars: List<VarState>, level: Int): Lit {
        for (i in 1..vars.lastIndex) {
            if (vars[i].value == LBool.UNDEFINED) return Lit(i)
        }
        return Lit.UNDEF
    }

    override fun addVariable() {
    }

    override fun update(lemma: Clause) {
    }

    override fun backTrack(variable: Var) {
    }
}

class VsidsWithoutQueue(private var numberOfVariables: Int = 0, private val solver: CDCL) : VariableSelector() {
    private val decay = 50
    private val multiplier = 2.0
    private val activityLimit = 1e100
    private var activityInc = 1.0

    private var numberOfConflicts = 0

    // list of activity for variables
    private val activity = mutableListOf<Double>()

    override fun update(lemma: Clause) {
        lemma.forEach { lit ->
            val v = lit.variable
            activity[v] += activityInc
        }

        numberOfConflicts++
        if (numberOfConflicts == decay) {
            activityInc *= multiplier
            // update activity
            numberOfConflicts = 0
            if (activityInc > activityLimit) {
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
                activity[lit.variable] += activityInc
            }
        }
    }

    override fun nextDecision(vars: List<VarState>, level: Int): Lit {
        if (assumptions.any { solver.getValue(it) == LBool.FALSE }) {
            return Lit.UNDEF
        }
        // if there is undefined assumption pick it, other way pick best choice
        return assumptions.firstOrNull { solver.getValue(it) == LBool.UNDEFINED } ?: getMaxActivityVariable(vars).posLit
    }

    override fun backTrack(variable: Var) {
    }

    // Looks for index of undefined variable with max activity
    private fun getMaxActivityVariable(vars: List<VarState>): Var {
        var v = Var.UNDEF
        var max = -1.0
        for (i in 0 until numberOfVariables) {
            if (vars[i].value == LBool.UNDEFINED && max < activity[i]) {
                v = Var(i)
                max = activity[i]
            }
        }
        return v
    }
}
