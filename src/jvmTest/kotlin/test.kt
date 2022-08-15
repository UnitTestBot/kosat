
import com.github.lipen.satlib.solver.MiniSatSolver
import com.soywiz.klock.measureTimeWithResult
import org.junit.jupiter.api.Test
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

    private fun getAllFilenames() : List<String> {
        val resourcesPath = Paths.get(projectDirAbsolutePath, testsPath)
        return Files.walk(resourcesPath)
            .filter { item -> Files.isRegularFile(item) }
            .map { item -> item.toString().substring(projectDirAbsolutePath.length + testsPath.length + 1) }
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

        if (ans.isEmpty()) return false

        val cnfRequest = readCnfRequests(input).first()
        if (ans.size != cnfRequest.vars) return false

        return cnfRequest.clauses.all { clause -> clause.lits.any { ans.contains(it) } }
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
            buildPadding(listOf(
                filepath, // test name
                timeKoSat.seconds.round(3).toString(),
                timeMiniSat.seconds.round(3).toString(),
                checkRes,
                if (isSolution) "SAT" else "UNSAT"
            ))
        )
        if (checkRes == "WA") {
            return false
        }
        return true
    }


    private fun runTests(path: String) : Boolean {
        val filenames = getAllFilenamesByPath()//.filter { !it.startsWith("benchmark") }
        println(filenames)
        println(buildPadding(headerNames))

        // trigger the shared library loading
        MiniSatSolver().close()

        var allCorrect = true


        filenames.forEach {
            val filename = path + it

            val input = File(filename).readText()

            val (solution, timeKoSat) = measureTimeWithResult { solveCnf(readCnfRequests(input).first()) }

            val (isSolution, timeMiniSat) = measureTimeWithResult { processMiniSatSolver(input) }

            val checkRes = if (checkKoSatSolution(solution, input, isSolution)) "OK" else "WA"

            if (checkRes == "WA") {
                allCorrect = false
            }

            println(
                buildPadding(listOf(
                    it.dropLast(format.length), // test name
                    timeKoSat.seconds.round(3).toString(),
                    timeMiniSat.seconds.round(3).toString(),
                    checkRes,
                    if (isSolution) "SAT" else "UNSAT"
                ))
            )
        }
        return allCorrect
    }

    private fun getAllFilenamesByPath(path: String) : List<String> {
        val resourcesPath = Paths.get(projectDirAbsolutePath, path)
        return Files.walk(resourcesPath)
            .filter { item -> Files.isRegularFile(item) }
            .map { item -> item.toString().substring(projectDirAbsolutePath.length + path.length + 1) }.filter { it.endsWith(format) }.toList()
    }

    @Test
    fun testAssumptions() {
        val path = "src/jvmTest/resources/testCover/small"

        val filenames = getAllFilenamesByPath(path)
        //println(filenames)
        //println(buildPadding(headerNames))

        // trigger the shared library loading
        MiniSatSolver().close()


        filenames.forEach { filename ->
            val filepath = path + filename

            val fileInput = File(filepath).readText()
            val lines = fileInput.split("\n", "\r", "\r\n").filter { line ->
                line.isNotEmpty() && line[0] != 'c'
            }
            val fileFirstLine = lines[0].split(' ')
            val variables = fileFirstLine[2]
            val clauses = fileFirstLine[3]

            val first = readCnfRequests(fileInput).first()

            val solver = CDCL(first.clauses as MutableList<Clause>, first.vars, SolverType.INCREMENTAL)

            repeat(5) { ind ->
                val assumptions = if (first.vars == 0) listOf() else List(ind)
                { Random.nextInt(1, first.vars + 1 ) }.map {
                    if (Random.nextBoolean()) it else -it
                }

                val input = fileFirstLine.dropLast(2).joinToString(" ") + " " +
                        (variables.toInt()).toString() + " " +
                        (clauses.toInt() + assumptions.size).toString() + "\n" +
                        lines.drop(1).joinToString(separator = "\n") +
                        assumptions.joinToString(prefix = "\n", separator = " 0\n", postfix =  " 0")

                println(assumptions)

                val (isSolution, timeMiniSat) = measureTimeWithResult { processMiniSatSolver(input) }

                val (solution, timeKoSat) = measureTimeWithResult { solver.solve(assumptions) }

                val checkRes = if (checkKoSatSolution(solution, input, isSolution)) "OK" else "WA"

                println(
                    buildPadding(listOf(
                        filename.dropLast(format.length), // test name
                        timeKoSat.seconds.round(3).toString(),
                        timeMiniSat.seconds.round(3).toString(),
                        checkRes,
                        if (isSolution) "SAT" else "UNSAT"
                    ))
                )
            }
        }
    }

    private val testsPath = "src/jvmTest/resources/testCover"

    @ParameterizedTest(name = "{0}")
    @MethodSource("getAllFilenames")
    fun test(filepath: String) {
        assertEquals(runTest("$testsPath$filepath"), true)
    }
}
