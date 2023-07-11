package org.kosat.heuristics

import org.kosat.CDCL
import org.kosat.Clause
import org.kosat.LBool
import org.kosat.Lit
import org.kosat.Var
import org.kosat.VarState
import org.kosat.get
import org.kosat.set
import org.kosat.swap

abstract class VariableSelector {
    protected var assumptions: List<Lit> = emptyList()

    fun initAssumptions(assumptions: List<Lit>) {
        this.assumptions = assumptions
    }

    abstract fun build(clauses: List<Clause>)
    abstract fun nextDecision(vars: List<VarState>, level: Int): Lit
    abstract fun addVariable()
    abstract fun update(lemma: Clause)
    abstract fun backTrack(variable: Var)
}

class VSIDS(private var numberOfVariables: Int = 0, private val solver: CDCL) : VariableSelector() {
    private val multiplier = 1.1
    private var numberOfConflicts = 0
    private var activityInc = 1.0
    private var activityLimit = 1e100

    // list of activity for variables
    private val activity = mutableListOf<Double>()

    class PriorityQueue(val activity: MutableList<Double>) {
        // stores max-heap built on variable activities (contains variables)
        val heap: MutableList<Int> = mutableListOf()

        // for each variable contains index with it position in heap
        val index: MutableList<Int> = mutableListOf()

        // maximum possible size of heap
        private var capacity = -1

        // current size
        var size = 0

        // compares variables by activity
        private fun cmp(u: Int, v: Int): Boolean {
            if (activity[u] > activity[v]) {
                return true
            }
            if (activity[u] < activity[v]) {
                return false
            }
            return u < v
        }

        private fun leftChild(u: Int): Int {
            return 2 * u + 1
        }

        private fun rightChild(u: Int): Int {
            return 2 * u + 2
        }

        private fun parent(u: Int): Int {
            return (u - 1) / 2
        }

        private fun swap(u: Int, v: Int) {
            heap.swap(u, v)
            index[heap[u]] = u
            index[heap[v]] = v
        }

        fun siftUp(u: Int) {
            val vertex = heap[u]
            var curInd = u
            var parent = parent(curInd)

            while (curInd > 0 && cmp(vertex, heap[parent])) {
                heap[curInd] = heap[parent]
                index[heap[curInd]] = curInd
                curInd = parent
                parent = parent(curInd)
            }
            heap[curInd] = vertex
            index[vertex] = curInd
        }

        // if some value of vertex decreased this function lift this vertex down to save heap structure
        private fun siftDown(u: Int) {
            val vertex = heap[u]
            var curInd = u

            var ls: Int
            var rs: Int
            var leftVertex: Int
            var rightVertex: Int

            while (leftChild(curInd) < size) {
                ls = leftChild(curInd)
                rs = rightChild(curInd)
                leftVertex = if (ls > size - 1) -1 else heap[ls]
                rightVertex = if (rs > size - 1) -1 else heap[rs]

                if (rs > size - 1) {
                    if (cmp(leftVertex, vertex)) {
                        heap[curInd] = leftVertex
                        index[leftVertex] = curInd
                        curInd = ls
                    } else {
                        break
                    }
                } else if (cmp(leftVertex, rightVertex)) {
                    if (cmp(leftVertex, vertex)) {
                        heap[curInd] = leftVertex
                        index[leftVertex] = curInd
                        curInd = ls
                    } else {
                        break
                    }
                } else if (cmp(rightVertex, vertex)) {
                    heap[curInd] = rightVertex
                    index[rightVertex] = curInd
                    curInd = rs
                } else {
                    break
                }
            }
            heap[curInd] = vertex
            index[vertex] = curInd
        }

        // returns element on top of heap
        fun top(): Int {
            require(size != 0)
            return heap[0]
        }

        // delete element on top of heap and returns it
        fun pop(): Int {
            require(size != 0)
            val max = top()
            swap(0, size - 1)
            index[heap[size - 1]] = -1
            size--
            if (heap.isNotEmpty()) {
                siftDown(0)
            }
            return max
        }

        fun insert(value: Int) {
            require(size != capacity)
            heap[size] = value
            index[value] = size
            size++
            siftUp(size - 1)
        }

        fun buildHeap(activity: MutableList<Double>) {
            for (ind in 0..activity.lastIndex) {
                heap.add(ind)
            }
            size = heap.size
            while (index.size < size) {
                index.add(0)
            }
            heap.forEachIndexed { ind, elem ->
                index[elem] = ind
            }
            for (ind in (heap.size / 2) downTo 0) {
                siftDown(ind)
            }
            capacity = size
        }
    }

