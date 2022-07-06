package org.kosat

import com.github.lipen.satlib.card.declareCardinality
import com.github.lipen.satlib.op.exactlyOne
import com.github.lipen.satlib.solver.MiniSatSolver
import com.github.lipen.satlib.solver.addClause
import com.github.lipen.satlib.solver.solve
import java.io.File


fun main() {
    println("v KOSAT SAT SOLVER, v1.0")
    println("v Solves formulas in CNF form. Input must as formatted according simplified DIMACS specified in http://www.satcompetition.org/2004/format-solvers2004.html")
    val input = System.`in`.bufferedReader().readText()
    val res = processCnfRequests(readCnfRequests(input))

    println(res)
}