import com.github.lipen.satlib.solver.MiniSatSolver
import com.soywiz.klock.measureTimeWithResult
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.SolverType
import org.kosat.readCnfRequests
import org.kosat.solveCnf
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sign
import kotlin.random.Random
import kotlin.streams.toList
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DiamondTests {
    private val projectDirAbsolutePath = Paths.get("").toAbsolutePath().toString()
    private val format = ".cnf"
    private val headerNames = listOf("Name:", "KoSAT time:", "MiniSAT time:", "Result:", "Solvable:")

    private fun getAllFilenames(): List<String> {
        val resourcesPath = Paths.get(projectDirAbsolutePath, testsPath)
        return Files.walk(resourcesPath)
            .filter { item -> Files.isRegularFile(item) }
            .map { item -> item.toString().substring(projectDirAbsolutePath.length + testsPath.length + 1) }
            .filter { it.endsWith(format) }.toList()
    }

    private fun getAssumptionFilenames(): List<String> {
        val resourcesPath = Paths.get(projectDirAbsolutePath, assumptionTestsPath)
        return Files.walk(resourcesPath)
            .filter { item -> Files.isRegularFile(item) }
            .map { item -> item.toString().substring(projectDirAbsolutePath.length + assumptionTestsPath.length + 1) }
            .filter { it.endsWith(format) }.toList()
    }

    private fun buildPadding(names: List<String>, padding: Int = 13, separator: String = " | "): String {
        val result = StringBuilder()

        names.forEachIndexed { ind, it ->

            result.append(it.padEnd(if (ind == 0) 40 else padding, ' '))
            result.append(separator)
        }
        return if (result.isNotEmpty()) result.dropLast(separator.length).toString() else ""
    }

    private fun processMiniSatSolver(input: String): Boolean {
        val data = readCnfRequests(input).first()

        with(MiniSatSolver()) {
            val lit = List(data.vars) { newLiteral() }
            for (clause in data.clauses)
                addClause(clause.lits.map { it.sign * lit[abs(it) - 1] })
            val result = solve()
            println("MiniSat conflicts: ${backend.numberOfConflicts}")
            return result
        }
    }

    private fun checkKoSatSolution(ans: List<Int>?, input: String, isSolution: Boolean): Boolean {

        if (ans == null) return !isSolution // null ~ UNSAT

        val cnfRequest = readCnfRequests(input).first()
        if (ans.size != cnfRequest.vars) return false

        return cnfRequest.clauses.all { clause -> clause.lits.any { ans[abs(it) - 1] == it } }
    }

    fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return round(this * multiplier) / multiplier
    }

    private fun runTest(filepath: String): Boolean {
        MiniSatSolver().close()

        val input = File(filepath).readText()

        val (solution, timeKoSat) = measureTimeWithResult { solveCnf(readCnfRequests(input).first()) }

        val (isSolution, timeMiniSat) = measureTimeWithResult { processMiniSatSolver(input) }

        val checkRes = if (checkKoSatSolution(solution, input, isSolution)) "OK" else "WA"

        println(
            buildPadding(
                listOf(
                    filepath.dropLast(format.length), // test name
                    timeKoSat.seconds.round(3).toString(),
                    timeMiniSat.seconds.round(3).toString(),
                    checkRes,
                    if (isSolution) "SAT" else "UNSAT"
                )
            )
        )
        if (checkRes == "WA") {
            return false
        }
        return true
    }

    private fun runAssumptionTest(filepath: String): Boolean {
        MiniSatSolver().close()

        val fileInput = File(filepath).readText()
        val lines = fileInput.split("\n", "\r", "\r\n").filter { line ->
            line.isNotEmpty() && line[0] != 'c'
        }
        val fileFirstLine = lines[0].split(' ')
        val variables = fileFirstLine[2]
        val clauses = fileFirstLine[3]

        val first = readCnfRequests(fileInput).first()

        val solver = CDCL(first.clauses as MutableList<Clause>, first.vars, SolverType.INCREMENTAL)

        var res = "OK"

        repeat(5) { ind ->
            val assumptions = if (first.vars == 0) listOf() else List(ind)
            { Random.nextInt(1, first.vars + 1) }.map {
                if (Random.nextBoolean()) it else -it
            }

            val input = fileFirstLine.dropLast(2).joinToString(" ") + " " +
                    (variables.toInt()).toString() + " " +
                    (clauses.toInt() + assumptions.size).toString() + "\n" +
                    lines.drop(1).joinToString(separator = "\n") +
                    assumptions.joinToString(prefix = "\n", separator = " 0\n", postfix = " 0")

            println(assumptions)

            val (isSolution, timeMiniSat) = measureTimeWithResult { processMiniSatSolver(input) }

            val (solution, timeKoSat) = measureTimeWithResult { solver.solve(assumptions) }

            val checkRes = if (checkKoSatSolution(solution, input, isSolution)) "OK" else "WA"

            if (checkRes == "WA") res = "WA"

            println(
                buildPadding(
                    listOf(
                        filepath.dropLast(format.length), // test name
                        timeKoSat.seconds.round(3).toString(),
                        timeMiniSat.seconds.round(3).toString(),
                        checkRes,
                        if (isSolution) "SAT" else "UNSAT"
                    )
                )
            )
        }
        if (res == "WA") {
            return false
        }
        return true
    }

    private val testsPath = "src/jvmTest/resources"

    @ParameterizedTest(name = "{0}")
    @MethodSource("getAllFilenames")
    fun test(filepath: String) {
        assertEquals(true, runTest("$testsPath$filepath"))
    }

    private val assumptionTestsPath = "src/jvmTest/resources/testCover/small"

    @ParameterizedTest(name = "{0}")
    @MethodSource("getAssumptionFilenames")
    fun assumptionTest(filepath: String) {
        assertEquals(true, runAssumptionTest("$assumptionTestsPath$filepath"))
    }
}
