package org.kosat

import java.io.File
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    println("v KOSAT SAT SOLVER, v1.0")
    println("v Solves formulas in CNF form. Input must as formatted according simplified DIMACS specified in http://www.satcompetition.org/2004/format-solvers2004.html")
    var input = ""
    input = if (args.isNotEmpty() && args[0] == "--file") {
        println("Found")
        File(args[1]).readText()
    } else {
        System.`in`.bufferedReader().readText()
    }
    var res = ""
    val time = measureTimeMillis { res = processCnfRequests(readCnfRequests(input)) }.toDouble()/1000

    println(res)
    println(time.toString())
}
