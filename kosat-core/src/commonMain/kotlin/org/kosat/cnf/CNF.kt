package org.kosat.cnf

import okio.Buffer
import okio.BufferedSource
import kotlin.math.abs

data class CNF(
    val clauses: List<List<Int>>,
    val numVars: Int = determineNumberOfVariables(clauses),
) {
    fun toString(includeHeader: Boolean): String {
        val builder = StringBuilder()
        if (includeHeader) {
            builder.appendLine("p cnf $numVars ${clauses.size}")
        }
        for (clause in clauses) {
            for (lit in clause) {
                builder.append(lit)
                builder.append(' ')
            }
            builder.append('0')
            builder.appendLine()
        }
        return builder.trim().toString()
    }

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
                        "Header should have exactly 4 tokens: \"$line\""
                    }
                    numVars = tokens[2].toInt()
                } else {
                    // Clause
                    val tokens = line.split(RE_SPACE)
                    check(tokens.last() == "0") {
                        "Last token of clause must be '0': \"$line\""
                    }
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

        fun fromString(string: String): CNF {
            val buffer = Buffer()
            buffer.writeUtf8(string)
            val result = from(buffer)
            buffer.close()
            return result
        }
    }
}

fun determineNumberOfVariables(clauses: List<List<Int>>): Int {
    return clauses.maxOfOrNull { clause -> clause.maxOfOrNull { lit -> abs(lit) } ?: 0 } ?: 0
}
