package org.kosat.heuristics

import org.kosat.swap

class PriorityQueue {
    // stores max-heap built on variable activities
    val heap: MutableList<Pair<Double, Int>> = mutableListOf()

    // for each variable contains index with it position in heap
    val index: MutableList<Int> = mutableListOf()

    // maximum possible size of heap
    private var capacity = -1

    // current size
    var size = 0

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
        index[heap[u].second] = u
        index[heap[v].second] = v
    }

    // if for element both children subtrees are heaps - make a heap for O(logn)
    private fun heapify(u: Int) {
        if (leftChild(u) > size - 1) {
            return
        }
        if (rightChild(u) > size - 1) {
            if (heap[leftChild(u)] > heap[u]) {
                swap(u, leftChild(u))
            }
            return
        }
        if (heap[leftChild(u)] > heap[rightChild(u)]) {
            if (heap[leftChild(u)] > heap[u]) {
                swap(u, leftChild(u))
                heapify(leftChild(u))
            }
        } else if (heap[rightChild(u)] > heap[u]) {
            swap(u, rightChild(u))
            heapify(rightChild(u))
        }
    }

    fun divideAllElements(divisor: Double) {
        for (ind in 0 until size) {
            heap[ind] = Pair(heap[ind].first / divisor, heap[ind].second)
        }
    }

    // returns element on top of heap
    fun top(): Pair<Double, Int> {
        require(size != 0)
        return heap[0]
    }

    // delete element on top of heap and returns it
    fun pop(): Pair<Double, Int> {
        require(size != 0)
        val max = top()
        swap(0, size - 1)
        index[heap[size - 1].second] = -1
        size--
        if (heap.isNotEmpty()) {
            heapify(0)
        }
        return max
    }

    // if some value of vertex increased this function lift this vertex up to save heap structure
    fun siftUp(u: Int) {
        var curInd = u
        var parent = parent(curInd)
        while (curInd > 0 && heap[curInd] > heap[parent]) {
            swap(curInd, parent)
            curInd = parent
            parent = parent(curInd)
        }
    }

    fun insert(newValue: Pair<Double, Int>) {
        require(size != capacity)
        heap[size] = newValue
        index[newValue.second] = size
        size++
        siftUp(size - 1)
    }

    fun increaseActivity(variable: Int, delta: Double) {
        val u = index[variable]
        heap[u] = Pair(heap[u].first + delta, heap[u].second)
        siftUp(u)
    }

    fun buildHeap(activity: MutableList<Double>) {
        for (ind in 0..activity.lastIndex) {
            heap.add(Pair(activity[ind], ind))
        }
        size = heap.size
        while (index.size < size) {
            index.add(0)
        }
        heap.forEachIndexed { ind, elem ->
            index[elem.second] = ind
        }
        for (ind in (heap.size / 2) downTo 0) {
            heapify(ind)
        }
        capacity = size
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
