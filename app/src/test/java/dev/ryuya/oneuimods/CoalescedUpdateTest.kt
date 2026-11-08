package dev.ryuya.oneuimods

import java.util.ArrayDeque
import org.junit.Assert.*
import org.junit.Test

class CoalescedUpdateTest {
    @Test
    fun burstsRenderLatestStateAndChangesDuringRenderAreRetained() {
        val queue = ArrayDeque<Runnable>()
        var revision = 0
        val rendered = mutableListOf<Int>()
        lateinit var update: CoalescedUpdate
        update =
            CoalescedUpdate({ queue.add(it) }) {
                rendered.add(revision)
                if (revision == 100) {
                    revision++
                    update.request()
                }
            }
        repeat(100) {
            revision++
            update.request()
        }
        assertEquals(1, queue.size)
        queue.remove().run()
        assertEquals(listOf(100), rendered)
        assertEquals(1, queue.size)
        queue.remove().run()
        assertEquals(listOf(100, 101), rendered)
        assertTrue(queue.isEmpty())
    }
}
