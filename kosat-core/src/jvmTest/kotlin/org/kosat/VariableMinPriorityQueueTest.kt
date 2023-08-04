package org.kosat

import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class VariableMinPriorityQueueTest {
    @Test
    fun works() {
        val queue = CDCL.VariableMinPriorityQueue(3)
        val x = Var(0)
        val y = Var(1)
        val z = Var(2)

        queue.setKey(x, 3.0)
        queue.setKey(y, 2.0)
        queue.setKey(x, 1.0)

        assertTrue(queue.size == 3)

        assertEquals(z, queue.pop())
        assertTrue(queue.contains(x))
        assertTrue(queue.contains(y))
        assertTrue(!queue.contains(z))
        assertTrue(queue.size == 2)

        assertEquals(x, queue.pop())
        assertTrue(!queue.contains(x))
        assertTrue(queue.contains(y))
        assertTrue(!queue.contains(z))
        assertTrue(queue.size == 1)

        assertEquals(y, queue.pop())
        assertTrue(!queue.contains(x))
        assertTrue(!queue.contains(y))
        assertTrue(!queue.contains(z))
        assertTrue(queue.size == 0)
    }
}