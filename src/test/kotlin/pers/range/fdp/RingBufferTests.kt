package pers.range.fdp

import org.junit.jupiter.api.Test
import pers.range.fdp.common.RingBuffer

class RingBufferTests {

    @Test
    fun ringBufferTest() {
        val ring = RingBuffer(5);
        ring.put(1)
        ring.put(2)
        ring.put(3)
        ring.put(4)
        ring.put(5)
        printRing(ring, 3)
        ring.put(1)
        ring.put(2)
        ring.put(3)
        ring.put(4)
        printRing(ring, 3)
        ring.put(1)
        ring.put(2)
        ring.put(3)
        ring.put(4)
        printRing(ring, 5)
    }

    private fun printRing(ring: RingBuffer, count: Int) {
        for (i in 1..count) {
            println(ring.take())
        }
        println()
    }

}