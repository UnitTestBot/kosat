package org.kosat

import okio.BufferedSink
import kotlin.time.TimeSource

/**
 * Reports statistics and information about stages of solving process of [CDCL]
 * to the given [sink]. It also tracks time elapsed since the last call to
 * [restartTimer], which is expected to be called at the start of [CDCL.solve].
 */
class Reporter(val sink: BufferedSink) {
    private var startTime = TimeSource.Monotonic.markNow()

    /**
     * Restarts the internal timer.
     */
    fun restartTimer() {
        startTime = TimeSource.Monotonic.markNow()
    }

    /**
     * Reports the given [event] to the [sink], along with printing the most
     * important statistics, using the provided [stats].
     */
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
