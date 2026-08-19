/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.microsoft.playwright.spring.boot.utils;


import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility helpers for creating thread pools, thread factories and for performing
 * graceful shutdown of threads and executors.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 */
@Slf4j
public final class ThreadUtils {

    /**
     * Creates a {@link ThreadPoolExecutor} with a named, optionally-daemon thread
     * factory.
     * @param corePoolSize    the number of threads to keep in the pool even when idle
     * @param maximumPoolSize the maximum number of threads ever allowed in the pool
     * @param keepAliveTime   the maximum time an idle thread waits for new tasks
     * @param unit            the time unit for {@code keepAliveTime}
     * @param workQueue       the queue used for holding tasks before they are executed
     * @param processName     the base name used for created threads
     * @param isDaemon        whether created threads should be daemon threads
     * @return the configured thread-pool executor
     */
    public static ExecutorService newThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                                        TimeUnit unit, BlockingQueue<Runnable> workQueue, String processName, boolean isDaemon) {
        return new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, newThreadFactory(processName, isDaemon));
    }

    /**
     * Creates a single-thread executor whose worker uses the given process name.
     * @param processName the base name used for the created thread
     * @param isDaemon    whether the created thread should be a daemon thread
     * @return the single-thread executor
     */
    public static ExecutorService newSingleThreadExecutor(String processName, boolean isDaemon) {
        return Executors.newSingleThreadExecutor(newThreadFactory(processName, isDaemon));
    }

    /**
     * Creates a single-thread scheduled executor whose worker uses the given process
     * name.
     * @param processName the base name used for the created thread
     * @param isDaemon    whether the created thread should be a daemon thread
     * @return the single-thread scheduled executor
     */
    public static ScheduledExecutorService newSingleThreadScheduledExecutor(String processName, boolean isDaemon) {
        return Executors.newSingleThreadScheduledExecutor(newThreadFactory(processName, isDaemon));
    }

    /**
     * Creates a fixed-size scheduled thread pool whose workers use the given process
     * name.
     * @param nThreads    the number of threads in the pool
     * @param processName the base name used for created threads
     * @param isDaemon    whether created threads should be daemon threads
     * @return the fixed-size scheduled thread pool
     */
    public static ScheduledExecutorService newFixedThreadScheduledPool(int nThreads, String processName,
                                                                       boolean isDaemon) {
        return Executors.newScheduledThreadPool(nThreads, newThreadFactory(processName, isDaemon));
    }

    /**
     * Creates a thread factory that prefixes created threads with {@code "Remoting-"}
     * followed by the supplied process name.
     * @param processName the base name used for created threads
     * @param isDaemon    whether created threads should be daemon threads
     * @return the configured thread factory
     */
    public static ThreadFactory newThreadFactory(String processName, boolean isDaemon) {
        return newGenericThreadFactory("Remoting-" + processName, isDaemon);
    }

    /**
     * Creates a non-daemon thread factory using the given process name.
     * @param processName the base name used for created threads
     * @return the configured thread factory
     */
    public static ThreadFactory newGenericThreadFactory(String processName) {
        return newGenericThreadFactory(processName, false);
    }

    /**
     * Creates a non-daemon thread factory using the given process name and thread count.
     * @param processName the base name used for created threads
     * @param threads     the pool size embedded into each thread name
     * @return the configured thread factory
     */
    public static ThreadFactory newGenericThreadFactory(String processName, int threads) {
        return newGenericThreadFactory(processName, threads, false);
    }

    /**
     * Creates a thread factory that numbers created threads sequentially using the
     * supplied process name.
     * @param processName the base name used for created threads
     * @param isDaemon    whether created threads should be daemon threads
     * @return the configured thread factory
     */
    public static ThreadFactory newGenericThreadFactory(final String processName, final boolean isDaemon) {
        return new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
    /**
     * <p>New thread.</p>
     * @param r
     * @return the new thread
     */
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, String.format("%s_%d", processName, this.threadIndex.incrementAndGet()));
                thread.setDaemon(isDaemon);
                return thread;
            }
        };
    }

    /**
     * Creates a thread factory that numbers created threads sequentially using the
     * supplied process name and pool size.
     * @param processName the base name used for created threads
     * @param threads     the pool size embedded into each thread name
     * @param isDaemon    whether created threads should be daemon threads
     * @return the configured thread factory
     */
    public static ThreadFactory newGenericThreadFactory(final String processName, final int threads,
                                                        final boolean isDaemon) {
        return new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
    /**
     * <p>New thread.</p>
     * @param r
     * @return the new thread
     */
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, String.format("%s_%d_%d", processName, threads, this.threadIndex.incrementAndGet()));
                thread.setDaemon(isDaemon);
                return thread;
            }
        };
    }

    /**
     * Create a new thread
     *
     * @param name The name of the thread
     * @param runnable The work for the thread to do
     * @param daemon Should the thread block JVM stop?
     * @return The unstarted thread
     */
    public static Thread newThread(String name, Runnable runnable, boolean daemon) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(daemon);
        thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
    /**
     * <p>Uncaught exception.</p>
     * @param t
     * @param e
     */
            public void uncaughtException(Thread t, Throwable e) {
                log.error("Uncaught exception in thread '" + t.getName() + "':", e);
            }
        });
        return thread;
    }

    /**
     * Shutdown passed thread using isAlive and join.
     *
     * @param t Thread to stop
     */
    public static void shutdownGracefully(final Thread t) {
        shutdownGracefully(t, 0);
    }

    /**
     * Shutdown passed thread using isAlive and join.
     *
     * @param millis Pass 0 if we're to wait forever.
     * @param t Thread to stop
     */
    public static void shutdownGracefully(final Thread t, final long millis) {
        if (t == null) {
            return;
        }
        while (t.isAlive()) {
            try {
                t.interrupt();
                t.join(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * An implementation of the graceful stop sequence recommended by
     * {@link ExecutorService}.
     *
     * @param executor executor
     * @param timeout timeout
     * @param timeUnit timeUnit
     */
    public static void shutdownGracefully(ExecutorService executor, long timeout, TimeUnit timeUnit) {
        // Disable new tasks from being submitted.
        executor.shutdown();
        try {
            // Wait a while for existing tasks to terminate.
            if (!executor.awaitTermination(timeout, timeUnit)) {
                executor.shutdownNow();
                // Wait a while for tasks to respond to being cancelled.
                if (!executor.awaitTermination(timeout, timeUnit)) {
                    log.warn(String.format("%s didn't terminate!", executor));
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted.
            executor.shutdownNow();
            // Preserve interrupt status.
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A constructor to stop this class being constructed.
     */
    private ThreadUtils() {
        // Unused

    }
}
