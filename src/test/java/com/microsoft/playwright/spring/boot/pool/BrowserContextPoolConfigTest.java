package com.microsoft.playwright.spring.boot.pool;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BrowserContextPoolConfig}.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class BrowserContextPoolConfigTest {

    @Test
    void defaults_shouldMatchCommonsPoolDefaults() {
        BrowserContextPoolConfig config = new BrowserContextPoolConfig();
        assertThat(config.isBlockWhenExhausted()).isEqualTo(GenericObjectPoolConfig.DEFAULT_BLOCK_WHEN_EXHAUSTED);
        assertThat(config.getDurationBetweenEvictionRuns()).isEqualTo(GenericObjectPoolConfig.DEFAULT_DURATION_BETWEEN_EVICTION_RUNS);
        assertThat(config.getEvictorShutdownTimeoutDuration()).isEqualTo(GenericObjectPoolConfig.DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT);
        assertThat(config.getEvictionPolicyClassName()).isEqualTo(GenericObjectPoolConfig.DEFAULT_EVICTION_POLICY_CLASS_NAME);
        assertThat(config.isFairness()).isEqualTo(GenericObjectPoolConfig.DEFAULT_FAIRNESS);
        assertThat(config.isLifo()).isEqualTo(GenericObjectPoolConfig.DEFAULT_LIFO);
        assertThat(config.getMaxWaitDuration()).isEqualTo(GenericObjectPoolConfig.DEFAULT_MAX_WAIT);
        assertThat(config.getMaxTotal()).isEqualTo(GenericObjectPoolConfig.DEFAULT_MAX_TOTAL);
        assertThat(config.getMaxIdle()).isEqualTo(GenericObjectPoolConfig.DEFAULT_MAX_IDLE);
        assertThat(config.getMinIdle()).isEqualTo(GenericObjectPoolConfig.DEFAULT_MIN_IDLE);
        assertThat(config.getMinEvictableIdleDuration()).isEqualTo(GenericObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_DURATION);
        assertThat(config.getSoftMinEvictableIdleDuration()).isEqualTo(GenericObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_DURATION);
        assertThat(config.getNumTestsPerEvictionRun()).isEqualTo(GenericObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN);
        assertThat(config.isTestOnCreate()).isEqualTo(GenericObjectPoolConfig.DEFAULT_TEST_ON_CREATE);
        assertThat(config.isTestOnBorrow()).isEqualTo(GenericObjectPoolConfig.DEFAULT_TEST_ON_BORROW);
        assertThat(config.isTestOnReturn()).isEqualTo(GenericObjectPoolConfig.DEFAULT_TEST_ON_RETURN);
        assertThat(config.isTestWhileIdle()).isEqualTo(GenericObjectPoolConfig.DEFAULT_TEST_WHILE_IDLE);
    }

    @Test
    void toPoolConfig_withDefaults_shouldProduceConfig() {
        BrowserContextPoolConfig config = new BrowserContextPoolConfig();
        org.apache.commons.pool2.impl.GenericObjectPoolConfig<com.microsoft.playwright.BrowserContext> poolConfig =
                config.toPoolConfig();
        assertThat(poolConfig).isNotNull();
        // Eviction policy class name is blank-by-default -> whenHasText skips setEvictionPolicyClassName,
        // so it falls back to the commons-pool default.
        assertThat(poolConfig.getEvictionPolicyClassName())
                .isEqualTo(GenericObjectPoolConfig.DEFAULT_EVICTION_POLICY_CLASS_NAME);
    }

    @Test
    void toPoolConfig_withCustomValues_shouldApplyAll() {
        BrowserContextPoolConfig config = new BrowserContextPoolConfig();
        config.setBlockWhenExhausted(false);
        config.setDurationBetweenEvictionRuns(Duration.ofMillis(200));
        config.setEvictionPolicyClassName("org.example.MyPolicy");
        config.setEvictorShutdownTimeoutDuration(Duration.ofSeconds(10));
        config.setFairness(true);
        config.setLifo(false);
        config.setMaxWaitDuration(Duration.ofSeconds(1));
        config.setMaxTotal(8);
        config.setMaxIdle(4);
        config.setMinIdle(1);
        config.setMinEvictableIdleDuration(Duration.ofMinutes(1));
        config.setSoftMinEvictableIdleDuration(Duration.ofSeconds(30));
        config.setNumTestsPerEvictionRun(3);
        config.setTestOnCreate(true);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);
        config.setTestWhileIdle(true);

        org.apache.commons.pool2.impl.GenericObjectPoolConfig<com.microsoft.playwright.BrowserContext> poolConfig =
                config.toPoolConfig();
        assertThat(poolConfig.getBlockWhenExhausted()).isFalse();
        assertThat(poolConfig.getDurationBetweenEvictionRuns()).isEqualTo(Duration.ofMillis(200));
        assertThat(poolConfig.getEvictionPolicyClassName()).isEqualTo("org.example.MyPolicy");
        assertThat(poolConfig.getEvictorShutdownTimeoutDuration()).isEqualTo(Duration.ofSeconds(10));
        assertThat(poolConfig.getFairness()).isTrue();
        assertThat(poolConfig.getLifo()).isFalse();
        assertThat(poolConfig.getMaxWaitDuration()).isEqualTo(Duration.ofSeconds(1));
        assertThat(poolConfig.getMaxTotal()).isEqualTo(8);
        assertThat(poolConfig.getMaxIdle()).isEqualTo(4);
        assertThat(poolConfig.getMinIdle()).isEqualTo(1);
        assertThat(poolConfig.getMinEvictableIdleDuration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(poolConfig.getSoftMinEvictableIdleDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(poolConfig.getNumTestsPerEvictionRun()).isEqualTo(3);
        assertThat(poolConfig.getTestOnCreate()).isTrue();
        assertThat(poolConfig.getTestOnBorrow()).isTrue();
        assertThat(poolConfig.getTestOnReturn()).isTrue();
        assertThat(poolConfig.getTestWhileIdle()).isTrue();
    }

    @Test
    void browserContextPool_constructors_shouldAcceptNullConfig() {
        com.microsoft.playwright.spring.boot.pool.BrowserContextPooledObjectFactory noopFactory =
                new com.microsoft.playwright.spring.boot.pool.BrowserContextPooledObjectFactory(
                        new com.microsoft.playwright.spring.boot.PlaywrightProperties());
        try {
            BrowserContextPool pool1 = new BrowserContextPool(noopFactory);
            BrowserContextPool pool2 = new BrowserContextPool(noopFactory,
                    new GenericObjectPoolConfig<>());
            BrowserContextPool pool3 = new BrowserContextPool(noopFactory,
                    new GenericObjectPoolConfig<>(), new org.apache.commons.pool2.impl.AbandonedConfig());
            assertThat(pool1).isNotNull();
            assertThat(pool2).isNotNull();
            assertThat(pool3).isNotNull();
        } finally {
            try {
                noopFactory.destroy();
            } catch (Exception ignored) {
                // destroy is best-effort
            }
        }
    }
}
