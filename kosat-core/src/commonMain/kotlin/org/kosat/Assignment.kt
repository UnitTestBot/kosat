package org.kosat

data class VarState(
    /** A clause which lead to assigning a value to this variable during [CDCL.propagate] */
    var reason: Clause?,

    /** Level of decision on which the variable was assigned a value */
    var level: Int,

    /** Index of the variable in the trail */
    var trailIndex: Int = -1,

    /** Whether the variable is active, i.e. used in the search and not eliminated in some way */
    var active: Boolean = true,

    /** Whether the variable is frozen (cannot be eliminated) */
    var frozen: Boolean = false,
)

class Assignment(private val solver: CDCL) {
    val value: MutableList<LBool> = mutableListOf()
    val varData: MutableList<VarState> = mutableListOf()
    val trail: MutableList<Lit> = mutableListOf()
    val numberOfVariables get() = value.size
    private var numberOfInactiveVariables = 0

    /** The number of active variables (i.e. not eliminated, substituted, etc.) */
    val numberOfActiveVariables get() = numberOfVariables - numberOfInactiveVariables

    var decisionLevel: Int = 0
    var qhead: Int = 0
    var qheadBinaryOnly: Int = 0

    /**
     * @return the value of the variable, assuming that it is not substituted.
     */
    fun value(v: Var): LBool {
        require(isActive(v))
        return value[v]
    }

    /**
     * @return the value of the literal, assuming that it is not substituted.
     */
    fun value(lit: Lit): LBool {
        require(isActive(lit))
        return value[lit.variable] xor lit.isNeg
    }

    fun isActiveAndTrue(lit: Lit): Boolean {
        return varData[lit.variable].active && value(lit) == LBool.TRUE
    }

    fun isActiveAndFalse(lit: Lit): Boolean {
        return varData[lit.variable].active && value(lit) == LBool.FALSE
    }

    fun isFrozen(lit: Lit): Boolean {
        return varData[lit.variable].frozen
    }

    fun freeze(lit: Lit) {
        require(varData[lit.variable].active)
        varData[lit.variable].frozen = true
    }

    fun unfreeze(lit: Lit) {
        varData[lit.variable].frozen = false
    }

    fun isActive(v: Var): Boolean {
        return varData[v].active
    }

    fun isActive(lit: Lit): Boolean = isActive(lit.variable)

    fun markActive(v: Var) {
        if (varData[v].active) return
        numberOfInactiveVariables--
        varData[v].active = true
    }

    fun markActive(lit: Lit) = markActive(lit.variable)

    fun markInactive(v: Var) {
        require(!varData[v].frozen)
        if (!varData[v].active) return
        numberOfInactiveVariables++
        varData[v].active = false
    }

    fun markInactive(lit: Lit) = markInactive(lit.variable)

    fun unassign(v: Var) {
        value[v] = LBool.UNDEF
        varData[v].reason = null
        varData[v].level = -1
        varData[v].trailIndex = -1
    }

    fun reason(v: Var): Clause? {
        return varData[v].reason
    }

    fun level(v: Var): Int {
        return varData[v].level
    }

    fun level(v: Lit): Int {
        return level(v.variable)
    }

    fun trailIndex(v: Var): Int {
        return varData[v].trailIndex
    }

    fun fixed(v: Var): LBool {
        return if (level(v) > 0) {
            LBool.UNDEF
        } else {
            value(v)
        }
    }

    fun fixed(lit: Lit): LBool {
        return if (level(lit.variable) > 0) {
            LBool.UNDEF
        } else {
            value(lit)
        }
    }

    fun addVariable() {
        check(decisionLevel == 0)
        value.add(LBool.UNDEF)
        varData.add(VarState(null, -1))
    }

    fun newDecisionLevel() {
        decisionLevel++
    }

    fun uncheckedEnqueue(lit: Lit, reason: Clause?) {
        require(value(lit) == LBool.UNDEF)
        require(isActive(lit))

        if (decisionLevel == 0) solver.dratBuilder.addClause(Clause(mutableListOf(lit)))

        value[lit.variable] = LBool.from(lit.isPos)
        varData[lit.variable].reason = reason
        varData[lit.variable].level = decisionLevel
        varData[lit.variable].trailIndex = trail.size
        trail.add(lit)
    }

    fun enqueue(lit: Lit, reason: Clause?): Boolean {
        return when (value(lit)) {
            LBool.UNDEF -> {
                uncheckedEnqueue(lit, reason)
                true
            }

            LBool.TRUE -> {
                // Existing consistent assignment of `lit`
                true
            }

            LBool.FALSE -> {
                // Conflict
                false
            }
        }
    }

    fun dequeue(): Lit? {
        return if (qhead < trail.size) {
            trail[qhead++]
        } else {
            null
        }
    }
}
