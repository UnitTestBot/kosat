package org.kosat

import java.io.File
import kotlin.system.measureTimeMillis

fun run(cnfDIMACS: String) {
    var res: String
    val time = measureTimeMillis { res = processCnfRequests(readCnfRequests(cnfDIMACS)) }.toDouble() / 1000
    println(res)
    println(time.toString())
}

fun usage() {
    println("USAGE:\n   1) ./kosat --file <filename>\n   2) Empty args to enter CNF from the keyboard")
}

fun main(args: Array<String>) {
    println("v KOSAT SAT SOLVER, v1.0")
    println("v Solves formulas in CNF form. Input must as formatted according simplified DIMACS specified in http://www.satcompetition.org/2004/format-solvers2004.html")

    when (args.size) {
        0 -> run(System.`in`.bufferedReader().readText())
        2 ->
            if (args[0] == "--file")
                run(File(args[1]).readText())
            else
                usage()
        else -> usage()
    }
}
