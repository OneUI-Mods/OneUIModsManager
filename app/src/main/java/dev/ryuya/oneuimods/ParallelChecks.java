package dev.ryuya.oneuimods;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Bounds concurrent requests and stops launching work when a server asks us to wait. */
final class ParallelChecks {
  record Result(boolean successful, long retryAt) {}

  record Summary(boolean successful, int completed) {}

  static <T> Summary run(
      List<T> items, int concurrency, Function<T, Result> check, Consumer<Result> onCompleted)
      throws InterruptedException {
    if (concurrency < 1) throw new IllegalArgumentException("Concurrency must be positive");
    ExecutorService pool = Executors.newFixedThreadPool(concurrency);
    CompletionService<Result> results = new ExecutorCompletionService<>(pool);
    int next = 0;
    int running = 0;
    int completed = 0;
    boolean successful = true;
    boolean throttled = false;
    try {
      while (next < items.size() || running > 0) {
        while (!throttled && next < items.size() && running < concurrency) {
          T item = items.get(next++);
          results.submit(() -> check.apply(item));
          running++;
        }
        if (running == 0) break;
        Result result;
        try {
          result = results.take().get();
        } catch (ExecutionException failed) {
          result = new Result(false, 0);
        }
        running--;
        completed++;
        successful &= result.successful();
        throttled |= result.retryAt() > 0;
        onCompleted.accept(result); // Coordinator thread owns progress and throttle state.
      }
      return new Summary(successful && completed == items.size(), completed);
    } finally {
      pool.shutdownNow();
    }
  }
}
