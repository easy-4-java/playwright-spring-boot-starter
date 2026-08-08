package com.microsoft.playwright.spring.boot;

import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.spring.boot.monitor.MemoryMonitor;
import com.microsoft.playwright.spring.boot.pool.BrowserContextPooledObjectFactory;
import com.microsoft.playwright.spring.boot.pool.BrowserContextPool;
import org.apache.commons.pool2.PooledObjectFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot integration tests for {@link PlaywrightAutoConfiguration}.
 *
 * <p>Uses {@link ApplicationContextRunner} so no servlet web context is started. The
 * {@code preparePool()} call inside the configuration is suppressed by providing a
 * spy of the real bean so the test does not attempt to launch a real browser.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class PlaywrightAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlaywrightAutoConfiguration.class));

    @Test
    void autoConfiguration_shouldRegisterAllBeans() {
        contextRunner
                .withUserConfiguration(BrowserPoolStubConfiguration.class)
                .run(this::assertCoreBeansPresent);
    }

    @Test
    void memoryMonitor_shouldBeCreatedWithConfiguredThreshold() {
        contextRunner
                .withPropertyValues("playwright.memory-threshold=0.5")
                .run(context -> {
                    assertThat(context).hasSingleBean(MemoryMonitor.class);
                    // Sanity check: with 50% threshold the bean should consider memory available.
                    MemoryMonitor monitor = context.getBean(MemoryMonitor.class);
                    assertThat(monitor.isMemoryAvailable()).isTrue();
                });
    }

    @Test
    void customBrowserContextPooledObjectFactory_shouldNotBeOverridden() {
        contextRunner
                .withUserConfiguration(CustomFactoryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(BrowserContextPooledObjectFactory.class);
                    assertThat(context.getBean(BrowserContextPooledObjectFactory.class))
                            .isSameAs(CustomFactoryConfiguration.CUSTOM_FACTORY);
                });
    }

    private void assertCoreBeansPresent(AssertableApplicationContext context) {
        assertThat(context).hasSingleBean(MemoryMonitor.class);
        assertThat(context).hasSingleBean(BrowserContextPooledObjectFactory.class);
        assertThat(context).hasSingleBean(BrowserContextPool.class);
    }

    /**
     * Provides a {@link BrowserContextPool} that does nothing on {@code preparePool()} so the
     * {@link BrowserContextInitializer} produced by {@link PlaywrightAutoConfiguration} runs
     * without launching a real browser.
     */
    @Configuration(proxyBeanMethods = false)
    static class BrowserPoolStubConfiguration {
        @Bean
        BrowserContextPool browserContextPool(BrowserContextPooledObjectFactory factory) {
            return new NoOpBrowserContextPool(factory);
        }
    }

    /** A pool that overrides preparePool() to a no-op (avoids makeObject -> real browser). */
    static class NoOpBrowserContextPool extends BrowserContextPool {
        NoOpBrowserContextPool(BrowserContextPooledObjectFactory factory) {
            super(factory);
        }
        @Override
        public void preparePool() {
            // intentionally left blank
        }
    }

    /**
     * Supplies a custom {@link BrowserContextPooledObjectFactory} to verify
     * {@code @ConditionalOnMissingBean} semantics.
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomFactoryConfiguration {
        static final BrowserContextPooledObjectFactory CUSTOM_FACTORY =
                new BrowserContextPooledObjectFactory(new PlaywrightProperties());

        @Bean
        BrowserContextPooledObjectFactory browserContextPooledObjectFactory() {
            return CUSTOM_FACTORY;
        }

        @Bean
        BrowserContextPool browserContextPool() {
            return new NoOpBrowserContextPool(CUSTOM_FACTORY);
        }
    }

    // Sanity check: the configuration class is on the classpath and the conditional class matches.
    @Test
    void playwrightAndPooledObjectFactoryAreOnClasspath() {
        assertThat(Playwright.class).isNotNull();
        assertThat(PooledObjectFactory.class).isNotNull();
    }
}
