package dev.ryuya.oneuimods;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.Test;

public class ParallelChecksTest {
  @Test(timeout = 10000)
  public void fourChecksOverlapWithoutExceedingTheLimit() throws Exception {
    CountDownLatch firstFour = new CountDownLatch(4);
    AtomicInteger active = new AtomicInteger();
    AtomicInteger maximum = new AtomicInteger();
    AtomicInteger callbacks = new AtomicInteger();
    ParallelChecks.Summary summary =
        ParallelChecks.run(
            IntStream.range(0, 14).boxed().toList(),
            4,
            item -> {
              int count = active.incrementAndGet();
              maximum.accumulateAndGet(count, Math::max);
              firstFour.countDown();
              try {
                assertTrue(firstFour.await(3, TimeUnit.SECONDS));
                return new ParallelChecks.Result(true, 0);
              } catch (InterruptedException e) {
                throw new AssertionError(e);
              } finally {
                active.decrementAndGet();
              }
            },
            result -> callbacks.incrementAndGet());
    assertTrue(summary.successful());
    assertEquals(14, summary.completed());
    assertEquals(14, callbacks.get());
    assertEquals(4, maximum.get());
  }

  @Test(timeout = 10000)
  public void rateLimitDrainsInFlightChecksButDoesNotStartTheRest() throws Exception {
    CountDownLatch throttleReceived = new CountDownLatch(1);
    AtomicInteger started = new AtomicInteger();
    ParallelChecks.Summary summary =
        ParallelChecks.run(
            List.of(0, 1, 2, 3, 4, 5),
            2,
            item -> {
              started.incrementAndGet();
              if (item == 0) return new ParallelChecks.Result(false, Long.MAX_VALUE);
              try {
                assertTrue(throttleReceived.await(3, TimeUnit.SECONDS));
              } catch (InterruptedException e) {
                throw new AssertionError(e);
              }
              return new ParallelChecks.Result(true, 0);
            },
            result -> {
              if (result.retryAt() > 0) throttleReceived.countDown();
            });
    assertFalse(summary.successful());
    assertEquals(2, started.get());
    assertEquals(2, summary.completed());
  }

  @Test(timeout = 10000)
  public void failedRepositoryDoesNotPreventOtherRepositoriesFromCompleting() throws Exception {
    AtomicInteger callbacks = new AtomicInteger();
    ParallelChecks.Summary summary =
        ParallelChecks.run(
            List.of(0, 1, 2),
            2,
            item -> {
              if (item == 1) throw new IllegalStateException("One repository failed");
              return new ParallelChecks.Result(true, 0);
            },
            result -> callbacks.incrementAndGet());
    assertFalse(summary.successful());
    assertEquals(3, summary.completed());
    assertEquals(3, callbacks.get());
  }
}
