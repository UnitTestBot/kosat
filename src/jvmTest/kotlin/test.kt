import org.junit.jupiter.api.Test
import org.kosat.processCnfRequests
import org.kosat.readCnfRequests
import java.io.File
import kotlin.system.measureTimeMillis
import com.github.lipen.satlib.solver.MiniSatSolver
import com.github.lipen.satlib.solver.addClause
import kotlin.math.abs
import kotlin.math.sign

val packageName = "src/jvmTest/resources/"
val strLen = 11

fun fill(s: String): String {
    return s.padEnd(strLen, ' ')
}

fun processMiniSatSolver(input: String) {
    val data = readCnfRequests(input).first()

    with(MiniSatSolver()) {
        val lit = List(data.vars) { newLiteral() }
        for (clause in data.clauses) {
            addClause { clause.lit.map { it.sign * lit[abs(it) - 1] } }
        }
        solve()
    }
}

internal class DiamondTests {
    val testNumber = 3
    val groupName = "diamond"
    val name = packageName + groupName
    val format = ".cnf"

    @Test
    fun diamondTests() {
        println("${fill("Name:")} | ${fill("KoSat time:")} | MiniSat time:")
        MiniSatSolver()
        for (ind in 1..testNumber) {
            val filename = name + ind + format
            val testName = groupName + ind

            val input = File(filename).readText()

            val timeKoSat: Double = measureTimeMillis { processCnfRequests(readCnfRequests(input)) }.toDouble() / 1000
            val timeMiniSat: Double = measureTimeMillis { processMiniSatSolver(input) }.toDouble() / 1000

            println("${fill(testName)} | ${fill(timeKoSat.toString())} | ${fill(timeMiniSat.toString())}")
        }
    }
}