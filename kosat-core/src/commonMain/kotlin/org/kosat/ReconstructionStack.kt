package org.kosat

/**
 * Reconstruction stack is data structure used to reconstruct the model after
 * the solver has found a satisfying assignment and to restore variables
 * required to perform an incremental addition of clauses or assumptions.
 *
 * It uses the idea of "witness" literals. Every time we eliminate a variable in
 * some way, we push its "definition" in clausal form to the stack. The
 * "definition" is in quotes, because it is not a definition in a strict sense.
 * Consider pure literal elimination. In it, we simply remove all clauses
 * containing a pure literal. That literal is not "defined", it is "constrained"
 * by the clauses. All we have to do, is to assign it a value which won't lead
 * to a conflict. However, most of the time, clauses strictly define a value of
 * a literal in terms of active variables (at the moment of pushing a
 * clause to a stack). For example, a "definition" of a variable `x` as a
 * conjunction of two other variables `y` and `z` can be written as
 * `(-y, -z, x), (-x, y), (-x, z)`. Literal `x` in this case is referred to as
 * a "witness" literal.
 *
 * There are multiple ways to store the stack. In the original paper, the stack
 * stored as a list of clauses, labelled by literals. Technically, it is enough
 * to just store the clauses, keeping the invariant that the first or last
 * literal in the clause is the witness. In CaDiCaL, the stack is stored as a
 * vector of integers, separated into "stack-frames". Each frame is either a
 * clause or a set of literals. This allows to label a clause with multiple
 * literals, which is useful for blocked clause elimination. Hopefully, the
 * structure is pretty flexible and allows to implement any other stack without
 * breaking the interface too much.
 *
 * We currently use the simplest approach: a list of pairs of clauses and
 * their witness literals. In the example above, the stack would be
 * `[((-y, -z, x), x), ((-x, y), -x), ((-x, z), -x)]`. If we then substituted
 * `y` with `-z`, the stack would become
 * `[((-y, -z, x), x), ((-x, y), -x), ((-x, z), -x), ((-y, -z), -y), ((y, z), -y)]`.
 *
 * Note that witness must be in the clause.
 */
class ReconstructionStack {
    data class ReconstructionStackEntry(
        val clause: Clause,
        val witness: Lit,
    )

    private val stack = mutableListOf<ReconstructionStackEntry>()

    /**
     * Pushes a clause with a given witness to the stack.
     */
    fun push(clause: Clause, witness: Lit) {
        check(!clause.learnt)
        require(witness in clause.lits)
        val copy = clause.copy()
        copy.deleted = false
        stack.add(ReconstructionStackEntry(copy, witness))
    }

    /**
     * Pushes a binary clause from a given literal and its witness to the stack.
     */
    fun pushBinary(lit: Lit, witness: Lit) {
        stack.add(ReconstructionStackEntry(Clause(mutableListOf(lit, witness)), witness))
    }

    /**
     * Pushes two binary clauses, indicating that [witness] is true if
     * and only if [substitution] is true.
     */
    fun pushSubstitution(substitution: Lit, witness: Lit) {
        pushBinary(substitution.neg, witness)
        pushBinary(substitution, witness.neg)
    }

    /**
     * Reconstructs the model from the current assignment, assuming it is a
     * satisfying assignment.
     */
    fun reconstruct(assignment: Assignment): List<Boolean> {
        println(assignment.value)
        println(stack)

        val model = MutableList(assignment.numberOfVariables) { varIndex ->
            val v = Var(varIndex)
            assignment.isActiveAndTrue(v.posLit)
        }

        // TODO: remove LBools, fix docs
        // To reconstruct the model, we need to go through the stack in reverse
        // order, and for each clause, check if it is already satisfied by the
        // model.
        // If not, it means that to satisfy the clause, we need to set the
        // witness literal to true.
        for (stackIndex in stack.lastIndex downTo 0) {
            val (clause, witness) = stack[stackIndex]
            val satisfied = clause.lits.any { model[it.variable] xor it.isNeg }
            if (!satisfied) {
                model[witness.variable] = witness.isPos
            }
        }

        return model
    }

    /**
     * Restores the solver state after adding new clauses or assumptions.
     *
     * During variable elimination, we might have eliminated a variable, which
     * is now required to be active again, due to the addition of new clauses
     * in the incremental solver, or due to given assumptions. In this case, we
     * need to restore that variable.
     *
     * @param solver the solver to restore
     * @param newClauses the clauses added to the solver from the last restore
     *        (not required on the first solve)
     * @param assumptions the assumptions added to the solver this solve.
     */
    fun restore(solver: CDCL, newClauses: List<Clause>, assumptions: List<Lit>) {
        require(solver.assignment.decisionLevel == 0)

        // The term "tainted" is used in the original paper and refers to the
        // variables which are required to be active again.
        val tainted = BooleanArray(solver.assignment.numberOfVariables)

        // We first mark all the variables in the new clauses and assumptions as
        // tainted.
        for (clause in newClauses) {
            for (lit in clause.lits) {
                tainted[lit.variable] = true
            }
        }

        for (lit in assumptions) {
            tainted[lit.variable] = true
        }

        // We then go through the stack and restore the variables.
        // Note that this may introduce new tainted variables.
        // This also removes clauses from the stack if the clause is satisfied
        // or the witness of that clause is tainted and needs to be restored.
        var newStackTop = 0
        for (stackIndex in 0 until stack.size) {
            val (clause, witness) = stack[stackIndex]
            stack[newStackTop++] = stack[stackIndex]

            // We simply keep the clause if there is no need to restore the
            // variable.
            if (!tainted[witness.variable]) continue

            newStackTop--

            // If the clause is satisfied at level 0, we can just remove it from
            // the stack (see (*)). Note that this is totally safe. The clauses
            // are guaranteed to contain a variable "definition" in some way, so
            // removing a satisfied clauses does not affect the correctness of
            // its constraints. There must be an unsatisfied clause left, which
            // will be used to restore the variable and force its value.
            val satisfied = clause.lits.any { solver.assignment.isActiveAndTrue(it) }

            // We also remove all false literals from the clause. Note that the
            // clause won't be empty, as it contains the witness literal, which
            // is unassigned.
            clause.lits.removeAll { solver.assignment.isActiveAndFalse(it) }

            // this is where we remove satisfied clauses
            if (satisfied || solver.assignment.isActiveAndFalse(witness)) continue // (*)
            // Otherwise, the clause needs to be restored.

            // We mark all the literals in the clause as tainted, as they are
            // required to be active again. Note, that while we are restoring
            // a lot of clauses, we are keeping all the information we derived
            // from preprocessing, just in a clausal form. In fact, it is
            // possible that on the next preprocessing step, we will derive
            // the same information again, just acknowledging the new clauses
            // and assumptions.
            for (lit in clause.lits) tainted[lit.variable] = true

            // Now the variable we derived is active again.
            solver.assignment.markActive(witness)

            // Note that the removal of falsified literals above is required,
            // otherwise we might end up attaching watches to the falsified
            // literals, of that clause.
            if (clause.size > 1) {
                solver.attachClause(clause)
            } else {
                check(solver.assignment.enqueue(clause[0], clause))
            }
        }

        // FIXME: ugly workaround
        for (varIndex in 0 until solver.assignment.numberOfVariables) {
            if (tainted[varIndex]) {
                solver.assignment.markActive(Var(varIndex))
            }
        }

        // We remove all the clauses which were removed from the stack.
        stack.retainFirst(newStackTop)
    }
}
