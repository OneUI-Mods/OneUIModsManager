package dev.ryuya.oneuimods

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.function.Consumer
import java.util.function.Function
import kotlin.jvm.JvmRecord
import kotlin.jvm.JvmStatic

/* Bounds concurrent requests and stops launching work when a server asks us to wait. */
object ParallelChecks {
    @JvmStatic
    @Throws(InterruptedException::class)
    fun <T> run(
        items: MutableList<T?>,
        concurrency: Int,
        check: Function<T?, ParallelChecks.Result?>,
        onCompleted: Consumer<ParallelChecks.Result?>,
    ): ParallelChecks.Summary? {
        require(concurrency >= 1) { "Concurrency must be positive" }
        val pool = Executors.newFixedThreadPool(concurrency)
        val results = ExecutorCompletionService<ParallelChecks.Result?>(pool)
        var next: Int = 0
        var running: Int = 0
        var completed: Int = 0
        var successful: Boolean = true
        var throttled: Boolean = false
        try {
            while (next < items.size || running > 0) {
                while (!throttled && next < items.size && running < concurrency) {
                    val item = items.get(next++)
                    results.submit(Callable { check.apply(item) })
                    running++
                }
                if (running == 0) break
                var result: ParallelChecks.Result?
                try {
                    result = results.take().get()
                } catch (failed: ExecutionException) {
                    result = ParallelChecks.Result(false, 0)
                }
                running--
                completed++
                successful = successful and result!!.successful
                throttled = throttled or (result!!.retryAt > 0)
                onCompleted.accept(result) // Coordinator thread owns progress and throttle state.
            }
            return ParallelChecks.Summary(successful && completed == items.size, completed)
        } finally {
            pool.shutdownNow()
        }
    }

    @JvmRecord
    data class Result(val successful: Boolean, val retryAt: Long) {
        override fun toString(): String = "Result[successful=$successful, retryAt=$retryAt]"
    }

    @JvmRecord
    data class Summary(val successful: Boolean, val completed: Int) {
        override fun toString(): String = "Summary[successful=$successful, completed=$completed]"
    }
}
