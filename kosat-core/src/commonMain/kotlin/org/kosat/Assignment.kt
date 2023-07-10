package org.kosat

data class VarState(
    /** A clause which lead to assigning a value to this variable during [CDCL.propagate] */
    var reason: Clause?,

    /** Level of decision on which the variable was assigned a value */
    var level: Int,
)

class Assignment {
    val value: MutableList<LBool> = mutableListOf()
    val varData: MutableList<VarState> = mutableListOf()
    val trail: MutableList<Lit> = mutableListOf()

    // val trailLim: MutableList<Int> = mutableListOf()
    var qhead: Int = 0
    var decisionLevel: Int = 0

    fun value(v: Var): LBool {
        return this.value[v]
    }

    fun value(lit: Lit): LBool {
        return this.value[lit.variable] xor lit.isNeg
    }

    // fun assign(v: Var, value: LBool) {
    //     this.value[v] = value
    // }

    fun unassign(v: Var) {
        this.value[v] = LBool.UNDEF
    }

    // fun varData(v: Var): VarState {
    //     return this.varData[v]
    // }

    fun reason(v: Var): Clause? {
        return this.varData[v].reason
    }

    fun level(v: Var): Int {
        return this.varData[v].level
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
        varData.add(VarState(null, 0))
    }

    fun newDecisionLevel() {
        decisionLevel++
    }

    fun uncheckedEnqueue(lit: Lit, reason: Clause?) {
        require(value(lit) == LBool.UNDEF)

        value[lit.variable] = LBool.from(lit.isPos)
        varData[lit.variable].reason = reason
        varData[lit.variable].level = decisionLevel
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
