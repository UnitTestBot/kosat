package org.kosat

import okio.BufferedSink

abstract class AbstractDratBuilder {
    abstract fun addClause(clause: Clause)
    abstract fun deleteClause(clause: Clause)
    abstract fun flush()

    open fun addComment(comment: String) {}

    fun addEmptyClauseAndFlush() {
        addClause(Clause(mutableListOf()))
        flush()
    }

    fun addLiteral(lit: Lit) {
        addClause(Clause(mutableListOf(lit)))
    }
}

class NoOpDratBuilder : AbstractDratBuilder() {
    override fun addClause(clause: Clause) {}
    override fun deleteClause(clause: Clause) {}
    override fun flush() {}
}

class DratBuilder(private val output: BufferedSink) : AbstractDratBuilder() {
    override fun addClause(clause: Clause) {
        output.writeUtf8(clause.toDimacs().joinToString(" ", postfix = " 0\n"))
    }

    override fun deleteClause(clause: Clause) {
        output.writeUtf8(clause.toDimacs().joinToString(" ", prefix = "d ", postfix = " 0\n"))
    }

    override fun flush() {
        output.flush()
    }

    override fun addComment(comment: String) {
        output.writeUtf8("c ").writeUtf8(comment).writeUtf8("\n")
    }
}

class BinaryDratBuilder(private val output: BufferedSink) : AbstractDratBuilder() {
    private fun mapLiteral(lit: Int): Int {
        return if (lit > 0) lit * 2 else -lit * 2 + 1
    }

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
