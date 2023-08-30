package org.kosat

import korlibs.time.measureTimeWithResult
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import org.junit.jupiter.api.Timeout
import org.kosat.cnf.parseDimacs
import kotlin.test.Test

@Timeout(1000_000_000)
internal class ManualTest {
    @Test
    fun testManual() {
        // val path = "src/jvmTest/resources/testCover/cover/cover0015.cnf".toPath()
        // val path = "../data/satcomp-2017/g2-mizh-md5-48-5.cnf".toPath()
        val path = "../data/satcomp-2017/g2-UCG-15-10p1.cnf".toPath()
        val solver = CDCL()
        println("Reading '$path'...")
        FileSystem.SYSTEM.source(path).buffer().use { source ->
            for (clause in parseDimacs(source)) {
                solver.newClause(clause)
            }
        }
        println("Solving...")
        val (result, timeSolve) = measureTimeWithResult {
            solver.solve()
        }
        println("result = $result")
        println("All done in $timeSolve")
    }
}
