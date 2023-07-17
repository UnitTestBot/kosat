package org.kosat

import com.github.lipen.satlib.solver.MiniSatSolver
import korlibs.time.measureTimeWithResult
import korlibs.time.roundMilliseconds
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.kosat.cnf.CNF
import org.kosat.cnf.from
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
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
    private val projectDirAbsolutePath = Paths.get("").toAbsolutePath().toString()
    private val format = ".cnf"
    private val dratProofsPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
        .resolve("dratProofs/${LocalDateTime.now()}")

    init {
        dratProofsPath.toFile().mkdirs()
    }

    private fun getAllFilenames(): List<String> {
        val resourcesPath = Paths.get(projectDirAbsolutePath, testsPath)
        return Files.walk(resourcesPath)
            .filter { Files.isRegularFile(it) }
            .map { it.toString().substring(projectDirAbsolutePath.length + testsPath.length + 1) }
            .filter { it.endsWith(format) }
            // FIXME: temporarily skip 'benchmarks':
            .filter { !it.contains("benchmark") }
            .toList()
    }

    private fun getAssumptionFilenames(): List<String> {
        val resourcesPath = Paths.get(projectDirAbsolutePath, assumptionTestsPath)
        return Files.walk(resourcesPath)
            .filter { Files.isRegularFile(it) }
            .map { it.toString().substring(projectDirAbsolutePath.length + assumptionTestsPath.length + 1) }
            .filter { it.endsWith(format) }
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

    private fun runTest(cnfPath: Path, cnf: CNF) {
        val (resultExpected, timeMiniSat) = measureTimeWithResult {
            solveWithMiniSat(cnf)
        }

        val solver = CDCL(cnf)

        val dratFile = dratProofsPath.resolve("${cnfPath.toFile().nameWithoutExtension}.drat")
        solver.dratBuilder = DratBuilder(FileSystem.SYSTEM.sink(dratFile).buffer())

        val (resultActual, timeKoSat) = measureTimeWithResult {
            solver.solve()
        }

        if (resultActual == SolveResult.SAT) {
            dratFile.toFile().delete()
        } else {
            val command = "drat-trim ${cnfPath.toNioPath().toAbsolutePath()} $dratFile -U"
            println("DRAT-TRIM command: $command")

            val validator = Runtime.getRuntime().exec(command)

            val finished = validator.waitFor(10, TimeUnit.SECONDS)

            if (!finished) {
                validator.destroy()
                throw Exception("DRAT-TRIM takes too long")
            }

            val stdout = validator.inputStream.bufferedReader().readLines().filter { it.isNotBlank() }

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
        }

        assertEquals(resultExpected, resultActual, "MiniSat and KoSat results are different. ")

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

    private val testsPath = "src/jvmTest/resources"

    @ParameterizedTest(name = "{0}")
    @MethodSource("getAllFilenames")
    fun test(filepath: String) {
        val path = "$testsPath$filepath".toPath()
        println("# Testing on: $path")
        runTest(path, CNF.from(path))
    }

    private val assumptionTestsPath = "$testsPath/testCover/small"

    @ParameterizedTest(name = "{0}")
    @MethodSource("getAssumptionFilenames")
    fun assumptionTest(filepath: String) {
        val cnf = CNF.from("$assumptionTestsPath$filepath".toPath())
        val clauseCount = cnf.clauses.size

        println("# Testing on: $filepath ($clauseCount clauses, ${cnf.numVars} variables)")

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
