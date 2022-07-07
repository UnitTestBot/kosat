package org.kosat

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.abs

//DPLL
fun solveCnf(cnf: CnfRequest): List<Int>? {
    val clauses = ArrayList(cnf.clauses.map { ArrayList(it.lit.toList()) })
    return dpll(clauses)?.sortedBy { abs(it) }
}


fun unitPropagate(clauses: ArrayList<ArrayList<Int>>): List<Int>? {
    val res = mutableListOf<Int>()
    while (true) {
        // If a clause is a unit clause, i.e. it contains only a single unassigned literal, this clause can only be
        // satisfied by assigning the necessary value to make this literal true.
        val clauseToRemove = clauses.firstOrNull { it.size == 1 } ?: return res

        //unit clause's literal
        val literal = clauseToRemove[0]
        res.add(literal)

        if (!substitute(clauses, literal))
            return null
    }
}

fun chooseLiteral(clauses: ArrayList<ArrayList<Int>>): Int {
    //dumb strategy, place your heuristics here
    return clauses.first()[0]
}

//returns false only if after substitution some clause becomes empty
fun substitute(clauses: ArrayList<ArrayList<Int>>, literal: Int): Boolean {
    //removing every clause containing literal
    clauses.removeAll { it.contains(literal) }

    //discarding the complement of literal from every clause containing that complement
    clauses.forEach {
        it.remove(-literal)
        if (it.isEmpty())
            return false
    }

    return true
}

fun dpll(clauses: ArrayList<ArrayList<Int>>): PersistentList<Int>? {
    if (clauses.isEmpty())
        return persistentListOf()

    if (clauses.any { it.isEmpty() })
        return null


    val unitLits = unitPropagate(clauses) ?: return null
    if (clauses.isEmpty())
        return persistentListOf<Int>().addAll(unitLits)

    //todo pure literal elimination rule

    val chosenLit = chooseLiteral(clauses)

    //make clone
    val clone = ArrayList<ArrayList<Int>>(clauses.size)
    clauses.forEach { clone.add(ArrayList(it)) }

    //use clone for literal substitution
    if (substitute(clone, chosenLit)) {
        val recursiveLits = dpll(clone)
        if (recursiveLits != null)
            return recursiveLits.add(chosenLit).addAll(unitLits)
    }

    //use clauses for complement substitution
    if (substitute(clauses, -chosenLit)) {
        val recursiveLits = dpll(clauses)
        if (recursiveLits != null)
            return recursiveLits.add(-chosenLit).addAll(unitLits)
    }

    return null
}

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
            var satisfied = false
            for (literal in clauses[clause]) {
                val value = litValues[abs(literal)]
                if (value == LitStatus.UNDEFINED) undefined.add(literal)
                else if (literal > 0 && value == LitStatus.TRUE || literal < 0 && value == LitStatus.FALSE)
                    satisfied = true
            }
            if (!satisfied) {
                if (undefined.size == 1) {
                    val literal = undefined[0]
                    trail.add(TrailMember(literal, clause, level))
                    litValues[abs(literal)] = if (literal > 0) LitStatus.TRUE else LitStatus.FALSE
                }
                if (undefined.size == 0) {
                    return true
                }
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