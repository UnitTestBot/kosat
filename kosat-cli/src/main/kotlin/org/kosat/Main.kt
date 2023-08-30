package org.kosat

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import kotlin.time.DurationUnit
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
        help = "File with CNF (read from stdin if the argument is absent)"
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

    private val timeLimit: Int? by option(
        "-t",
        "--time-limit",
        help = "Time limit"
    ).int()

    private val isNotPrintModel: Boolean by option(
        "-n",
        "--no-print-model",
        help = "Do not print satisfying assignment"
    ).flag()

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
        val cnf = cnfSource.use { CNF.from(it) }
        val solver = CDCL(cnf)

        solver.reporter = Reporter(System.out.sink().buffer())

        val dratProofSink = if (proofFile != null) {
            println("c Writing proof to '$proofFile'")
            proofFile!!.sink().buffer()
        } else {
            null
        }
        solver.dratBuilder = dratProofSink?.use {
            if (binaryProof) {
                BinaryDratBuilder(it)
            } else {
                DratBuilder(it)
            }
        } ?: NoOpDratBuilder()

        solver.config.timeLimit = timeLimit

        val (result, timeSolve) = measureTimedValue {
            solver.solve()
        }

        when (result) {
            SolveResult.SAT -> {
                println("s SATISFIABLE")
                if (!isNotPrintModel) {
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
            }

            SolveResult.UNSAT -> {
                println("s UNSATISFIABLE")
            }

            SolveResult.UNKNOWN -> {
                println("s UNKNOWN")
            }
        }

        solver.stats.write(System.out.sink().buffer())
        println("c Running time: ${timeSolve.toDouble(DurationUnit.SECONDS)} s")

        dratProofSink?.close()
    }
}

fun main(args: Array<String>) {
    KoSAT().main(args)
}
