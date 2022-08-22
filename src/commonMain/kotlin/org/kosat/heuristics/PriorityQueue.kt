package org.kosat.heuristics

import org.kosat.swap

// TODO: Refactor code

class PriorityQueue {
    val heap: MutableList<Pair<Double, Int>> = mutableListOf()
    val order: MutableList<Int> = mutableListOf()
    var maxSize = -1
    var size = 0

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
        heap.swap(u, v)
        order[heap[u].second] = u
        order[heap[v].second] = v
    }

    fun heapify(u: Int) {
        if (ls(u) > size - 1) {
            return
        }
        if (rs(u) > size - 1) {
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
        for (ind in 0 until size) {
            heap[ind] = Pair(heap[ind].first / divisor, heap[ind].second)
        }
    }

    fun getMax(): Pair<Double, Int> {
        require(size != 0)
        return heap[0]
    }

    fun deleteMax() {
        require(size != 0)
        swap(0, size - 1)
        order[heap[size - 1].second] = -1
        size--
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
        require(size != maxSize)
        heap[size] = newValue
        order[newValue.second] = size
        size++
        liftVertex(size - 1)
    }

    fun increaseActivity(ind: Int, delta: Double) {
        val u = order[ind]
        heap[u] = Pair(heap[u].first + delta, heap[u].second)
        liftVertex(u)
    }

    fun buildHeap(activity: MutableList<Double>) {
        for (ind in 0..activity.lastIndex) {
            heap.add(Pair(activity[ind], ind))
        }
        size = heap.size
        while (order.size < size) {
            order.add(0)
        }
        heap.forEachIndexed { ind, elem ->
            order[elem.second] = ind
        }
        for (ind in (heap.size / 2) downTo 0) {
            heapify(ind)
        }
        maxSize = size
    }

    operator fun Pair<Double, Int>.compareTo(other: Pair<Double, Int>): Int {
        return if (this.first > other.first || (this.first == other.first && this.second < other.second)) {
            1
        } else if (this.first < other.first || (this.first == other.first && this.second > other.second)) {
            -1
        } else if (this.second > other.second) {
            -1
        } else if (this.second < other.second) {
            1
        } else {
            0
        }
    }
}
