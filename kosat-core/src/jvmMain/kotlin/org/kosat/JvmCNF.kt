package org.kosat

import okio.FileSystem
import okio.Path

fun CNF.Companion.from(path: Path): CNF = FileSystem.SYSTEM.read(path) { from(this) }
