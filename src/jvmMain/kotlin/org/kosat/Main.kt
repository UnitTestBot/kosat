package org.kosat

import java.io.File
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    println("v KOSAT SAT SOLVER, v1.0")
    println("v Solves formulas in CNF form. Input must as formatted according simplified DIMACS specified in http://www.satcompetition.org/2004/format-solvers2004.html")
    var input = ""
    if (args[0] == "--file") {
        input = File(args[1]).readText()
    } else {
        input = System.`in`.bufferedReader().readText()
    }
    var res = ""
    val time = measureTimeMillis { res = processCnfRequests(readCnfRequests(input)) }.toDouble()/1000

    println(res)
    println(time.toString())
}
