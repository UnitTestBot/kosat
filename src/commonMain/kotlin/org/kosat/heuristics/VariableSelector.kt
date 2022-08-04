package org.kosat.heuristics

import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.Lit
import kotlin.math.abs

abstract class VariableSelector {
    protected var assumptions: List<Lit> = emptyList()

    fun initAssumptions(assumptions: List<Lit>) {
        this.assumptions = assumptions
    }

    abstract fun build(clauses: List<Clause>)
    abstract fun nextDecisionVariable(vars: List<CDCL.VarState>, level: Int): Int
    abstract fun addVariable()
    abstract fun update(lemma: Clause)
    abstract fun backTrack(variable: Int)
}

class VSIDS(private var varsNumber: Int = 0, private val vars: MutableList<CDCL.VarState>) : VariableSelector() {
    private val decay = 50
    private val multiplier = 2.0
    private var numberOfConflicts = 0
    private var scoreInc = 1.0
    private var incLimit = 1e100

    // list of scores for variables
    private val scores = mutableListOf<Double>()
    // priority queue of scores of undefined variables
    private var scoresPQ = PriorityQueue()

    override fun update(lemma: Clause) {
        lemma.forEach { lit ->
            val v = abs(lit)
            if (scoresPQ.order[v] < scoresPQ.sz) {
                scoresPQ.increaseScore(v, scoreInc)
            }
            scores[v] += scoreInc

        } //todo litIndex

        numberOfConflicts++
        if (numberOfConflicts == decay) {
            scoreInc *= multiplier
            // update scores
            numberOfConflicts = 0
            if (scoreInc > incLimit) {
                scores.forEachIndexed { ind, value ->
                    scores[ind] = value / scoreInc
                }
                scoresPQ.divideAllElements(scoreInc)
                scoreInc = 1.0
            }
        }
    }

    override fun addVariable() {
        scores.add(0.0)
        varsNumber++
    }

    override fun build(clauses: List<Clause>) {
        while (scores.size < varsNumber + 1) {
            scores.add(0.0)
        }
        clauses.forEach { clause ->
            clause.forEach { lit ->
                scores[abs(lit)] += scoreInc
            }
        }
        scoresPQ.buildHeap(scores)
    }

    override fun nextDecisionVariable(vars: List<CDCL.VarState>, level: Int): Int {
        return if (level > assumptions.size) {
            vsids(vars)
        } else {
            assumptions[level - 1]
        }
    }

    override fun backTrack(variable: Int) {
        if (scoresPQ.order[variable] >= scoresPQ.sz) {
            scoresPQ.addValue(Pair(scores[variable], variable))
        }
    }

    // Looks for index of undefined variable with max activity
    private fun vsids(vars: List<CDCL.VarState>): Int {
        /*val x1 = (1..varsNumber).filter {
            vars[it].status == CDCL.VarStatus.UNDEFINED
        }.let { undefined ->
            undefined.maxByOrNull { scores[it] }
        } ?: -1*/
        var x2: Int
        while (true) {
            x2 = scoresPQ.getMax().second
            scoresPQ.deleteMax()
            println("try to take ${x2}")
            if (vars[x2].status == CDCL.VarStatus.UNDEFINED) {
                println("${x2} is good!!!!\n---------------\n")
                break
            }
            println("defined")
        }
        //println(x2)
        return x2
    }
}
