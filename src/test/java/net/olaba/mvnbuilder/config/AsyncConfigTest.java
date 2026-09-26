package net.olaba.mvnbuilder.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncConfigTest {

    @Test
    void acceptsMoreTasksThanPoolAndQueueCanHold() throws InterruptedException {
        final ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig().taskExecutor();
        final CountDownLatch started = new CountDownLatch(11);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch completed = new CountDownLatch(60);
        final AtomicReference<Throwable> submissionFailure = new AtomicReference<>();
        final Thread submitter = new Thread(() -> {
            try {
                for (int i = 0; i < 60; i++) {
                    executor.execute(() -> {
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            completed.countDown();
                        }
                    });
                }
            } catch (Throwable failure) {
                submissionFailure.set(failure);
            }
        });
        try {
            submitter.start();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            release.countDown();
            submitter.join(5000);
            assertNull(submissionFailure.get());
            assertTrue(completed.await(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
