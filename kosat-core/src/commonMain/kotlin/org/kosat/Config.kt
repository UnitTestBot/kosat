package org.kosat

/**
 * Configuration of the solver.
 */
data class Config(
    /**
     * The strategy to use for reducing the clause database.
     *
     * [ReduceStrategy.LBD] will use the LBD (Literal Block Distance), and will
     * remove half of the learnt clauses with the highest LBD.
     *
     * [ReduceStrategy.ACTIVITY] will use the activity of the clauses, and will
     * remove half of the learnt clauses with the highest activity.
     */
    var clauseDbStrategy: ReduceStrategy = ReduceStrategy.ACTIVITY,
    /**
     * The initial absolute maximum count of learnts in the database. The chosen
     * initial count will be chosen between this value and total number of
     * initial clauses times [clauseDbMaxSizeInitialRelative]. Once this limit
     * is reached, the database will be reduced, and the maximum count will be
     * multiplied by [clauseDbMaxSizeIncrement].
     */
    var clauseDbMaxSizeInitial: Int = 6000,
    /**
     * The initial relative maximum count of learnts in the database. The chosen
     * initial count will be chosen between [clauseDbMaxSizeInitial] and total
     * number of initial clauses times this value. Once this limit is reached,
     * the database will be reduced, and the maximum count will be multiplied
     * by [clauseDbMaxSizeIncrement].
     */
    var clauseDbMaxSizeInitialRelative: Double = 0.333,
    /**
     * The increment of the maximum count of learnts in the database. Every time
     * the database is reduced, the maximum count is multiplied by this value.
     */
    var clauseDbMaxSizeIncrement: Double = 1.1,
    /**
     * When using [ReduceStrategy.ACTIVITY], this is how fast the clause
     * activity will decay. The higher the value, the slower the decay. Must be
     * between 0 and 1.
     */
    var clauseDbActivityDecay: Double = 0.999,

    /**
     * The rate of variable activity decay in [VSIDS]. The higher the value, the
     * slower the decay. Must be between 0 and 1.
     */
    var vsidsActivityDecay: Double = 0.95,

    /**
     * Whether to run Luby restarts.
     */
    var restarts: Boolean = true,
    /**
     * The starting constant to use for the Luby restart sequence.
     */
    var restarterLubyConstant: Int = 100,
    /**
     * The base to use for the Luby restart sequence. For example, if set to 2,
     * the sequence will be
     * ```
     * 1, 1, 2, 1, 1, 2, 4, 1, 1, 2, ...
     * ```
     * If set to 1.5, the sequence will be
     * ```
     * 1, 1, 1.5, 1, 1, 1.5, 2.25, 1, 1, 1.5, ...
     * ```
     * Multiplied by [restarterLubyConstant], this will be the number of
     * conflicts between restarts.
     */
    var restarterLubyBase: Double = 2.0,

    /**
     * Whether to run Equivalent Literal Substitution.
     */
    var els: Boolean = true,
    /**
     * The number of rounds of ELS to run before the Failed Literal Probing.
     */
    var elsRoundsBeforeFlp: Int = 3,
    /**
     * The number of rounds of ELS to run after the Failed Literal Probing.
     */
    var elsRoundsAfterFlp: Int = 3,

    /**
     * Whether to run Failed Literal Probing.
     */
    var flp: Boolean = true,
    /**
     * The maximum number of probes to try in FLP
     */
    var flpMaxProbes: Int = 1000,
    /**
     * Whether to perform on-the-fly hyper-binary resolution in FLP.
     */
    val flpHyperBinaryResolution: Boolean = true,

    /**
     * Whether to run Bounded Variable Elimination.
     */
    var bve: Boolean = true,
    /**
     * The maximum allowed size of the resolvent in BVE.
     */
    var bveResolventSizeLimit: Int = 32,
    /**
     * The order of the variable elimination is determined by the score of the
     * variable. The score is calculated as the weighted sum of the number of
     * clauses the variable appears in and the product of the number of clauses
     * the variable appears in positive and negative phase.
     *
     * This is the weight of the sum part of the score.
     */
    var bveVarScoreSumWeight: Double = -1.0,
    /**
     * This is the weight of the product part of the score.
     */
    var bveVarScoreProdWeight: Double = 1.0,
    /**
     * The maximum allowed score of a variable. If a variable has a higher score
     * than this, it will not be eliminated.
     */
    var bveMaxVarScore: Double = 400.0,
    /**
     * The maximum number of new clauses after a variable elimination.
     * If there was `P` clauses containing the eliminated variable, and
     * elimination generated `N` non-tautological resolvents, then the elimination
     * will be aborted if `N - P` is greater than this value.
     */
    var bveMaxNewClausesPerElimination: Int = 16,
    /**
     * The maximum number of variables to eliminate in a single run of BVE.
     */
    var bveMaxVarsToEliminate: Int = Int.MAX_VALUE,

    var timeLimit: Int? = null,
)
