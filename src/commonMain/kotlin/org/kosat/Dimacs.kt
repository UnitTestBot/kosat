package org.kosat

class DimacsLiteral(val value: Int) {
    fun toLiteral() = if (value > 0)
        (value - 1) shl 1
    else
        ((-value - 1) shl 1) or 1
}

class DimacsClause(val dimacsLiterals: List<DimacsLiteral>) {
    fun toClause() = Clause(dimacsLiterals.map { it.toLiteral() }.toMutableList())
}
