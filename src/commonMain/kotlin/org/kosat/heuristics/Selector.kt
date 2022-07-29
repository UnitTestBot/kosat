package org.kosat.heuristics

import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.Lit
import kotlin.math.abs

abstract class Selector {
    protected var assumptions: List<Lit> = emptyList()

    fun initAssumptions(assumptions: List<Lit>) {
        this.assumptions = assumptions
    }

    abstract fun build(clauses: List<Clause>)
    abstract fun nextDecisionVariable(vars: List<CDCL.VarState>, level: Int): Int
    abstract fun addVariable()
    abstract fun update(lemma: Clause)
}

class VSIDS(private var varsNumber: Int = 0) : Selector() {
    private val decay = 50
    private val divisionCoeff = 2.0
    private var numberOfConflicts = 0

    override fun update(lemma: Clause) {
        lemma.forEach { lit -> activity[abs(lit)]++ } //todo litIndex
        numberOfConflicts++
        if (numberOfConflicts == decay) {
            // update scores
            numberOfConflicts = 0
            activity.forEachIndexed { ind, _ -> activity[ind] /= divisionCoeff }
        }
    }

    override fun addVariable() {
        activity.add(0.0)
        varsNumber++
    }

    private val activity = mutableListOf<Double>()
    override fun build(clauses: List<Clause>) {
        activity.add(0.0)
        for (ind in 1..varsNumber) {
            activity.add(clauses.count { clause -> clause.contains(ind) || clause.contains(-ind) }.toDouble())
        }
    }

    override fun nextDecisionVariable(vars: List<CDCL.VarState>, level: Int): Int {
        return if (level > assumptions.size) {
            vsids(vars)
        } else {
            assumptions[level - 1]
        }
    }

    // Looks for index of undefined variable with max activity
    private fun vsids(vars: List<CDCL.VarState>) = (1..varsNumber).filter {
        vars[it].status == CDCL.VarStatus.UNDEFINED
    }.let { undefined ->
        undefined.maxByOrNull { activity[it] }
    } ?: -1
}
