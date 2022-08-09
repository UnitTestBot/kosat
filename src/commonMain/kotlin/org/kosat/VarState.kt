package org.kosat

enum class VarStatus {
    TRUE, FALSE, UNDEFINED;

    operator fun not(): VarStatus {
        return when (this) {
            TRUE -> FALSE
            FALSE -> TRUE
            UNDEFINED -> UNDEFINED
        }
    }
}

data class VarState(
    var status: VarStatus,
    var reason: Clause?,
    var level: Int
)
