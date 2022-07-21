import org.junit.jupiter.api.Test
import org.kosat.readCnfRequests
import java.io.File
import kotlin.system.measureTimeMillis
import com.github.lipen.satlib.solver.MiniSatSolver
import org.kosat.solveCnf
import org.kosat.solveWithAssumptions
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random
import kotlin.streams.toList

internal class DiamondTests {
    private val projectDirAbsolutePath = Paths.get("").toAbsolutePath().toString()
    private val format = ".cnf"
    private val headerNames = listOf("Name:", "KoSAT time:", "MiniSAT time:", "Result:", "Solvable:")

    private fun getAllFilenamesByPath(path: String): List<String> {
        val resourcesPath = Paths.get(projectDirAbsolutePath, path)
        return Files.walk(resourcesPath)
            .filter { item -> Files.isRegularFile(item) }
            .map { item -> item.toString().substring(projectDirAbsolutePath.length + path.length + 1) }.toList()
    }


    private fun buildPadding(names: List<String>, padding: Int = 13, separator: String = " | "): String {
        val result = StringBuilder()

        names.forEach {
            result.append(it.padEnd(padding, ' '))
            result.append(separator)
        }
        return if (result.isNotEmpty()) result.dropLast(separator.length).toString() else ""
    }

    private fun processMiniSatSolver(input: String): Boolean {
        val data = readCnfRequests(input).first()

        with(MiniSatSolver()) {
            val lit = List(data.vars) { newLiteral() }
            for (clause in data.clauses)
                addClause(clause.lit.map { it.sign * lit[abs(it) - 1] })

            return solve()
        }
    }

    private fun checkKoSatSolution(ans: List<Int>?, input: String, isSolution: Boolean): Boolean {
        if (ans == null) return !isSolution // null ~ UNSAT

        val cnfRequest = readCnfRequests(input).first()
        if (ans.size != cnfRequest.vars) return false

        return cnfRequest.clauses.all { clause -> clause.lit.any { ans.contains(it) } }
    }

    private fun runTests(path: String) {
        val filenames = getAllFilenamesByPath(path)
        println(filenames)
        println(buildPadding(headerNames))

        // trigger the shared library loading
        MiniSatSolver().close()


        filenames.forEach {
            val filename = path + it

            val input = File(filename).readText()

            var solution: List<Int>?
            var isSolution: Boolean

            val timeKoSat = (measureTimeMillis {
                solution = solveCnf(readCnfRequests(input).first())
            }.toDouble() / 1000).toString()

            val timeMiniSat = measureTimeMillis { isSolution = processMiniSatSolver(input) }.toDouble() / 1000

            val checkRes = if (checkKoSatSolution(solution, input, isSolution)) "OK" else "WA"

            println(
                buildPadding(
                    listOf(
                        it.dropLast(format.length), // test name
                        timeKoSat,
                        timeMiniSat.toString(),
                        checkRes,
                        if (isSolution) "SAT" else "UNSAT"
                    )
                )
            )
        }
    }

    @Test
    fun testAssumptions() {
        val path = "src/jvmTest/resources/small/"

        val filenames = getAllFilenamesByPath(path)
        // println(filenames)
        // println(buildPadding(headerNames))

        // trigger the shared library loading
        MiniSatSolver().close()


        filenames.forEach { filename ->
            val filepath = path + filename

            val fileInput = File(filepath).readText()
            val lines = fileInput.split('\n').filter { line ->
                line.isNotEmpty() && line[0] != 'c'
            }
            val fileFirstLine = lines[0].split(' ')
            val variables = fileFirstLine[2]
            val clauses = fileFirstLine[3].dropLast(1)

            var solution: List<Int>?
            var isSolution: Boolean

            repeat(5) { ind ->
                val assumptions = List(ind) { Random.nextInt(1, variables.toInt() + 2) }.map {
                    if (Random.nextBoolean()) it else -it
                }

                val input = fileFirstLine.dropLast(2).joinToString(" ") + " " +
                        (variables.toInt() + 1).toString() + " " +
                        (clauses.toInt() + assumptions.size).toString() + "\n" +
                        lines.drop(1).joinToString(separator = "\n") +
                        assumptions.joinToString(separator = " 0\n", postfix = " 0")

                println(assumptions)

                val timeKoSat = (measureTimeMillis {
                    solution = solveWithAssumptions(readCnfRequests(input).first())
                }.toDouble() / 1000).toString()

                val timeMiniSat = measureTimeMillis { isSolution = processMiniSatSolver(input) }.toDouble() / 1000

                val checkRes = if (checkKoSatSolution(solution, input, isSolution)) "OK" else "WA"

                println(
                    buildPadding(
                        listOf(
                            filename.dropLast(format.length), // test name
                            timeKoSat,
                            timeMiniSat.toString(),
                            checkRes,
                            if (isSolution) "SAT" else "UNSAT"
                        )
                    )
                )
            }
        }
    }

    @Test
    fun test() {
        runTests("src/jvmTest/resources/")
    }

}
