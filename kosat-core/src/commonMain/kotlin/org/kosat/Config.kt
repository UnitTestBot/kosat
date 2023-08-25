package org.kosat

data class Config(
    var clauseDbStrategy: ReduceStrategy = ReduceStrategy.LBD,
    var clauseDbMaxSizeInitial: Int = 6000,
    var clauseDbMaxSizeIncrement: Int = 500,
    var clauseDbActivityDecay: Double = 0.999,

    var vsidsActivityIncrement: Double = 1.0,
    var vsidsActivityMultiplier: Double = 1.1,

    var restarterLubyConstant: Int = 50,

    var els: Boolean = true,
    var elsRoundsBeforeFlp: Int = 5,
    var elsRoundsAfterFlp: Int = 5,

    var flp: Boolean = true,
    var flpMaxProbes: Int = 1000,
    val flpHyperBinaryResolution: Boolean = true,

    var bve: Boolean = true,
    var bveResolventSizeLimit: Int = 16,
    var bveVarScoreSumWeight: Double = -1.0,
    var bveVarScoreProdWeight: Double = 1.0,
    var bveMaxVarScore: Double = 1.0,
    var bveMaxNewResolventsPerElimination: Int = 16,
    var bveMaxVarsToEliminate: Int = Int.MAX_VALUE,
)