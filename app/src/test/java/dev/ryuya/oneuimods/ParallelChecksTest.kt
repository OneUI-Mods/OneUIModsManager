package dev.ryuya.oneuimods

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.IntBinaryOperator
import java.util.stream.IntStream
import kotlin.math.max
import org.junit.Assert.*
import org.junit.Test

open class ParallelChecksTest {
    @Test(timeout = 10000)
    @Throws(Exception::class)
    open fun fourChecksOverlapWithoutExceedingTheLimit() {
        val firstFour = CountDownLatch(4)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val callbacks = AtomicInteger()
        val summary =
            ParallelChecks.run<Int?>(
                IntStream.range(0, 14).boxed().toList(),
                4,
                Function { item: Int? ->
                    val count = active.incrementAndGet()
                    maximum.accumulateAndGet(
                        count,
                        IntBinaryOperator { a: Int, b: Int -> Math.max(a, b) },
                    )
                    firstFour.countDown()
                    try {
                        assertTrue(firstFour.await(3, TimeUnit.SECONDS))
                        return@Function ParallelChecks.Result(true, 0)
                    } catch (e: InterruptedException) {
                        throw AssertionError(e)
                    } finally {
                        active.decrementAndGet()
                    }
                },
                Consumer { result: ParallelChecks.Result? -> callbacks.incrementAndGet() },
            )
        assertTrue(summary!!.successful)
        assertEquals(14, summary!!.completed)
        assertEquals(14, callbacks.get())
        assertEquals(4, maximum.get())
    }

    @Test(timeout = 10000)
    @Throws(Exception::class)
    open fun rateLimitDrainsInFlightChecksButDoesNotStartTheRest() {
        val throttleReceived = CountDownLatch(1)
        val started = AtomicInteger()
        val summary =
            ParallelChecks.run<Int?>(
                java.util.List.of<Int?>(0, 1, 2, 3, 4, 5),
                2,
                Function { item: Int? ->
                    started.incrementAndGet()
                    if (item == 0) return@Function ParallelChecks.Result(false, Long.MAX_VALUE)
                    try {
                        assertTrue(throttleReceived.await(3, TimeUnit.SECONDS))
                    } catch (e: InterruptedException) {
                        throw AssertionError(e)
                    }
                    ParallelChecks.Result(true, 0)
                },
                Consumer { result: ParallelChecks.Result? ->
                    if (result!!.retryAt > 0) throttleReceived.countDown()
                },
            )
        assertFalse(summary!!.successful)
        assertEquals(2, started.get())
        assertEquals(2, summary!!.completed)
    }

    @Test(timeout = 10000)
    @Throws(Exception::class)
    open fun failedRepositoryDoesNotPreventOtherRepositoriesFromCompleting() {
        val callbacks = AtomicInteger()
        val summary =
            ParallelChecks.run<Int?>(
                java.util.List.of<Int?>(0, 1, 2),
                2,
                Function { item: Int? ->
                    check(item != 1) { "One repository failed" }
                    ParallelChecks.Result(true, 0)
                },
                Consumer { result: ParallelChecks.Result? -> callbacks.incrementAndGet() },
            )
        assertFalse(summary!!.successful)
        assertEquals(3, summary!!.completed)
        assertEquals(3, callbacks.get())
    }
}
