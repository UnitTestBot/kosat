package org.kosat

import okio.BufferedSink

/**
 * A builder for DRAT proofs.
 *
 * DRAT is a format for SAT proof traces.
 *
 * The description of the format can be found here:
 * https://github.com/marijnheule/drat-trim.
 *
 * This class provides a simple universal interface for building proofs.
 */
abstract class AbstractDratBuilder {
    /**
     * Add a clause to the proof. The clause must have a RAT property
     * with respect to the clauses that were added before and clauses
     * in the original CNF.
     */
    abstract fun addClause(clause: Clause)

    /**
     * Delete a clause from the proof. Only clauses that were added
     * before can be deleted, therefore clause from the original CNF
     * cannot be deleted.
     *
     * Clauses in the proof are stored in a multiset, so if a clause
     * was added multiple times, it can be deleted the same number of
     * times (or less).
     */
    abstract fun deleteClause(clause: Clause)

    /**
     * Flush the proof to the output. This method is expected to be
     * called after the proof is finished.
     */
    abstract fun flush()

    /**
     * Add a comment to the proof. This method is optional and only makes
     * sense to implement in the text-based version of the format.
     *
     * @see DratBuilder.addComment
     */
    open fun addComment(comment: String) {}

    /**
     * Finishes the proof with UNSAT, or, equivalently, adds an empty
     * clause and [flush]es the proof.
     */
    fun addEmptyClauseAndFlush() {
        addClause(Clause(mutableListOf()))
        flush()
    }

    /**
     * Adds a literal to the proof by adding a unit clause with that
     * literal.
     */
    fun addLiteral(lit: Lit) {
        addClause(Clause(mutableListOf(lit)))
    }
}

/**
 * A [DratBuilder] that does nothing. Useful if the proof is not needed.
 *
 * @see DratBuilder
 */
class NoOpDratBuilder : AbstractDratBuilder() {
    override fun addClause(clause: Clause) {}
    override fun deleteClause(clause: Clause) {}
    override fun flush() {}
}

/**
 * A [DratBuilder] that writes the text proof to a given [output][BufferedSink].
 */
class DratBuilder(private val output: BufferedSink) : AbstractDratBuilder() {
    override fun addClause(clause: Clause) {
        // Addition of the clause is represented by the clause in DIMACS format
        output.writeUtf8(clause.toDimacs().joinToString(" ", postfix = " 0\n"))
    }

    override fun deleteClause(clause: Clause) {
        // Deletion of the clause is represented by the clause in DIMACS format,
        // prefixed with an ascii letter "d"
        output.writeUtf8(clause.toDimacs().joinToString(" ", prefix = "d ", postfix = " 0\n"))
    }

    override fun flush() {
        output.flush()
    }

    override fun addComment(comment: String) {
        // Comments are lines, prefixed with an ascii letter "c"
        output.writeUtf8("c ").writeUtf8(comment).writeUtf8("\n")
    }
}

/**
 * A [DratBuilder] that writes the binary proof to a given [output][BufferedSink].
 *
 * The binary format is more compact than the text format, but it is not human-readable,
 * therefore, comments are ignored (and not included in the format in the first place).
 *
 * See [Format specification](https://github.com/marijnheule/drat-trim#binary-proof-format).
 */
class BinaryDratBuilder(private val output: BufferedSink) : AbstractDratBuilder() {
    /**
     * Every literal must be renumbered to a positive integer,
     * using the specified formula.
     */
    private fun mapLiteral(lit: Int): Int {
        return if (lit > 0) lit * 2 else -lit * 2 + 1
    }

    /**
     * Writes a variable-length integer to the output.
     */
    private fun writeVarInt(value: Int) {
        var v = value
        while (v >= 0x80) {
            output.writeByte((v and 0x7f) or 0x80)
            v = v ushr 7
        }
        output.writeByte(v)
    }

    private fun writeDIMACS(lits: List<Int>) {
        for (lit in lits) {
            writeVarInt(mapLiteral(lit))
        }
        output.writeByte(0)
    }

    override fun addClause(clause: Clause) {
        output.writeByte(0x61)
        writeDIMACS(clause.toDimacs())
    }

    override fun deleteClause(clause: Clause) {
        output.writeByte(0x64)
        writeDIMACS(clause.toDimacs())
    }

    override fun flush() {
        output.flush()
    }
}
