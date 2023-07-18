package org.kosat.cnf

import okio.BufferedSource
import kotlin.math.abs

class CNF(
    val clauses: List<List<Int>>,
    val numVars: Int = determineNumberOfVariables(clauses),
) {
    companion object {
        private val RE_SPACE: Regex = """\s""".toRegex()

        fun from(source: BufferedSource): CNF {
            val clauses: MutableList<List<Int>> = mutableListOf()
            var numVars = 0

            while (true) {
                val line = source.readUtf8Line() ?: break

                if (line.startsWith('c')) {
                    // Skip comment
                } else if (line.isBlank()) {
                    // Skip empty line
                } else if (line.startsWith('p')) {
                    // Header
                    val tokens = line.split(RE_SPACE)
                    check(tokens[0] == "p") {
                        "First header token must be 'p': \"$line\""
                    }
                    check(tokens[1] == "cnf") {
                        "Second header token must be 'cnf': \"$line\""
                    }
                    check(tokens.size == 4) {
                        "Header should have exactly 3 tokens: \"$line\""
                    }
                    numVars = tokens[2].toInt()
                } else {
                    // Clause
                    val tokens = line.split(RE_SPACE)
                    check(tokens.last() == "0")
                    val lits = tokens.map { it.toInt() }
                    val clause = lits.dropLast(1)
                    clauses.add(clause)
                }
            }

            val determinedNumVars = determineNumberOfVariables(clauses)
            if (numVars == 0) {
                numVars = determinedNumVars
            }
            if (determinedNumVars > numVars) {
                error("CNF contains more variables ($determinedNumVars) than specified in the header ($numVars)")
            }

            return CNF(clauses, numVars)
        }
    }
}

fun determineNumberOfVariables(clauses: List<List<Int>>): Int {
    return clauses.maxOfOrNull { clause -> clause.maxOfOrNull{ lit -> abs(lit) } ?: 0 } ?: 0
}
