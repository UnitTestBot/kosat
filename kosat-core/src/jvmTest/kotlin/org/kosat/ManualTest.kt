package org.kosat

import korlibs.time.measureTimeWithResult
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test

internal class ManualTest {
    @Test
    fun testManual() {
        val path = "src/jvmTest/resources/testCover/cover/cover0023.cnf".toPath()
        // val path = "../data/satcomp-2017/g2-mizh-md5-48-5.cnf".toPath()
        // val path = "../data/satcomp-2017/mp1-qpr-bmp280-driver-5.cnf".toPath()
        println("Reading '$path'...")
        val cnf = FileSystem.SYSTEM.read(path) { CNF.from(this) }
        println("Creating solver from CNF...")
        val solver = CDCL(cnf)
        println("Solving...")
        val (result, timeSolve) = measureTimeWithResult {
            solver.solve()
        }
        println("result = $result")
        println("All done in ${timeSolve.seconds} s")
    }
}
