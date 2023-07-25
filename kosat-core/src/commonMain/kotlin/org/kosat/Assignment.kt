package org.kosat

data class VarState(
    /** A clause which lead to assigning a value to this variable during [CDCL.propagate] */
    var reason: Clause?,

    /** Level of decision on which the variable was assigned a value */
    var level: Int,

    /** Index of the variable in the trail */
    var trailIndex: Int = -1,

    /** A literal which is used to substitute this variable after Equivalent Literal Substitution */
    var substitution: Lit? = null,
)

class Assignment(private val solver: CDCL) {
    val value: MutableList<LBool> = mutableListOf()
    val varData: MutableList<VarState> = mutableListOf()
    val trail: MutableList<Lit> = mutableListOf()
    val numberOfVariables get() = value.size
    private var numberOfSubstitutions = 0

    /** The number of not substituted variables */
    val numberOfActiveVariables get() = numberOfVariables - numberOfSubstitutions

    var decisionLevel: Int = 0
    var qhead: Int = 0
    var qheadBinaryOnly: Int = 0

    /**
     * @return the value of the variable, assuming that it is not substituted.
     */
    fun value(v: Var): LBool {
        require(varData[v].substitution == null)
        return value[v]
    }

    /**
     * @return the value of the literal, assuming that it is not substituted.
     */
    fun value(lit: Lit): LBool {
        // TODO: It would be nice to have this assertion,
        //       however, there are too many moving pieces at the moment
        //       and enabling this will require a lot of additional
        //       isSubstituted checks.
        // require(varData[lit.variable].substitution == null)
        return value[lit.variable] xor lit.isNeg
    }

    /**
     * @return the value of the variable, considering its substitution if
     *         needed.
     */
    fun valueAfterSubstitution(v: Var): LBool {
        val substitution = varData[v].substitution
        return if (substitution != null) {
            value(substitution)
        } else {
            value(v)
        }
    }

    /**
     * @return the value of the literal, considering its substitution if
     *         needed.
     */
    fun valueAfterSubstitution(lit: Lit): LBool {
        return valueAfterSubstitution(lit.variable) xor lit.isNeg
    }

    /**
     * Marks the literal as substituted by the given literal.
     */
    fun markSubstituted(lit: Lit, substitution: Lit) {
        if (varData[lit.variable].substitution == null) numberOfSubstitutions++
        varData[lit.variable].substitution = substitution xor lit.isNeg
    }

    /**
     * If a literal is substituted by another literal, which is then substituted
     * by another literal, then the first literal is substituted by the last
     * literal. This function removes such nested substitutions, up to the
     * depth of 2.
     */
    fun fixNestedSubstitutions() {
        for (varIndex in 0 until numberOfVariables) {
            val variable = Var(varIndex)
            val substitution = varData[variable].substitution ?: continue
            val secondSubstitution = varData[substitution.variable].substitution ?: continue
            check(varData[secondSubstitution.variable].substitution == null)
            varData[variable].substitution = secondSubstitution xor substitution.isNeg
        }
    }

    /**
     * @return the substitution of the literal, if it is substituted, or the
     *         literal itself otherwise.
     */
    fun getSubstitutionOf(lit: Lit): Lit {
        val substitution = varData[lit.variable].substitution
        return if (substitution != null) {
            substitution xor lit.isNeg
        } else {
            lit
        }
    }

    /**
     * @return true if the variable is substituted.
     */
    fun isSubstituted(v: Var): Boolean {
        return varData[v].substitution != null
    }

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
        require(varData[lit.variable].substitution == null)

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
