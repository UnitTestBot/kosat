package org.kosat

import com.github.lipen.satlib.solver.MiniSatSolver
import korlibs.time.DateTime
import korlibs.time.measureTimeWithResult
import korlibs.time.roundMilliseconds
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.buffer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.kosat.cnf.CNF
import org.kosat.cnf.from
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random
import kotlin.streams.toList
import kotlin.test.assertContains

private const val timeFormat = "yyyy-MM-dd_HH-mm-ss"

internal class DiamondTests {
    companion object {
        private val workingDir = Paths.get("").toAbsolutePath()
        private val testsPath = workingDir.resolve("src/jvmTest/resources")
        private val assumptionTestsPath = testsPath.resolve("testCover")
        private val benchmarksPath = testsPath.resolve("benchmarks")

        private val dratTrimExecutable: Path = Paths.get("drat-trim")
        private val generateAndCheckDrat = System.getenv()["TEST_CHECK_UNSAT_PROOF"]?.let { it == "true" } ?: false

        private const val ext = "cnf"
        private val dratProofsPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
            .resolve("dratProofs/${DateTime.nowLocal().format(timeFormat)}")

        init {
            if (generateAndCheckDrat) {
                dratProofsPath.toFile().mkdirs()
                System.err.println(
                    "DRAT proofs will be generated to $dratProofsPath " +
                        "and verified using $dratTrimExecutable"
                )
            } else {
                System.err.println("DRAT proofs will not be generated")
            }
        }

        private fun isTestFile(path: Path): Boolean {
            return path.isRegularFile() && path.extension == ext
        }

        @JvmStatic
        private fun getAllNotBenchmarks(): List<Arguments> {
            return Files.walk(testsPath)
                .filter { isTestFile(it) }
                .filter { !it.startsWith(benchmarksPath) }
                .map { Arguments.of(it.toFile(), it.relativeTo(testsPath).toString()) }
                .toList()
        }

        @JvmStatic
        private fun getAssumptionFiles(): List<Arguments> {
            return Files.walk(assumptionTestsPath)
                .filter { isTestFile(it) }
                .map { Arguments.of(it.toFile(), it.relativeTo(testsPath).toString()) }
                .toList()
        }

        @JvmStatic
        private fun getBenchmarkFiles(): List<Arguments> {
            return Files.walk(benchmarksPath)
                .filter { isTestFile(it) }
                .map { Arguments.of(it.toFile(), it.relativeTo(testsPath).toString()) }
                .toList()
        }
    }

    private fun solveWithMiniSat(cnf: CNF): SolveResult {
        MiniSatSolver().close()

        return with(MiniSatSolver()) {
            val lits = List(cnf.numVars) { newLiteral() }
            for (clause in cnf.clauses) {
                addClause(clause.map { it.sign * lits[abs(it) - 1] })
            }
            val result = if (solve()) {
                SolveResult.SAT
            } else {
                SolveResult.UNSAT
            }
            println("MiniSat conflicts: ${backend.numberOfConflicts}")
            println("Minisat decisions: ${backend.numberOfDecisions}")

            result
        }
    }

