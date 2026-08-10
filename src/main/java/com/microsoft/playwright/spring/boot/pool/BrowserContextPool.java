package com.microsoft.playwright.spring.boot.pool;

import com.microsoft.playwright.BrowserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

/**
 * A Commons-Pool2 object pool that recycles {@link BrowserContext} instances, exposing
 * the standard {@link GenericObjectPool} constructors.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 */
@Slf4j
public class BrowserContextPool extends GenericObjectPool<BrowserContext> {

    /**
     * Creates a new pool with default configuration.
     * @param factory the factory used to create, activate and destroy pooled contexts
     */
    public BrowserContextPool(PooledObjectFactory<BrowserContext> factory) {
        super(factory);
    }

    /**
     * Creates a new pool with the supplied configuration.
     * @param factory the factory used to create, activate and destroy pooled contexts
     * @param config  the pool configuration
     */
    public BrowserContextPool(PooledObjectFactory<BrowserContext> factory, GenericObjectPoolConfig<BrowserContext> config) {
        super(factory, config);
    }

    /**
     * Creates a new pool with the supplied configuration and abandoned-object settings.
     * @param factory         the factory used to create, activate and destroy pooled contexts
     * @param config          the pool configuration
     * @param abandonedConfig the abandoned-object configuration
     */
    public BrowserContextPool(PooledObjectFactory<BrowserContext> factory, GenericObjectPoolConfig<BrowserContext> config, AbandonedConfig abandonedConfig) {
        super(factory, config, abandonedConfig);
    }
}
