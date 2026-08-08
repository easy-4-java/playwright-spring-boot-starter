package com.microsoft.playwright.spring.boot.initializer;

import com.microsoft.playwright.spring.boot.PlaywrightProperties;
import com.microsoft.playwright.spring.boot.pool.BrowserContextPool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link BrowserContextInitializer}.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class BrowserContextInitializerTest {

    @Test
    void run_withNullPool_shouldBeNoOp() {
        PlaywrightProperties props = new PlaywrightProperties();
        BrowserContextInitializer initializer = new BrowserContextInitializer(null, props);
        assertThatCode(initializer::run).doesNotThrowAnyException();
    }

    @Test
    void run_withNullProperties_shouldBeNoOp() {
        BrowserContextPool pool = mock(BrowserContextPool.class);
        BrowserContextInitializer initializer = new BrowserContextInitializer(pool, null);
        assertThatCode(initializer::run).doesNotThrowAnyException();
        verifyNoInteractions(pool);
    }

    @Test
    void run_withValidArgs_shouldPreparePool() throws Exception {
        BrowserContextPool pool = mock(BrowserContextPool.class);
        org.mockito.Mockito.doNothing().when(pool).preparePool();
        BrowserContextInitializer initializer = new BrowserContextInitializer(pool, new PlaywrightProperties());
        initializer.run();
        verify(pool, times(1)).preparePool();
    }

    @Test
    void run_whenPreparePoolThrows_shouldSwallowException() throws Exception {
        BrowserContextPool pool = mock(BrowserContextPool.class);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(pool).preparePool();
        BrowserContextInitializer initializer = new BrowserContextInitializer(pool, new PlaywrightProperties());
        assertThatCode(initializer::run).doesNotThrowAnyException();
        verify(pool).preparePool();
    }
}
