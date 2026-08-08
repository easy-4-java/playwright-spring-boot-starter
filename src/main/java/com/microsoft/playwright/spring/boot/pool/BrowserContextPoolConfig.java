package com.microsoft.playwright.spring.boot.pool;

import com.microsoft.playwright.BrowserContext;
import lombok.Data;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.context.properties.PropertyMapper;
import com.microsoft.playwright.spring.boot.properties.PropertyMapperCompat;

import java.time.Duration;

@Data
public class BrowserContextPoolConfig {

    private boolean blockWhenExhausted = GenericObjectPoolConfig.DEFAULT_BLOCK_WHEN_EXHAUSTED;

    private Duration durationBetweenEvictionRuns = GenericObjectPoolConfig.DEFAULT_DURATION_BETWEEN_EVICTION_RUNS;

    private Duration evictorShutdownTimeoutDuration = GenericObjectPoolConfig.DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT;

    private String evictionPolicyClassName = GenericObjectPoolConfig.DEFAULT_EVICTION_POLICY_CLASS_NAME;

    private boolean fairness = GenericObjectPoolConfig.DEFAULT_FAIRNESS;

    private boolean lifo = GenericObjectPoolConfig.DEFAULT_LIFO;

    private Duration maxWaitDuration = GenericObjectPoolConfig.DEFAULT_MAX_WAIT;

    private int maxTotal = GenericObjectPoolConfig.DEFAULT_MAX_TOTAL;

    private int maxIdle = GenericObjectPoolConfig.DEFAULT_MAX_IDLE;

    private int minIdle = GenericObjectPoolConfig.DEFAULT_MIN_IDLE;

    private Duration minEvictableIdleDuration = GenericObjectPoolConfig. DEFAULT_MIN_EVICTABLE_IDLE_DURATION;

    private Duration softMinEvictableIdleDuration = GenericObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_DURATION;

    private int numTestsPerEvictionRun = GenericObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    private boolean testOnCreate = GenericObjectPoolConfig.DEFAULT_TEST_ON_CREATE;

    private boolean testOnBorrow = GenericObjectPoolConfig.DEFAULT_TEST_ON_BORROW;

    private boolean testOnReturn = GenericObjectPoolConfig.DEFAULT_TEST_ON_RETURN;

    private boolean testWhileIdle = GenericObjectPoolConfig.DEFAULT_TEST_WHILE_IDLE;

    public GenericObjectPoolConfig<BrowserContext> toPoolConfig(){
        PropertyMapper map = PropertyMapperCompat.alwaysApplyingWhenNonNull();
        GenericObjectPoolConfig<BrowserContext> poolConfig = new GenericObjectPoolConfig<>();
        map.from(this.isBlockWhenExhausted()).to(poolConfig::setBlockWhenExhausted);
        map.from(this.getDurationBetweenEvictionRuns()).to(poolConfig::setTimeBetweenEvictionRuns);
        map.from(this.getEvictionPolicyClassName()).whenHasText().to(poolConfig::setEvictionPolicyClassName);
        map.from(this.getEvictorShutdownTimeoutDuration()).to(poolConfig::setEvictorShutdownTimeout);
        map.from(this.isFairness()).to(poolConfig::setFairness);
        map.from(this.isLifo()).to(poolConfig::setLifo);
        map.from(this.getMaxWaitDuration()).to(poolConfig::setMaxWait);
        map.from(this.getMaxIdle()).to(poolConfig::setMaxIdle);
        map.from(this.getMaxTotal()).to(poolConfig::setMaxTotal);
        map.from(this.getMinEvictableIdleDuration()).to(poolConfig::setMinEvictableIdleDuration);
        map.from(this.getMinIdle()).to(poolConfig::setMinIdle);
        map.from(this.getNumTestsPerEvictionRun()).to(poolConfig::setNumTestsPerEvictionRun);
        map.from(this.getSoftMinEvictableIdleDuration()).to(poolConfig::setSoftMinEvictableIdleDuration);
        map.from(this.isTestOnBorrow()).to(poolConfig::setTestOnBorrow);
        map.from(this.isTestOnCreate()).to(poolConfig::setTestOnCreate);
        map.from(this.isTestOnReturn()).to(poolConfig::setTestOnReturn);
        map.from(this.isTestWhileIdle()).to(poolConfig::setTestWhileIdle);
        return poolConfig;
    }


}
