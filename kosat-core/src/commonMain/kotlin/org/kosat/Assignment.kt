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
    val value: DenseLBoolVec = DenseLBoolVec(0)
    val varData: MutableList<VarState> = mutableListOf()
    val trail: DenseLitVec = DenseLitVec.empty
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
        return value[v.index]
    }

    /**
     * @return the value of the literal, assuming that it is not substituted.
     */
    fun value(lit: Lit): LBool {
        // require(isActive(lit))
        return value[lit.variable.index] xor lit.isNeg
    }

    /** @return whether the variable is [VarState.frozen] */
    fun isFrozen(v: Var): Boolean {
        return varData[v].frozen
    }

    /** @return whether the variable corresponding to the [lit] is [VarState.frozen] */
    fun isFrozen(lit: Lit): Boolean = isFrozen(lit.variable)

    /** Marks active variable corresponding to [lit] as [VarState.frozen] */
    fun freeze(lit: Lit) {
        require(varData[lit.variable].active)
        varData[lit.variable].frozen = true
    }

    /** Marks variable corresponding to [lit] as not [VarState.frozen] */
    fun unfreeze(lit: Lit) {
        varData[lit.variable].frozen = false
    }

    /** @return whether the variable is [VarState.active] */
    fun isActive(v: Var): Boolean {
        return varData[v].active
    }

    /** @return whether the variable corresponding to the [lit] is [VarState.active] */
    fun isActive(lit: Lit): Boolean = isActive(lit.variable)

    /** Marks variable as [VarState.active] */
    fun markActive(v: Var) {
        if (varData[v].active) return
        numberOfInactiveVariables--
        varData[v].active = true
    }

    /** Marks variable corresponding to the [lit] as [VarState.active] */
    fun markActive(lit: Lit) = markActive(lit.variable)

    /** Marks variable as not [VarState.active] */
    fun markInactive(v: Var) {
        require(!varData[v].frozen)
        if (!varData[v].active) return
        numberOfInactiveVariables++
        varData[v].active = false
    }

    /** Marks variable corresponding to the [lit] as not [VarState.active] */
    fun markInactive(lit: Lit) = markInactive(lit.variable)

    fun unassign(v: Var) {
        value[v.index] = LBool.UNDEF
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
        value.add()
        varData.add(VarState(null, -1))
        if (numberOfActiveVariables > trail.capacity) {
            trail.grow()
        }
    }

    fun newDecisionLevel() {
        decisionLevel++
    }

    fun uncheckedEnqueue(lit: Lit, reason: Clause?) {
        require(value(lit) == LBool.UNDEF)
        require(isActive(lit))

        if (decisionLevel == 0) solver.dratBuilder.addClause(Clause(DenseLitVec.of(lit)))

        value[lit.variable.index] = LBool.from(lit.isPos)
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

class DenseLBoolVec(size: Int) {
    private var raw = LongArray(64)
    var size: Int = size
        private set
    private val capacity get() = raw.size shl 5

    fun add() {
        if (size == capacity) {
            raw = raw.copyOf(raw.size * 2)
        }
        size++
    }

    operator fun get(index: Int): LBool {
        val word = raw[index shr 5]
        val offset = (index and 0b11111) shl 1
        val mask = 0b11L shl offset
        val result = when ((word and mask) ushr offset) {
            0b01L -> LBool.FALSE
            0b10L -> LBool.TRUE
            else -> LBool.UNDEF
        }
        return result
    }

    operator fun set(index: Int, value: LBool) {
        val encoded = when (value) {
            LBool.UNDEF -> 0b00L
            LBool.FALSE -> 0b01L
            LBool.TRUE -> 0b10L
        }

        val wordIndex = index shr 5
        val word = raw[wordIndex]
        val offset = (index and 0b11111) shl 1
        val mask = 0b11L shl offset

        raw[wordIndex] = (word and mask.inv()) or (encoded shl offset)
    }
}
