package org.kosat

import kotlin.math.abs

//CDCL

class CDCL(private var clauses: ArrayList<ArrayList<Int>>, private val varsNumber: Int) {
    // clause is where this literal came from (if it 'guessed' literal than clause == -1)
    data class TrailMember(val literal: Int, val clause: Int, val decisionLevel: Int)
    enum class LitStatus { TRUE, FALSE, UNDEFINED }

    // values of variables
    private var litValues: MutableList<LitStatus> = MutableList(varsNumber + 1) { LitStatus.UNDEFINED }

    // all decisions and consequences
    private val trail: ArrayList<TrailMember> = ArrayList()

    // decision level
    private var level: Int = 0
    fun solve(): List<Int>? {
        while (true) {
            // if conflict
            if (unitPropagateCdcl()) {
                if (level == 0) return null
                val newClause: ArrayList<Int> = analyzeConflict()
                addClause(newClause)
                backjump(newClause)
            } else {
                // return model if all variables have value
                if (trail.size == varsNumber) return trail.map { it.literal }

                // make new decision
                level++
                // todo: choose undefined literal
                val chosenLit = chooseLiteral(clauses)
                trail.add(TrailMember(chosenLit, -1, level))
            }
        }
    }

    // change level, undefine variables and so on
    private fun backjump(newClause: ArrayList<Int>) {
        // todo
    }

    // add clause and change structures for it
    private fun addClause(newClause: ArrayList<Int>) {
        // todo
        clauses.add(newClause)
    }

    // return true if you get conflict
    // todo: norm realization
    private fun unitPropagateCdcl(): Boolean {
        for (clause in clauses.indices) {
            val undefined = ArrayList<Int>()
            for (literal in clauses[clause]) {
                if (litValues[abs(literal)] == LitStatus.UNDEFINED) undefined.add(literal)
            }
            if (undefined.size == 1) {
                val literal = undefined[0]
                trail.add(TrailMember(literal, clause, level))
                litValues[abs(literal)] = if (literal > 0) LitStatus.TRUE else LitStatus.FALSE
            }
            if (undefined.size == 0) {
                return true
            }
        }
        return false
    }

    // analyze conflict and return new clause
    private fun analyzeConflict(): ArrayList<Int> {
        // todo: hardest one
        return ArrayList()
    }
}