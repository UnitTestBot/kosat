package org.kosat

import okio.BufferedSink
import kotlin.time.TimeSource

class Reporter(val sink: BufferedSink) {
    private var startTime = TimeSource.Monotonic.markNow()

    fun restartTimer() {
        startTime = TimeSource.Monotonic.markNow()
    }

    fun report(event: String, stats: Stats) {
        val timeElapsed = TimeSource.Monotonic.markNow() - startTime

        sink.writeUtf8("c ${event.padStart(42)} | ")
        sink.writeUtf8("conflicts=${stats.conflicts} ")
        sink.writeUtf8("decisions=${stats.decisions} ")
        sink.writeUtf8("propagations=${stats.propagations} ")
        sink.writeUtf8("time=${timeElapsed.inWholeMilliseconds}ms")
        sink.writeUtf8("\n")

        sink.flush()
    }
}
