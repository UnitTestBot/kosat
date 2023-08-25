package org.kosat

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import okio.buffer
import okio.sink
import okio.source
import org.kosat.cnf.CNF
import java.io.File

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

    private val proofFile: File? by option(
        "--proof",
        help = "File to write proof to"
    ).file(
        canBeDir = false,
    )

    private val binaryProof: Boolean by option(
        "--binary-proof",
        help = "Write proof in binary format"
    ).flag(default = false)

    override fun run() {
        println("c KoSAT: pure-Kotlin modern CDCL SAT solver")
        println("c The input must be formatted according to the simplified DIMACS format")
        println("c provided at http://www.satcompetition.org/2004/format-solvers2004.html")

        val cnfSource = if (cnfFile != null) {
            println("c Reading from '$cnfFile'")
            cnfFile!!.source().buffer()
        } else {
            println("c Reading from STDIN")
            System.`in`.source().buffer()
        }

        val cnf: CNF
        try {
            cnf = CNF.from(cnfSource)
        } catch (e: Exception) {
            println("c Parsing Error: ${e.message}")
            return
        }

        cnfSource.close()

        val solver = CDCL(cnf)

        val dratProofSink = if (proofFile != null) {
            println("c Writing proof to '$proofFile'")
            proofFile!!.sink().buffer()
        } else {
            null
        }

        val dratBuilder = if (dratProofSink != null) {
            if (binaryProof) {
                BinaryDratBuilder(dratProofSink)
            } else {
                DratBuilder(dratProofSink)
            }
        } else {
            NoOpDratBuilder()
        }

        solver.dratBuilder = dratBuilder

        val result = solver.solve()

        when (result) {
            SolveResult.SAT -> {
                println("s SATISFIABLE")
                val model = solver.getModel()
                val modelString = model.withIndex().joinToString(separator = " ") {
                    if (it.value) {
                        (it.index + 1).toString()
                    } else {
                        (-it.index - 1).toString()
                    }
                }
                println("v $modelString")
            }
            SolveResult.UNSAT -> {
                println("s UNSATISFIABLE")
            }
            SolveResult.UNKNOWN -> {
                println("s UNKNOWN")
            }
        }

        dratProofSink?.close()
    }
}

fun main(args: Array<String>) {
    KoSAT().main(args)
}
