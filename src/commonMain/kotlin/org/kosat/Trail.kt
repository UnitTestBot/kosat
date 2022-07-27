package org.kosat

class Trail(val solver: CDCL): Collection<Int>, Iterable<Int> {
    val lastIndex: Int
        get() = trail.lastIndex

    // all decisions and consequences
    private val trail: MutableList<Int> = mutableListOf()

    // clear trail until given level
    fun clear(until: Int = -1) {
        while (trail.isNotEmpty() && solver.vars[trail.last()].level > until) {
            solver.delVariable(trail.removeLast())
        }
    }

    override val size: Int
        get() = trail.size

    override fun contains(element: Int): Boolean = trail.contains(element)

    override fun containsAll(elements: Collection<Int>): Boolean = trail.containsAll(elements)

    override fun isEmpty(): Boolean = trail.isEmpty()

    override fun iterator(): Iterator<Int> = trail.iterator()

    operator fun get(ind: Int): Int = trail[ind]

    fun add(v: Int) {
        trail.add(v)
    }
}