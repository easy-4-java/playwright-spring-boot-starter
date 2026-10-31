package com.microsoft.playwright.spring.boot;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.spring.boot.pool.BrowserContextPool;
import com.microsoft.playwright.spring.boot.pool.BrowserContextPooledObjectFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaywrightStarterPoolTest {

    @Test
    void bindsConfigurationAndClearsBorrowedContextOnReturn() {
        new ApplicationContextRunner()
            .withUserConfiguration(PlaywrightAutoConfiguration.class)
            .withPropertyValues(
                "playwright.isolated=true",
                "playwright.launch-options.channel=chrome",
                "playwright.launch-options.headless=true",
                "playwright.browser-context-pool.max-total=1",
                "playwright.browser-context-pool.max-idle=1",
                "playwright.browser-context-pool.min-idle=0",
                "playwright.browser-context-pool.test-on-borrow=true",
                "playwright.browser-context-pool.test-on-return=true",
                "playwright.memory-threshold=0.7")
            .run(context -> {
                assertNull(context.getStartupFailure());
                PlaywrightProperties properties = context.getBean(PlaywrightProperties.class);
                assertTrue(properties.isIsolated());
                assertEquals(0.7, properties.getMemoryThreshold());
                assertEquals("chrome", properties.getLaunchOptions().getChannel());
                assertEquals(1, properties.getBrowserContextPool().getMaxTotal());
                assertNotNull(context.getBean(BrowserContextPooledObjectFactory.class));

                BrowserContextPool pool = context.getBean(BrowserContextPool.class);
                try {
                    exercisePool(pool);
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });
    }

    private static void exercisePool(BrowserContextPool pool) throws Exception {
                assertEquals(0, pool.getNumActive());
                BrowserContext borrowed = pool.borrowObject();
                assertNotNull(borrowed);
                Page page = borrowed.newPage();
                page.setContent("<p>local fixture</p>");
                assertEquals("local fixture", page.locator("p").textContent());
                pool.returnObject(borrowed);
                assertEquals(0, pool.getNumActive());
                assertEquals(1, pool.getNumIdle());

                BrowserContext reused = pool.borrowObject();
                assertEquals(borrowed, reused);
                assertTrue(reused.pages().isEmpty(), "Pages from the previous borrower must be closed");
                assertTrue(reused.cookies().isEmpty(), "Cookies from the previous borrower must be cleared");
                pool.returnObject(reused);
    }
}
