package org.kosat

class Assignment(private val solver: CDCL) {
    val value: LBoolVec = LBoolVec()
    private val reasons: MutableList<Clause?> = mutableListOf()
    private var levels: IntArray = IntArray(16) { -1 }
    private var trailIndices: IntArray = IntArray(16) { -1 }
    private var active: BooleanArray = BooleanArray(16) { true }
    private var frozen: BooleanArray = BooleanArray(16) { false }
    val trail: LitVec = LitVec()
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
        // require(isActive(v))
        return value[v]
    }

    /**
     * @return the value of the literal, assuming that it is not substituted.
     */
    fun value(lit: Lit): LBool {
        // require(isActive(lit))
        return value[lit.variable] xor lit.isNeg
    }

    /** @return whether the variable is [frozen] */
    fun isFrozen(v: Var): Boolean {
        return frozen[v]
    }

    /** @return whether the variable corresponding to the [lit] is [frozen] */
    fun isFrozen(lit: Lit): Boolean = isFrozen(lit.variable)

    /** Marks active variable corresponding to [lit] as [frozen] */
    fun freeze(lit: Lit) {
        require(active[lit.variable])
        frozen[lit.variable] = true
    }

    /** Marks variable corresponding to [lit] as not [frozen] */
    fun unfreeze(lit: Lit) {
        frozen[lit.variable] = false
    }

    /** @return whether the variable is [active] */
    fun isActive(v: Var): Boolean {
        return active[v]
    }

    /** @return whether the variable corresponding to the [lit] is [active] */
    fun isActive(lit: Lit): Boolean = isActive(lit.variable)

    /** Marks variable as [active] */
    fun markActive(v: Var) {
        if (active[v]) return
        numberOfInactiveVariables--
        if (numberOfInactiveVariables < 0) {
            numberOfInactiveVariables = 0
        }
        active[v] = true
    }

    /** Marks variable corresponding to the [lit] as [active] */
    fun markActive(lit: Lit) = markActive(lit.variable)

    /** Marks variable as not [active] */
    fun markInactive(v: Var) {
        require(!frozen[v])
        if (!active[v]) return
        numberOfInactiveVariables++
        active[v] = false
    }

    /** Marks variable corresponding to the [lit] as not [active] */
    fun markInactive(lit: Lit) = markInactive(lit.variable)

    fun unassign(v: Var) {
        value[v] = LBool.UNDEF
        reasons[v] = null
        levels[v] = -1
        trailIndices[v] = -1
    }

    fun reason(v: Var): Clause? {
        return reasons[v.index]
    }

    fun reason(lit: Lit): Clause? {
        return reason(lit.variable)
    }

    fun level(v: Var): Int {
        return levels[v.index]
    }

    fun level(v: Lit): Int {
        return level(v.variable)
    }

    fun trailIndex(v: Var): Int {
        return trailIndices[v]
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
        reasons.add(null)
        if (value.size > levels.size) {
            val oldSize = levels.size
            val newSize = oldSize * 2
            levels = levels.copyOf(newSize)
            trailIndices = trailIndices.copyOf(newSize)
            active = active.copyOf(newSize)
            frozen = frozen.copyOf(newSize)
            for (i in oldSize until newSize) {
                levels[i] = -1
                trailIndices[i] = -1
                active[i] = true
                frozen[i] = false
            }
        }
    }

    fun newDecisionLevel() {
        decisionLevel++
    }

    fun uncheckedEnqueue(lit: Lit, reason: Clause?) {
        // require(value(lit) == LBool.UNDEF)
        // require(isActive(lit))

        if (decisionLevel == 0) {
            solver.dratBuilder.addClause(Clause(LitVec.of(lit)))
            solver.stats.unitsFound++
        }

        val v = lit.variable
        value[v] = LBool.from(lit.isPos)
        reasons[v] = reason
        levels[v] = decisionLevel
        trailIndices[v] = trail.size
        trail.add(lit)
    }

    fun enqueue(lit: Lit, reason: Clause?): Boolean {
        return when (value(lit)) {
            LBool.TRUE -> {
                // Existing consistent assignment of `lit`
                true
            }

            LBool.FALSE -> {
                // Conflict
                false
            }

            else -> {
                uncheckedEnqueue(lit, reason)
                true
            }
        }
    }

    fun dequeue(): Lit {
        return trail[qhead++]
    }
}
