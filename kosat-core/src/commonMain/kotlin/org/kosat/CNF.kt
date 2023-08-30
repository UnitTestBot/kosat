package org.kosat

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource

class CNF(
    val clauses: List<Clause>,
    val numVars: Int = determineMaxVariable(clauses),
) {
    fun writeDimacs(sink: BufferedSink, includeHeader: Boolean = true) {
        if (includeHeader) {
            sink.writeUtf8("p cnf $numVars ${clauses.size}\n")
        }
        for (clause in clauses) {
            for (lit in clause.lits) {
                sink.writeUtf8(lit.toDimacs().toString()).writeUtf8(" ")
            }
            sink.writeUtf8("0\n")
        }
    }

    fun toDimacsString(includeHeader: Boolean = true): String {
        val buffer = Buffer()
        writeDimacs(buffer, includeHeader)
        return buffer.readUtf8()
    }

    companion object {
        fun from(source: BufferedSource): CNF {
            val clauses = parseDimacs(source).toList()
            return CNF(clauses)
        }

        fun fromString(string: String): CNF {
            return from(Buffer().writeUtf8(string))
        }
    }
}

fun determineMaxVariable(clauses: List<Clause>): Int {
    return clauses.maxOfOrNull { clause -> clause.lits.maxOfOrNull { lit -> lit.variable.index + 1 } ?: 0 } ?: 0
}

private val RE_SPACE: Regex = """\s+""".toRegex()

fun parseDimacs(source: BufferedSource): Sequence<Clause> = sequence {
    while (true) {
        val line = source.readUtf8Line()?.trim() ?: break

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
        } else {
            // Clause
            val tokens = line.split(RE_SPACE)
            check(tokens.last() == "0") {
                "Last token in clause must be '0': \"$line\""
            }
            val lits = tokens.dropLast(1).map { it.toInt() }
            val clause = Clause.fromDimacs(lits)
            yield(clause)
        }
    }
}
