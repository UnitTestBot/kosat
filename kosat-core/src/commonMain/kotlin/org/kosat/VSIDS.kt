package org.kosat

class VSIDS {
    private var numberOfVariables = 0
    private val multiplier = 1.1
    private var numberOfConflicts = 0
    private var activityInc = 1.0
    private var activityLimit = 1e100

    /**
     * Activities of variables
     */
    val activity = mutableListOf<Double>()

    /**
     * Priority queue of variables sorted by activity. The activity list is
     * shared between the priority queue and VSIDS.
     */
    private var activityPQ = PriorityQueue(activity)

    class PriorityQueue(private val activity: List<Double>) {
        val heap: MutableList<Int> = mutableListOf()
        val index: MutableList<Int> = mutableListOf()
        private var capacity = -1
        var size = 0

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

        fun top(): Int {
            require(size != 0)
            return heap[0]
        }

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
            for (ind in (heap.size / 2 - 1) downTo 0) {
                siftDown(ind)
            }
            capacity = size
        }
    }

    /**
     * Bump the activity of all variables in the clause.
     *
     * This increases the activity of all variables in the clause and increases
     * the activity increment, making recent bumps have more effect.
     */
    fun bump(learnt: Clause) {
        learnt.lits.forEach { lit ->
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

    /**
     * Register a new variable in the variable selector.
     */
    fun addVariable() {
        activity.add(0.0)
        numberOfVariables++
    }

    /**
     * Build the priority queue of variables. Must be called before all
     * decisions.
     */
    fun build(clauses: List<Clause>) {
        clauses.forEach { clause ->
            clause.lits.forEach { lit ->
                activity[lit.variable] += activityInc
            }
        }
        activityPQ.buildHeap(activity)
    }

    /**
     * Select the next variable to assign.
     */
    fun nextDecision(assignment: Assignment): Var {
        while (true) {
            require(activityPQ.size > 0)
            val v = Var(activityPQ.pop())
            if (assignment.isActive(v) && assignment.value(v) == LBool.UNDEF) {
                return v
            }
        }
    }

    /**
     * Put the variable in the VSIDS queue again, if it is not already there.
     * This is used when a variable is unassigned in backtracking.
     */
    fun enqueueAgain(variable: Var) {
        if (activityPQ.index[variable] == -1) {
            activityPQ.insert(variable.index)
        }
    }
}
