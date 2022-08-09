package org.kosat.heuristics

class PriorityQueue {
    val heap: MutableList<Pair<Double, Int>> = mutableListOf()
    val order: MutableList<Int> = mutableListOf()
    var maxSize = -1
    var sz = 0

    private fun ls(u: Int): Int {
        return 2 * u + 1
    }

    private fun rs(u: Int): Int {
        return 2 * u + 2
    }

    private fun parent(u: Int): Int {
        return (u - 1) / 2
    }

    fun swap(u: Int, v: Int) {
        heap[u] = heap[v].also { heap[v] = heap[u] }
        order[heap[u].second] = u
        order[heap[v].second] = v
    }

    fun heapify(u: Int) {
        if (ls(u) > sz - 1) {
            return
        }
        if (rs(u) > sz - 1) {
            if (heap[ls(u)] > heap[u]) {
                swap(u, ls(u))
            }
            return
        }
        if (heap[ls(u)] > heap[rs(u)]) {
            if (heap[ls(u)] > heap[u]) {
                swap(u, ls(u))
                heapify(ls(u))
            }
        } else if (heap[rs(u)] > heap[u]) {
            swap(u, rs(u))
            heapify(rs(u))
        }
    }

    fun divideAllElements(divisor: Double) {
        for (ind in 0 until sz) {
            heap[ind] = Pair(heap[ind].first / divisor, heap[ind].second)
        }
    }

    fun getMax(): Pair<Double, Int> {
        require(sz != 0)
        return heap[0]
    }

    fun deleteMax() {
        require(sz != 0)
        swap(0, sz - 1)
        order[heap[sz - 1].second] = -1
        sz--
        if (heap.isNotEmpty()) {
            heapify(0)
        }
    }

    // if some value of vertex increased this function lift this vertex up to save heap structure
    fun liftVertex(u: Int) {
        var curInd = u
        var parent = parent(curInd)
        while (curInd > 0 && heap[curInd] > heap[parent]) {
            swap(curInd, parent)
            curInd = parent
            parent = parent(curInd)
        }
    }

    fun addValue(newValue: Pair<Double, Int>) {
        require(sz != maxSize)
        heap[sz] = newValue
        order[newValue.second] = sz
        sz++
        liftVertex(sz - 1)
    }

    fun increaseActivity(ind: Int, delta: Double) {
        val u = order[ind]
        heap[u] = Pair(heap[u].first + delta, heap[u].second)
        liftVertex(u)
    }

    fun buildHeap(activity: MutableList<Double>) {
        for (ind in 1..activity.lastIndex) {
            heap.add(Pair(activity[ind], ind))
        }
        for (ind in (heap.size / 2)..0) {
            heapify(ind)
        }
        sz = heap.size
        while (order.size < sz + 1) {
            order.add(0)
        }
        heap.forEachIndexed { ind, elem ->
            order[elem.second] = ind
        }
        maxSize = sz
    }

    operator fun Pair<Double, Int>.compareTo(other: Pair<Double, Int>): Int {
        return if (this.first > other.first || (this.first == other.first && this.second > other.second)) {
            1
        } else if (this.first < other.first || (this.first == other.first && this.second < other.second)) {
            -1
        } else {
            0
        }
    }

}
