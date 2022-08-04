package org.kosat.heuristics

class PriorityQueue {
    val heap: MutableList<Pair<Double, Int>> = mutableListOf()
    var maxSize = -1
    private var sz = 0

    private fun ls(u: Int): Int {
        return 2 * u + 1
    }

    private fun rs(u: Int): Int {
        return 2 * u + 2
    }

    private fun parent(u: Int): Int {
        return (u - 1) / 2
    }

    fun heapify(u: Int) {
        if (ls(u) > sz - 1) {
            return
        }
        if (rs(u) > sz - 1) {
            if (heap[ls(u)] > heap[u]) {
                heap[u] = heap[ls(u)].also { heap[ls(u)] = heap[u] }
            }
            return
        }
        if (heap[ls(u)] > heap[rs(u)]) {
            if (heap[ls(u)] > heap[u]) {
                heap[u] = heap[ls(u)].also { heap[ls(u)] = heap[u] }
                heapify(ls(u))
            }
        } else if (heap[rs(u)] > heap[u]) {
            heap[u] = heap[rs(u)].also { heap[rs(u)] = heap[u] }
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
        heap[0] = heap[sz - 1].also { heap[sz - 1] = heap[0] }
        sz--
        if (heap.isNotEmpty()) {
            heapify(0)
        }
    }

    fun addValue(newValue: Pair<Double, Int>) {
        require (sz != maxSize)
        heap[sz] = newValue
        sz++
        var curInd = sz - 1
        var parent = parent(curInd)
        while (curInd > 0 && heap[curInd] > heap[parent]) {
            heap[curInd] = heap[parent].also { heap[parent] = heap[curInd] }
            curInd = parent
            parent = parent(curInd)
        }
    }

    fun buildHeap(scores: MutableList<Double>) {
        for (ind in 1..scores.lastIndex) {
            heap.add(Pair(scores[ind], ind))
        }
        for (ind in (heap.size / 2)..0) {
            heapify(ind)
        }
        sz = heap.size
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