package com.ticketbooking.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/**
 * Runs N tasks at (as close as possible to) the same instant.
 *
 * Every task gets its own thread, waits on a shared start gate, and only
 * starts once all threads are ready. This maximises overlap, which is what
 * exposes race conditions.
 */
public final class ConcurrentRunner {

    private ConcurrentRunner() {}

    public static <T> List<T> runConcurrently(int count, IntFunction<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final int index = i;
                Callable<T> callable = () -> {
                    ready.countDown();
                    start.await();
                    return task.apply(index);
                };
                futures.add(pool.submit(callable));
            }
            ready.await();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
