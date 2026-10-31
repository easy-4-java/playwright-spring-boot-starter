package com.microsoft.playwright.spring.boot.monitor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MemoryMonitor}.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class MemoryMonitorTest {

    @Test
    void constructor_shouldStoreThreshold() {
        MemoryMonitor monitor = new MemoryMonitor(0.42d);
        // isMemoryAvailable reflects the threshold indirectly; threshold of 0.42 with little heap used
        // means available should be true.
        assertThat(monitor.isMemoryAvailable()).isTrue();
    }

    @Test
    void monitorMemory_shouldRunWithoutError() {
        MemoryMonitor monitor = new MemoryMonitor(0.99d);
        // No assertions on output (it logs), but the method must complete cleanly.
        monitor.monitorMemory();
    }

    @Test
    void isMemoryAvailable_withZeroThreshold_shouldReportNotAvailable() {
        // A threshold of 0.0 means any used memory (> 0 in practice) exceeds it.
        MemoryMonitor monitor = new MemoryMonitor(0.0d);
        // Depending on the JVM the very first used/max ratio could be > 0; treat as non-available.
        boolean available = monitor.isMemoryAvailable();
        // usedMemory/maxMemory >= 0 -> ratio == 0 only when nothing is used, which is unrealistic.
        assertThat(available).isFalse();
    }

    @Test
    void isMemoryAvailable_withHighThreshold_shouldReportAvailable() {
        MemoryMonitor monitor = new MemoryMonitor(1.0d);
        // usedMemory/maxMemory < 1.0 in any healthy JVM, so available should be true.
        assertThat(monitor.isMemoryAvailable()).isTrue();
    }

    @Test
    void monitorMemory_withZeroThreshold_shouldTriggerCleanupBranch() {
        // A threshold of 0.0 guarantees memoryUsage > threshold, exercising the
        // "trigger cleanup" log + System.gc() branch.
        MemoryMonitor monitor = new MemoryMonitor(0.0d);
        monitor.monitorMemory();
    }
}
