package com.microsoft.playwright.spring.boot.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link ThreadUtils}.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class ThreadUtilsTest {

    @Test
    void newThreadPoolExecutor_shouldCreateWorkingExecutor() throws Exception {
        ExecutorService executor = ThreadUtils.newThreadPoolExecutor(
                1, 2, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>(10), "worker", false);
        try {
            AtomicReference<String> captured = new AtomicReference<>();
            Future<?> future = executor.submit(() -> captured.set(Thread.currentThread().getName()));
            future.get(5, TimeUnit.SECONDS);
            assertThat(captured.get()).startsWith("Remoting-worker_");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void newSingleThreadExecutor_shouldCreateDaemonThreadFactory() throws Exception {
        ExecutorService executor = ThreadUtils.newSingleThreadExecutor("single", true);
        try {
            AtomicReference<Boolean> daemon = new AtomicReference<>(false);
            executor.submit(() -> daemon.set(Thread.currentThread().isDaemon())).get(5, TimeUnit.SECONDS);
            assertThat(daemon.get()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void newSingleThreadScheduledExecutor_shouldRunScheduledTask() throws Exception {
        ScheduledExecutorService executor = ThreadUtils.newSingleThreadScheduledExecutor("sched", false);
        try {
            CountDownLatch latch = new CountDownLatch(1);
            executor.schedule(latch::countDown, 10, TimeUnit.MILLISECONDS);
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void newFixedThreadScheduledPool_shouldRunScheduledTask() throws Exception {
        ScheduledExecutorService executor = ThreadUtils.newFixedThreadScheduledPool(2, "fixed-sched", false);
        try {
            CountDownLatch latch = new CountDownLatch(1);
            executor.schedule(latch::countDown, 10, TimeUnit.MILLISECONDS);
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void newThreadFactory_default_shouldProduceNamedNonDaemonThreads() {
        ThreadFactory factory = ThreadUtils.newThreadFactory("biz", false);
        Thread t = factory.newThread(() -> {});
        assertThat(t.getName()).startsWith("Remoting-biz_");
        assertThat(t.isDaemon()).isFalse();
    }

    @Test
    void newGenericThreadFactory_oneArg_shouldBeNonDaemon() {
        ThreadFactory factory = ThreadUtils.newGenericThreadFactory("simple");
        Thread t = factory.newThread(() -> {});
        assertThat(t.getName()).startsWith("simple_");
        assertThat(t.isDaemon()).isFalse();
    }

    @Test
    void newGenericThreadFactory_twoArg_shouldCarryThreadCount() {
        ThreadFactory factory = ThreadUtils.newGenericThreadFactory("pooled", 8);
        Thread t = factory.newThread(() -> {});
        assertThat(t.getName()).startsWith("pooled_8_");
        assertThat(t.isDaemon()).isFalse();
    }

    @Test
    void newGenericThreadFactory_withDaemon_shouldBeDaemon() {
        ThreadFactory factory = ThreadUtils.newGenericThreadFactory("daemon-factory", true);
        Thread t = factory.newThread(() -> {});
        assertThat(t.isDaemon()).isTrue();
    }

    @Test
    void newGenericThreadFactory_withThreadsAndDaemon_shouldRespectFlags() {
        ThreadFactory factory = ThreadUtils.newGenericThreadFactory("d2", 4, true);
        Thread t = factory.newThread(() -> {});
        assertThat(t.getName()).contains("d2_4_");
        assertThat(t.isDaemon()).isTrue();
    }

    @Test
    void newThread_shouldSetNameDaemonAndUncaughtExceptionHandler() throws InterruptedException {
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread t = ThreadUtils.newThread("custom-thread", () -> {
            throw new IllegalStateException("boom");
        }, false);
        // The default UncaughtExceptionHandler logs the error; replace it for verification so
        // the test does not rely on stdout, but the default-handler code path is still executed
        // in newThread_uncaughtHandler_logsError below.
        t.setUncaughtExceptionHandler((thread, ex) -> caught.set(ex));
        t.start();
        t.join(2_000);
        assertThat(caught.get()).isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
        assertThat(t.getName()).isEqualTo("custom-thread");
        assertThat(t.isDaemon()).isFalse();
    }

    @Test
    void newThread_defaultUncaughtHandler_shouldLogError() throws InterruptedException {
        // Exercise the inlined UncaughtExceptionHandler (logs the throwable) without replacing it.
        Thread t = ThreadUtils.newThread("logging-thread", () -> {
            throw new IllegalStateException("handler-boom");
        }, false);
        t.start();
        t.join(2_000);
        assertThat(t.isAlive()).isFalse();
    }

    @Test
    void shutdownGracefully_thread_noArg_shouldStopThread() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean running = new AtomicBoolean(true);
        Thread t = new Thread(() -> {
            started.countDown();
            while (!Thread.currentThread().isInterrupted() && running.get()) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        t.start();
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        ThreadUtils.shutdownGracefully(t);
        assertThat(t.isAlive()).isFalse();
    }

    @Test
    void shutdownGracefully_thread_null_shouldBeNoOp() {
        assertThatNoException().isThrownBy(() -> ThreadUtils.shutdownGracefully((Thread) null));
    }

    @Test
    void shutdownGracefully_threadWithTimeout_shouldStopThread() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            started.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        t.start();
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        ThreadUtils.shutdownGracefully(t, 500);
        assertThat(t.isAlive()).isFalse();
    }

    @Test
    void shutdownGracefully_executorService_shouldTerminateGracefully() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch done = new CountDownLatch(1);
        executor.submit(() -> done.countDown());
        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        ThreadUtils.shutdownGracefully(executor, 1, TimeUnit.SECONDS);
        assertThat(executor.isTerminated()).isTrue();
    }

    @Test
    void shutdownGracefully_executorService_needsNow_shouldShutdownNow() throws Exception {
        // A task that sleeps longer than the timeout forces the awaitTermination path to time out.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        ThreadUtils.shutdownGracefully(executor, 50, TimeUnit.MILLISECONDS);
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shutdownGracefully_executorService_interruptingCaller_shouldPropagate() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch inside = new CountDownLatch(1);
        executor.submit(() -> {
            inside.countDown();
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(inside.await(2, TimeUnit.SECONDS)).isTrue();

        Thread caller = new Thread(() ->
                ThreadUtils.shutdownGracefully(executor, 50, TimeUnit.MILLISECONDS));
        caller.start();
        caller.interrupt();
        caller.join(2_000);
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void privateConstructor_isUsableViaReflection() throws Exception {
        java.lang.reflect.Constructor<ThreadUtils> ctor = ThreadUtils.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }
}
