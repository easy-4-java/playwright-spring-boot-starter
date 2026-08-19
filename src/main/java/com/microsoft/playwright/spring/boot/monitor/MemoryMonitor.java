package com.microsoft.playwright.spring.boot.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
@Slf4j
public class MemoryMonitor {

    private final double memoryThreshold;

    public MemoryMonitor(double memoryThreshold) {
        this.memoryThreshold = memoryThreshold;
    }

    @Scheduled(fixedRate = 5000) // 每5秒检查一次
    /**
     * <p>Monitor memory.</p>
     */
    public void monitorMemory() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        double memoryUsage = (double) usedMemory / maxMemory;

        if (memoryUsage > memoryThreshold) {
            log.warn("High memory usage detected: {}%, triggering cleanup",
                String.format("%.2f", memoryUsage * 100));
            System.gc();
        }

        log.info("Memory usage: {}/{} MB ({}%)",
            usedMemory / 1024 / 1024,
            maxMemory / 1024 / 1024,
            String.format("%.2f", memoryUsage * 100));
    }

    /**
     * <p>Checks if memory available.</p>
     * @return the is memory available
     */
    public boolean isMemoryAvailable() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return (double) usedMemory / maxMemory < memoryThreshold;
    }
}
