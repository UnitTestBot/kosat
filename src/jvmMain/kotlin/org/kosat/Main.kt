package org.kosat


fun main() {
    println("v KOSAT SAT SOLVER, v1.0")
    println("v Solves formulas in CNF form. Input must as formatted according simplified DIMACS specified in http://www.satcompetition.org/2004/format-solvers2004.html")
    val input = System.`in`.bufferedReader().readText()
    val res = processCnfRequests(readCnfRequests(input))

    println(res)
}
