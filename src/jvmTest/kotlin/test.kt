import com.github.lipen.satlib.solver.MiniSatSolver
import com.soywiz.klock.measureTimeWithResult
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.kosat.CDCL
import org.kosat.DimacsLiteral
import org.kosat.LBool
import org.kosat.get
import org.kosat.readCnfRequests
import org.kosat.round
import org.kosat.solveCnf
import java.io.File
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
                addClause(clause.dimacsLiterals.map { it.value.sign * lit[abs(it.value) - 1] })
            val result = solve()
            println("MiniSat conflicts: ${backend.numberOfConflicts}")
            println("Minisat decisions: ${backend.numberOfDecisions}")
            return result
        }
    }

    private fun checkKoSatSolution(ans: List<LBool>?, input: String, isSolution: Boolean): Boolean {
        if (ans == null) return !isSolution // null ~ UNSAT

        val cnfRequest = readCnfRequests(input).first()
        if (ans.size != cnfRequest.vars) return false

        return cnfRequest.clauses.all { clause ->
            clause.toClause().any {
                if (it.isPos) {
                    ans[it.variable] == LBool.TRUE
                } else {
                    ans[it.variable] == LBool.FALSE
                }
            }
        }
    }

    private fun runTest(filepath: String): Boolean {
        MiniSatSolver().close()

        val input = File(filepath).readText()

        val (isSolution, timeMiniSat) = measureTimeWithResult { processMiniSatSolver(input) }

        val (solution, timeKoSat) = measureTimeWithResult { solveCnf(readCnfRequests(input).first()) }

        val checkRes = if (checkKoSatSolution(solution, input, isSolution)) "OK" else "WA"

        println(
            buildPadding(
                listOf(
                    filepath.dropLast(format.length), // test name
                    timeKoSat.seconds.round(3).toString(),
                    timeMiniSat.seconds.round(3).toString(),
                    checkRes,
                    if (isSolution) "SAT" else "UNSAT",
                ),
            ),
        )

        return checkRes != "WA"
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

        val solver = CDCL(first.clauses.map { it.toClause() }, first.vars)

        var res = "OK"

        repeat(5) { ind ->
            val random = Random(ind)
            val assumptions = if (first.vars == 0) {
                listOf()
            } else {
                List(ind) {
                    random.nextInt(1, first.vars + 1)
                }.map {
                    DimacsLiteral(if (Random.nextBoolean()) it else -it)
                }
            }

            val input = fileFirstLine.dropLast(2).joinToString(" ") + " " +
                (variables.toInt()).toString() + " " +
                (clauses.toInt() + assumptions.size).toString() + "\n" +
                lines.drop(1).joinToString(separator = "\n") +
                assumptions.map { it.value }.joinToString(prefix = "\n", separator = " 0\n", postfix = " 0")

            val (isSolution, timeMiniSat) = measureTimeWithResult { processMiniSatSolver(input) }

            val (solution, timeKoSat) = measureTimeWithResult { solver.solve(assumptions.map { it.toLiteral() }) }

            val checkRes = if (checkKoSatSolution(solution, input, isSolution)) "OK" else "WA"

            if (checkRes == "WA") res = "WA"

            println(
                buildPadding(
                    listOf(
                        filepath.dropLast(format.length), // test name
                        timeKoSat.seconds.round(3).toString(),
                        timeMiniSat.seconds.round(3).toString(),
                        checkRes,
                        if (isSolution) "SAT" else "UNSAT",
                    ),
                ),
            )
        }
        return res != "WA"
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
