import org.junit.jupiter.api.Test
import org.kosat.readCnfRequests
import java.io.File
import kotlin.system.measureTimeMillis
import com.github.lipen.satlib.solver.MiniSatSolver
import org.kosat.Clause
import org.kosat.solveCnf
import kotlin.math.abs
import kotlin.math.sign

val packageName = "src/jvmTest/resources/"
val strLen = 13

fun fill(s: String): String {
    return s.padEnd(strLen, ' ')
}

fun processMiniSatSolver(input: String): Boolean {
    val data = readCnfRequests(input).first()

    with(MiniSatSolver()) {
        val lit = List(data.vars) { newLiteral() }
        for (clause in data.clauses) {
            addClause(clause.lit.map { it.sign * lit[abs(it) - 1] })
        }
        return solve()
    }
}

fun checkClause(ans: List<Int>?, clause: Clause) : Boolean {
    if (ans == null) return false
    return clause.lit.any { ans.contains(it) }
}

fun checkKoSatSolution(ans: List<Int>?, input: String, isSolution: Boolean): Boolean {
    if (ans == null) {
        return !isSolution
    }
    val cnfRequest = readCnfRequests(input).first()
    if (ans.size != cnfRequest.vars) {
        return false
    }
    var isFailedClause = false
    for (clause in cnfRequest.clauses) {
        if (!checkClause(ans, clause)) isFailedClause = true
    }
    return !isFailedClause
}

internal class DiamondTests {
    val testNumber = 18
    val groupName = "diamond"
    val name = packageName + groupName
    val format = ".cnf"

    @Test
    fun diamondTests() {
        println("${fill("Name:")} | ${fill("KoSat time:")} | ${fill("MiniSat time:")} | ${fill("Check result")} | ${fill("Solvable")}")
        MiniSatSolver()
        for (ind in 1..testNumber) {
            val filename = name + ind + format
            val testName = groupName + ind

            val input = File(filename).readText()

            var solution: List<Int>?
            var isSolution: Boolean

            val timeKoSat: Double = measureTimeMillis { solution = solveCnf(readCnfRequests(input).first()) }.toDouble() / 1000
            val timeMiniSat: Double = measureTimeMillis { isSolution = processMiniSatSolver(input) }.toDouble() / 1000

            val checkRes = if (checkKoSatSolution(solution, input, isSolution)) "OK" else "WA"



            println("${fill(testName)} | ${fill(timeKoSat.toString())} | ${fill(timeMiniSat.toString())} | ${fill(checkRes)} | ${if(isSolution) "SAT" else "UNSAT"}")
        }
    }
}