    // priority queue of activity of undefined variables
    private var activityPQ = PriorityQueue(activity)

    override fun update(lemma: Clause) {
        lemma.forEach { lit ->
            val v = lit.variable
            activity[v] += activityInc
            if (activityPQ.index[v] != -1) {
                activityPQ.siftUp(activityPQ.index[v])
            }
            if (activity[v] > activityLimit) {
                activity.forEachIndexed { ind, value ->
                    activity[ind] = value / activityLimit
                }
                activityInc /= activityLimit
            }
        }

        activityInc *= multiplier
        numberOfConflicts++
    }

    override fun addVariable() {
        activity.add(0.0)
        numberOfVariables++
    }

    override fun build(clauses: List<Clause>) {
        while (activity.size < numberOfVariables) {
            activity.add(0.0)
        }
        clauses.forEach { clause ->
            clause.forEach { lit ->
                activity[lit.variable] += activityInc
            }
        }
        activityPQ.buildHeap(activity)
    }

    // returns the literal as the assumptions give information about the value of the variable
    override fun nextDecision(vars: List<VarState>, level: Int): Lit {
        if (assumptions.any { solver.getValue(it) == LBool.FALSE }) {
            return Lit.UNDEF
        }
        // if there is undefined assumption pick it, other way pick best choice
        return assumptions.firstOrNull { solver.getValue(it) == LBool.UNDEFINED }
            ?: getMaxActivityVariable(vars).posLit
    }

    override fun backTrack(variable: Var) {
        if (activityPQ.index[variable] == -1) {
            activityPQ.insert(variable.ord)
        }
    }

    // Looks for index of undefined variable with max activity
    private fun getMaxActivityVariable(vars: List<VarState>): Var {
        while (true) {
            require(activityPQ.size > 0)
            val v = activityPQ.pop()
            if (vars[v].value == LBool.UNDEFINED) {
                return Var(v)
            }
        }
    }
}

class FixedOrder(val solver: CDCL) : VariableSelector() {
    override fun build(clauses: List<Clause>) {
    }

    override fun nextDecision(vars: List<VarState>, level: Int): Lit {
        for (i in 1..vars.lastIndex) {
            if (vars[i].value == LBool.UNDEFINED) return Lit(i)
        }
        return Lit.UNDEF
    }

    override fun addVariable() {
    }

    override fun update(lemma: Clause) {
    }

    override fun backTrack(variable: Var) {
    }
}

class VsidsWithoutQueue(private var numberOfVariables: Int = 0, private val solver: CDCL) : VariableSelector() {
    private val decay = 50
    private val multiplier = 2.0
    private val activityLimit = 1e100
    private var activityInc = 1.0

    private var numberOfConflicts = 0

    // list of activity for variables
    private val activity = mutableListOf<Double>()

    override fun update(lemma: Clause) {
        lemma.forEach { lit ->
            val v = lit.variable
            activity[v] += activityInc
        }

        numberOfConflicts++
        if (numberOfConflicts == decay) {
            activityInc *= multiplier
            // update activity
            numberOfConflicts = 0
            if (activityInc > activityLimit) {
                activity.forEachIndexed { ind, value ->
                    activity[ind] = value / activityInc
                }
                activityInc = 1.0
            }
        }
    }

    override fun addVariable() {
        activity.add(0.0)
        numberOfVariables++
    }

    override fun build(clauses: List<Clause>) {
        while (activity.size < numberOfVariables) {
            activity.add(0.0)
        }
        clauses.forEach { clause ->
            clause.forEach { lit ->
                activity[lit.variable] += activityInc
            }
        }
    }

    override fun nextDecision(vars: List<VarState>, level: Int): Lit {
        if (assumptions.any { solver.getValue(it) == LBool.FALSE }) {
            return Lit.UNDEF
        }
        // if there is undefined assumption pick it, other way pick best choice
        return assumptions.firstOrNull { solver.getValue(it) == LBool.UNDEFINED } ?: getMaxActivityVariable(vars).posLit
    }

    override fun backTrack(variable: Var) {
    }

    // Looks for index of undefined variable with max activity
    private fun getMaxActivityVariable(vars: List<VarState>): Var {
        var v = Var.UNDEF
        var max = -1.0
        for (i in 0 until numberOfVariables) {
            if (vars[i].value == LBool.UNDEFINED && max < activity[i]) {
                v = Var(i)
                max = activity[i]
            }
        }
        return v
    }
}
