package org.kosat

import com.github.lipen.satlib.solver.MiniSatSolver
import korlibs.time.DateTime
import korlibs.time.TimeSpan
import korlibs.time.measureTimeWithResult
import korlibs.time.roundMilliseconds
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Sink
import okio.Path.Companion.toPath
import okio.buffer
import org.junit.jupiter.api.Assertions
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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.time.Duration
import kotlin.time.measureTime

private const val timeFormat = "yyyy-MM-dd_HH-mm-ss"

internal class DiamondTests {
    companion object {
        private val workingDir = Paths.get("").toAbsolutePath()
        private val testsPath = workingDir.resolve("src/jvmTest/resources")
        private val assumptionTestsPath = testsPath.resolve("testCover")
        private val benchmarksPath = testsPath.resolve("benchmarks")
        private val incrementalTestsPath = testsPath.resolve("testCover")

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
        private fun getIncrementalFiles(): List<Arguments> {
            return Files.walk(incrementalTestsPath)
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
        return MiniSatSolver().use { minisat ->
            val lits = List(cnf.numVars) { minisat.newLiteral() }
            for (clause in cnf.clauses) {
                minisat.addClause(clause.map { it.sign * lits[abs(it) - 1] })
            }
            val result = if (minisat.solve()) {
                SolveResult.SAT
            } else {
                SolveResult.UNSAT
            }
            println("MiniSat conflicts: ${minisat.backend.numberOfConflicts}")
            println("Minisat decisions: ${minisat.backend.numberOfDecisions}")
            println("MiniSat result: $result")

            result
        }
    }

    private fun runTest(cnfFile: File, cnf: CNF) {
        val (resultExpected, timeMiniSat) = measureTimeWithResult {
            solveWithMiniSat(cnf)
        }

        val solver = CDCL(cnf)

        val dratPath = dratProofsPath.resolve("${cnfFile.nameWithoutExtension}.drat")
        var dratSink: Sink? = null
        var dratBufferedSink: okio.BufferedSink? = null

        val dratBuilder = if (generateAndCheckDrat) {
            dratSink = FileSystem.SYSTEM.sink(dratPath)
            dratBufferedSink = dratSink.buffer()
            DratBuilder(dratBufferedSink)
        } else {
            NoOpDratBuilder()
        }

        solver.dratBuilder = dratBuilder

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

        dratSink?.close()
        dratBufferedSink?.close()

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

    private fun runIncrementalTest(cnf: CNF) {
        val solver = CDCL(cnf)
        var fullCnf = cnf

        val getInstances = 10
        val results = mutableListOf<List<Boolean>>()

        for (i in 0 until getInstances) {
            val expectedResult = solveWithMiniSat(fullCnf)

            val actualResult = solver.solve()

            Assertions.assertEquals(expectedResult, actualResult) { "MiniSat and KoSat results are different" }
            println("MiniSat and KoSat results are the same: $actualResult")

            if (actualResult == SolveResult.SAT) {
                val model = solver.getModel()

                for (clause in fullCnf.clauses) {
                    var satisfied = false
                    for (lit in clause) {
                        if (model[abs(lit) - 1] == (lit.sign == 1)) {
                            satisfied = true
                            break
                        }
                    }

                    Assertions.assertTrue(satisfied) { "Clause $clause is not satisfied. Model: $model" }
                }

                results.add(model)

                val incrementalDimacsClause = model.mapIndexed { index, value ->
                    if (value) index + 1 else -(index + 1)
                }

                fullCnf = CNF(fullCnf.clauses + listOf(incrementalDimacsClause), fullCnf.numVars)

                val incrementalClause = Clause.fromDimacs(incrementalDimacsClause)

                solver.newClause(incrementalClause)
            } else {
                break
            }
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
    // @Disabled
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

        val assumptionSets = List(15) { i ->
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

    @ParameterizedTest(name = "{1}")
    @MethodSource("getIncrementalFiles")
    fun incrementalTest(file: File, testName: String) {
        val cnf = CNF.from(file.toOkioPath())
        val clauseCount = cnf.clauses.size
        println("# Testing on: $file ($clauseCount clauses, ${cnf.numVars} variables)")

        runIncrementalTest(cnf)
    }

    @Test
    fun justBenchmarkWithSatElite() {
        var total = Duration.ZERO
        val files = Files.walk(benchmarksPath).filter { isTestFile(it) }.toList()
        for ((i, file) in files.withIndex()) {
            println("${i + 1}/${files.size} # Testing on: $file")
            val command = "/home/elt/Desktop/SatELite_v1.0_linux $file /tmp/out.cnf"
            println(command)
            val preprocessor = Runtime.getRuntime().exec(command)
            preprocessor.waitFor()
            val time = measureTime {
                val solver = CDCL(CNF.from("/tmp/out.cnf".toPath()))
                val result = solver.solve()
                println("Result: $result")
            }

            total += time
            println("Time: $time")
        }

        println("Total time: $total")
    }

    @Test
    fun justBenchmarkWithMinisat2() {
        var total = Duration.ZERO
        val files = Files.walk(benchmarksPath).filter { isTestFile(it) }.toList()
        for ((i, file) in files.withIndex()) {
            println("${i + 1}/${files.size} # Testing on: $file")
            val command = "minisat $file -dimacs=/tmp/out.cnf"
            println(command)
            val preprocessor = Runtime.getRuntime().exec(command)
            preprocessor.waitFor()
            val time = measureTime {
                val solver = CDCL(CNF.from("/tmp/out.cnf".toPath()))
                val result = solver.solve()
                println("Result: $result")
            }

            total += time
            println("Time: $time")
        }

        println("Total time: $total")
    }

    fun r1() {
        println("========== RUN 1 ==========")
        var total = Duration.ZERO
        val files = Files.walk(benchmarksPath).filter { isTestFile(it) }.toList()
        for ((i, file) in files.withIndex()) {
            println("${i + 1}/${files.size} # Testing on: $file")
            // val command = "/home/elt/Desktop/SatELite_v1.0_linux $file /tmp/out.cnf"
            // println(command)
            // val preprocessor = Runtime.getRuntime().exec(command)
            // preprocessor.waitFor()
            val time = measureTime {
                val solver = CDCL(CNF.from(file.toOkioPath()))
                val result = solver.solve()
                println("Result: $result")
            }

            total += time
            println("Time: $time")
        }

        println("Total time: $total")
    }

    fun r2() {
        println("========== RUN 2 ==========")
        var total = Duration.ZERO
        val files = Files.walk(benchmarksPath).filter { isTestFile(it) }.toList()
        for ((i, file) in files.withIndex()) {
            println("${i + 1}/${files.size} # Testing on: $file")
            // val command = "/home/elt/Desktop/SatELite_v1.0_linux $file /tmp/out.cnf"
            // println(command)
            // val preprocessor = Runtime.getRuntime().exec(command)
            // preprocessor.waitFor()
            val time = measureTime {
                val solver = CDCL(CNF.from(file.toOkioPath()))
                val result = solver.solve()
                println("Result: $result")
            }

            total += time
            println("Time: $time")
        }

        println("Total time: $total")
    }

    @Test
    fun justBenchmark() {
        r1()
        r2()
    }
}
