package dev.ryuya.oneuimods

import java.util.concurrent.atomic.AtomicBoolean

/** A burst of state changes shares one UI task; a change during rendering queues another. */
internal class CoalescedUpdate(
    private val post: (Runnable) -> Unit,
    private val render: () -> Unit,
) {
    private val pending = AtomicBoolean()
    private val task = Runnable {
        pending.set(false)
        render()
    }

    fun request() {
        if (pending.compareAndSet(false, true)) post(task)
    }
}
