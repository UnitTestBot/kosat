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

    private fun runTest(cnf: CNF, assumptions: List<Int>? = null) {
        val (isSatExpected, timeMiniSat) = measureTimeWithResult {
            val cnfWithAssumptions = if (assumptions != null) {
                val newClauses = assumptions.map { listOf(it) }
                // intentional copy when adding new clauses
                CNF(cnf.clauses + newClauses)
            } else {
                cnf
            }

            MiniSatSolver().close()
            with(MiniSatSolver()) {
                val lits = List(cnfWithAssumptions.numVars) { newLiteral() }
                for (clause in cnfWithAssumptions.clauses) {
                    addClause(clause.map { it.sign * lits[abs(it) - 1] })
                }
                val result = solve()
                println("MiniSat conflicts: ${backend.numberOfConflicts}")
                println("Minisat decisions: ${backend.numberOfDecisions}")

                result
            }
        }

        val solver = CDCL(cnf)

        val (isSatActual, timeKoSat) = measureTimeWithResult {
            val result = if (assumptions != null) {
                solver.solve(assumptions.map { Lit.fromDimacs(it) })
            } else {
                solver.solve()
            }

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

            if (assumptions != null) {
                for (assumption in assumptions) {
                    assert(model[abs(assumption) - 1] == (assumption.sign == 1)) {
                        "Assumption $assumption is not satisfied. Model: $model"
                    }
                }
            }
        }

        println("MiniSat time: ${timeMiniSat.roundMilliseconds()}")
        println("KoSat time: ${timeKoSat.roundMilliseconds()}")
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

        for (i in 1..5) {
            val random = Random(i)

            val assumptions = List(i) {
                random.nextInt(1, cnf.numVars + 1)
            }.map {
                if (random.nextBoolean()) it else -it
            }

            println("## Testing with assumptions: $assumptions")

            runTest(cnf, assumptions)
        }

        println()
    }
}
