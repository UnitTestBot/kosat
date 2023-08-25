package org.kosat

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import kotlin.time.measureTimedValue

class KoSAT : CliktCommand(name = "kosat") {
    init {
        context {
            helpFormatter = {
                MordantHelpFormatter(
                    it,
                    requiredOptionMarker = "*",
                    showDefaultValues = true,
                    showRequiredTag = true
                )
            }
        }
    }

    private val cnfFile: File? by argument(
        name = "cnf",
        help = "File with CNF"
    ).file(
        mustExist = true,
        canBeDir = false,
        mustBeReadable = true
    ).optional()

    override fun run() {
        println("c KoSAT: pure-Kotlin modern CDCL SAT solver")
        println("c The input must be formatted according to the simplified DIMACS format")
        println("c provided at http://www.satcompetition.org/2004/format-solvers2004.html")

        val cnfDimacs = if (cnfFile != null) {
            println("c Reading from '$cnfFile'")
            cnfFile!!.readText()
        } else {
            println("c Reading from STDIN")
            System.`in`.bufferedReader().readText()
        }
        val (res, time) = measureTimedValue {
            processCnfRequests(readCnfRequests(cnfDimacs))
        }
        print(res)
        println("c Done in ${time.inWholeMilliseconds / 1000.0} s")
    }
}

fun main(args: Array<String>) {
    KoSAT().main(args)
}
