package org.kosat.cnf

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import kotlin.math.abs

class CNF(
    val clauses: List<List<Int>>,
    val numVars: Int = determineNumberOfVariables(clauses),
) {
    fun toDimacsString(includeHeader: Boolean): String {
        val buffer = Buffer()
        writeDimacs(buffer, includeHeader)
        return buffer.readUtf8()
    }

    fun writeDimacs(sink: BufferedSink, includeHeader: Boolean) {
        if (includeHeader) {
            sink.writeUtf8("p cnf $numVars ${clauses.size}\n")
        }
        for (clause in clauses) {
            for (lit in clause) {
                sink.writeUtf8(lit.toString()).writeUtf8(" ")
            }
            sink.writeUtf8("0\n")
        }
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
