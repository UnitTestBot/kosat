package org.kosat.cnf

import okio.FileSystem
import okio.Path
import okio.buffer

fun CNF.Companion.from(path: Path): CNF {
    return FileSystem.SYSTEM.source(path).use { source ->
        source.buffer().use { bufferedSource ->
            from(bufferedSource)
        }
    }
}
