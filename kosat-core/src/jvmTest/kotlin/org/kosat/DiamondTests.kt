package org.kosat

import com.github.lipen.satlib.solver.MiniSatSolver
import korlibs.time.measureTimeWithResult
import korlibs.time.roundMilliseconds
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.buffer
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.kosat.cnf.CNF
import org.kosat.cnf.from
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random
import kotlin.streams.toList
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// Lazy messages are not in JUnit
// and interpolating strings where we have to is too expensive
private fun assertTrue(value: Boolean, lazyMessage: () -> String) {
    if (!value) println(lazyMessage())
    assertTrue(value)
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DiamondTests {
    private val workingDir = Paths.get("").toAbsolutePath()
    private val testsPath = workingDir.resolve("src/jvmTest/resources")
    private val assumptionTestsPath = testsPath.resolve("testCover/small")
    private val benchmarksPath = testsPath.resolve("benchmarks")

    private val format = "cnf"

    private val dratProofsPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
        .resolve("dratProofs/${LocalDateTime.now()}")

    init {
        dratProofsPath.toFile().mkdirs()
    }

    private fun isTestFile(path: Path): Boolean {
        return path.isRegularFile() && path.extension == format
    }

    private fun getAllNotBenchmarks(): List<Arguments> {
        return Files.walk(testsPath)
            .filter { isTestFile(it) }
            .filter { !it.startsWith(benchmarksPath) }
            .map { Arguments { arrayOf(it.toFile(), it.relativeTo(testsPath).toString()) } }
            .toList()
    }

    private fun getAssumptionFiles(): List<Arguments> {
        return Files.walk(assumptionTestsPath)
            .filter { isTestFile(it) }
            .map { Arguments { arrayOf(it.toFile(), it.relativeTo(testsPath).toString()) } }
            .toList()
    }

    private fun getBenchmarkFiles(): List<Arguments> {
        return Files.walk(benchmarksPath)
            .filter { isTestFile(it) }
            .map { Arguments { arrayOf(it.toFile(), it.relativeTo(testsPath).toString()) } }
            .toList()
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
        solver.dratBuilder = DratBuilder(FileSystem.SYSTEM.sink(dratPath).buffer())

        val (resultActual, timeKoSat) = measureTimeWithResult {
            solver.solve()
        }

        assertEquals(resultExpected, resultActual, "MiniSat and KoSat results are different.")

        if (resultActual == SolveResult.UNSAT) {
            val command = "drat-trim ${cnfFile.absolutePath} $dratPath -U -f"

            println("DRAT-TRIM command: $command")

            val validator = Runtime.getRuntime().exec(command)

            val stdout = validator.inputStream.bufferedReader().readLines().filter { it.isNotBlank() }

            validator.waitFor()

            println("DRAT-TRIM stdout:")
            println(stdout.joinToString("\n\t", prefix = "\t"))
            println("DRAT-TRIM stdout end")

            assertContains(stdout, "s VERIFIED")

            assertNotEquals(
                80,
                validator.exitValue(),
                "DRAT-TRIM exited with code 80 " +
                        "(possibly because of a termination due to warning if ran with -W flag)",
            )
        } else {
            dratPath.toFile().renameTo(dratPath.parent!!.resolve("sats/${dratPath.name}").toFile())

            val model = solver.getModel()

            for (clause in cnf.clauses) {
                var satisfied = false
                for (lit in clause) {
                    if (model[abs(lit) - 1] == (lit.sign == 1)) {
                        satisfied = true
                        break
                    }
                }

                assertTrue(satisfied) { "Clause $clause is not satisfied. Model: $model" }
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

            assertEquals(resultExpected, resultActual, "MiniSat and KoSat results are different")

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

                    assertTrue(satisfied) { "Clause $clause is not satisfied. Model: $model" }
                }

                for (assumption in assumptions) {
                    val assumptionValue = model[abs(assumption) - 1] == (assumption.sign == 1)
                    assertTrue(assumptionValue) { "Assumption $assumption is not satisfied. Model: $model" }
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