    private fun runTest(cnfFile: File, cnf: CNF) {
        val (resultExpected, timeMiniSat) = measureTimeWithResult {
            solveWithMiniSat(cnf)
        }

        val solver = CDCL(cnf)

        val dratPath = dratProofsPath.resolve("${cnfFile.nameWithoutExtension}.drat")

        if (generateAndCheckDrat) {
            solver.dratBuilder = DratBuilder(FileSystem.SYSTEM.sink(dratPath).buffer())
        }

        val (resultActual, timeKoSat) = measureTimeWithResult {
            solver.solve()
        }

        Assertions.assertEquals(resultExpected, resultActual) { "MiniSat and KoSat results are different." }

        println("MiniSat and KoSat results are the same: $resultActual")

        if (resultActual == SolveResult.UNSAT) {
            if (!generateAndCheckDrat) {
                println(
                    "Path to DRAT-TRIM in environment variable DRAT_TRIM_EXECUTABLE is not set. " +
                        "Skipping DRAT-trim test."
                )
            } else {
                val command = "$dratTrimExecutable ${cnfFile.absolutePath} $dratPath -U -f"

                println("DRAT-TRIM command: $command")

                val validator = Runtime.getRuntime().exec(command)

                val stdout = validator.inputStream.bufferedReader().readLines().filter { it.isNotBlank() }

                validator.waitFor()

                println("DRAT-TRIM stdout:")
                println(stdout.joinToString("\n\t", prefix = "\t"))
                println("DRAT-TRIM stdout end")

                assertContains(stdout, "s VERIFIED")

                Assertions.assertNotEquals(
                    80,
                    validator.exitValue()
                ) {
                    "DRAT-TRIM exited with code 80 " +
                        "(possibly because of a termination due to warning if ran with -W flag)"
                }
            }
        } else {
            if (generateAndCheckDrat) {
                dratPath.toFile().renameTo(dratPath.parent!!.resolve("sats/${dratPath.name}").toFile())
            }

            val model = solver.getModel()

            for (clause in cnf.clauses) {
                var satisfied = false
                for (lit in clause) {
                    if (model[abs(lit) - 1] == (lit.sign == 1)) {
                        satisfied = true
                        break
                    }
                }

                Assertions.assertTrue(satisfied) { "Clause $clause is not satisfied. Model: $model" }
            }
        }

        println("MiniSat time: ${timeMiniSat.roundMilliseconds()}")
        println("KoSat time: ${timeKoSat.roundMilliseconds()}")
    }

    private fun runTestWithAssumptions(cnf: CNF, assumptionsSets: List<List<Int>>) {
        val solver = CDCL(cnf)

        for (assumptions in assumptionsSets) {
            println("## Solving with assumptions: $assumptions")
            val cnfWithAssumptions = CNF(cnf.clauses + assumptions.map { listOf(it) }, cnf.numVars)

            val (resultExpected, timeMiniSat) = measureTimeWithResult {
                solveWithMiniSat(cnfWithAssumptions)
            }

            val (resultActual, timeKoSat) = measureTimeWithResult {
                solver.solve(assumptions.map { Lit.fromDimacs(it) })
            }

            Assertions.assertEquals(resultExpected, resultActual) { "MiniSat and KoSat results are different" }
            println("MiniSat and KoSat results are the same: $resultActual")

            if (resultActual == SolveResult.SAT) {
                val model = solver.getModel()

                for (clause in cnf.clauses) {
                    var satisfied = false
                    for (lit in clause) {
                        if (model[abs(lit) - 1] == (lit.sign == 1)) {
                            satisfied = true
                            break
                        }
                    }

                    Assertions.assertTrue(satisfied) { "Clause $clause is not satisfied. Model: $model" }
                }

                for (assumption in assumptions) {
                    val assumptionValue = model[abs(assumption) - 1] == (assumption.sign == 1)
                    Assertions.assertTrue(assumptionValue) { "Assumption $assumption is not satisfied. Model: $model" }
                }
            }

            println("MiniSat time: ${timeMiniSat.roundMilliseconds()}")
            println("KoSat time: ${timeKoSat.roundMilliseconds()}")
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("getAllNotBenchmarks")
    fun test(file: File, testName: String) {
        println("# Testing on: $file")
        runTest(file, CNF.from(file.toOkioPath()))
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("getBenchmarkFiles")
    @Disabled
    fun testOnBenchmarks(file: File, testName: String) {
        println("# Testing on: $file")
        runTest(file, CNF.from(file.toOkioPath()))
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("getAssumptionFiles")
    fun assumptionTest(file: File, testName: String) {
        val cnf = CNF.from(file.toOkioPath())

        val clauseCount = cnf.clauses.size
        println("# Testing on: $file ($clauseCount clauses, ${cnf.numVars} variables)")

        if (cnf.numVars == 0) {
            return
        }

        val assumptionSets = List(5) { i ->
            val random = Random(i)

            List(i) {
                random.nextInt(1, cnf.numVars + 1)
            }.map {
                if (random.nextBoolean()) it else -it
            }
        }

        runTestWithAssumptions(cnf, assumptionSets)

        println()
    }
}
