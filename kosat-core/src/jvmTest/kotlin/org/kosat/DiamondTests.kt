package org.kosat

import com.github.lipen.satlib.solver.MiniSatSolver
import korlibs.time.measureTimeWithResult
import korlibs.time.roundMilliseconds
import okio.Path.Companion.toPath
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.kosat.cnf.CNF
import org.kosat.cnf.from
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random
import kotlin.streams.toList
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DiamondTests {
    private val projectDirAbsolutePath = Paths.get("").toAbsolutePath().toString()
    private val format = ".cnf"

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

    private fun solveWithMiniSat(cnf: CNF): Boolean {
        MiniSatSolver().close()

        return with(MiniSatSolver()) {
            val lits = List(cnf.numVars) { newLiteral() }
            for (clause in cnf.clauses) {
                addClause(clause.map { it.sign * lits[abs(it) - 1] })
            }
            val result = solve()
            println("MiniSat conflicts: ${backend.numberOfConflicts}")
            println("Minisat decisions: ${backend.numberOfDecisions}")

            result
        }
    }

    private fun runTest(cnf: CNF) {
        val (isSatExpected, timeMiniSat) = measureTimeWithResult {
            solveWithMiniSat(cnf)
        }

        val solver = CDCL(cnf)

        val (isSatActual, timeKoSat) = measureTimeWithResult {
            val result = solver.solve()
            result == SolveResult.SAT
        }

        assertEquals(isSatExpected, isSatActual, "MiniSat and KoSat results are different")

        if (isSatActual) {
            val model = solver.getModel()

            for (clause in cnf.clauses) {
                var satisfied = false
                for (lit in clause) {
                    if (model[abs(lit) - 1] == (lit.sign == 1)) {
                        satisfied = true
                        break
                    }
                }

                assert(satisfied) { "Clause $clause is not satisfied. Model: $model" }
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

            val (isSatExpected, timeMiniSat) = measureTimeWithResult {
                solveWithMiniSat(cnfWithAssumptions)
            }

            val (isSatActual, timeKoSat) = measureTimeWithResult {
                val result = solver.solve(assumptions.map { Lit.fromDimacs(it) })
                result == SolveResult.SAT
            }

            assertEquals(isSatExpected, isSatActual, "MiniSat and KoSat results are different")

            if (isSatActual) {
                val model = solver.getModel()

                for (clause in cnf.clauses) {
                    var satisfied = false
                    for (lit in clause) {
                        if (model[abs(lit) - 1] == (lit.sign == 1)) {
                            satisfied = true
                            break
                        }
                    }

                    assert(satisfied) { "Clause $clause is not satisfied. Model: $model" }
                }

                for (assumption in assumptions) {
                    val assumptionValue = model[abs(assumption) - 1] == (assumption.sign == 1)
                    assert(assumptionValue) { "Assumption $assumption is not satisfied. Model: $model" }
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
        println("# Testing on: $filepath")
        runTest(CNF.from("$testsPath$filepath".toPath()))
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
