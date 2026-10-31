package com.microsoft.playwright.spring.boot.pool;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.spring.boot.PlaywrightProperties;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real headless Chromium regressions: no user profile or network navigation. */
class BrowserContextPoolLifecycleTest {
    @TempDir Path temporaryProfile;

    private BrowserContextPooledObjectFactory factory() {
        PlaywrightProperties properties = new PlaywrightProperties();
        properties.setIsolated(true);
        properties.getLaunchOptions().setChannel("chrome");
        properties.getLaunchOptions().setHeadless(true);
        return new BrowserContextPooledObjectFactory(properties);
    }

    private Browser launch(Playwright playwright) {
        return playwright.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome").setHeadless(true));
    }

    private BrowserContextPool pool() {
        GenericObjectPoolConfig<BrowserContext> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(1);
        config.setMaxIdle(1);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);
        config.setJmxEnabled(false);
        return new BrowserContextPool(factory(), config);
    }

    @Test
    void validationRejectsClosedContextWithoutClosingSharedBrowser() throws Exception {
        try (Playwright playwright = Playwright.create(); Browser browser = launch(playwright)) {
            BrowserContext context = browser.newContext();
            BrowserContext sibling = browser.newContext();
            BrowserContextPooledObjectFactory factory = factory();
            Page existingPage = context.newPage();
            context.addCookies(List.of(new Cookie("fixture", "value").setUrl("https://example.invalid")));
            assertTrue(factory.validateObject(new DefaultPooledObject<>(context)));
            assertFalse(existingPage.isClosed());
            assertEquals(1, context.cookies().size());
            context.close();
            assertTrue(browser.isConnected());
            assertFalse(factory.validateObject(new DefaultPooledObject<>(context)));
            factory.destroyObject(new DefaultPooledObject<>(context));
            assertEquals("alive", sibling.newPage().evaluate("() => 'alive'"));
            sibling.close();
        }
    }

    @Test
    void validationRejectsDisconnectedBrowser() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = launch(playwright);
            BrowserContext context = browser.newContext();
            browser.close();
            assertFalse(browser.isConnected());
            assertFalse(factory().validateObject(new DefaultPooledObject<>(context)));
        }
    }

    @Test
    void activationPropagatesClosedContextFailure() {
        try (Playwright playwright = Playwright.create(); Browser browser = launch(playwright)) {
            BrowserContext context = browser.newContext();
            context.close();
            assertThrows(Exception.class, () -> factory().activateObject(new DefaultPooledObject<>(context)));
        }
    }

    @Test
    void passivationPropagatesDisconnectedContextFailure() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = launch(playwright);
            BrowserContext context = browser.newContext();
            browser.close();
            assertThrows(Exception.class, () -> factory().passivateObject(new DefaultPooledObject<>(context)));
        }
    }

    @Test
    void borrowDestroysAndReplacesContextClosedWhileIdle() throws Exception {
        try (BrowserContextPool pool = pool()) {
            BrowserContext first = pool.borrowObject();
            pool.returnObject(first);
            first.close();
            BrowserContext replacement = pool.borrowObject();
            try {
                assertNotSame(first, replacement);
                assertEquals(1, pool.getDestroyedCount());
                assertEquals("usable", replacement.newPage().evaluate("() => 'usable'"));
            } finally {
                pool.returnObject(replacement);
            }
        }
    }

    @Test
    void returnDestroysClosedContextRatherThanKeepingItIdle() throws Exception {
        try (BrowserContextPool pool = pool()) {
            BrowserContext first = pool.borrowObject();
            first.close();
            pool.returnObject(first);
            assertEquals(0, pool.getNumIdle());
            assertEquals(1, pool.getDestroyedCount());
            BrowserContext replacement = pool.borrowObject();
            try {
                assertNotSame(first, replacement);
                assertEquals("usable", replacement.newPage().evaluate("() => 'usable'"));
            } finally {
                pool.returnObject(replacement);
            }
        }
    }

    @Test
    void passivationFailureDestroysContextEvenWithoutReturnValidation() throws Exception {
        try (BrowserContextPool pool = pool()) {
            pool.setTestOnReturn(false);
            BrowserContext first = pool.borrowObject();
            first.close();
            pool.returnObject(first);
            assertEquals(0, pool.getNumIdle());
            assertEquals(1, pool.getDestroyedCount());
            BrowserContext replacement = pool.borrowObject();
            try {
                assertNotSame(first, replacement);
                assertEquals("usable", replacement.newPage().evaluate("() => 'usable'"));
            } finally {
                pool.returnObject(replacement);
            }
        }
    }

    @Test
    void liveBorrowReturnReusesContextAndClearsPagesAndCookies() throws Exception {
        try (BrowserContextPool pool = pool()) {
            BrowserContext first = pool.borrowObject();
            Page page = first.newPage();
            page.setContent("<h1>local fixture</h1>");
            first.addCookies(List.of(new Cookie("fixture", "value").setUrl("https://example.invalid")));
            pool.returnObject(first);
            assertTrue(page.isClosed());
            BrowserContext again = pool.borrowObject();
            try {
                assertSame(first, again);
                assertTrue(again.cookies().isEmpty());
                assertTrue(again.pages().isEmpty());
                assertEquals(0, pool.getDestroyedCount());
            } finally {
                pool.returnObject(again);
            }
        }
    }

    @Test
    void shutdownClosesOwnedDriverEvenWhenContextWasAlreadyClosed() throws Exception {
        BrowserContextPooledObjectFactory factory = factory();
        BrowserContext context = factory.makeObject().getObject();
        Browser ownedBrowser = context.browser();
        try {
            context.close();
            assertTrue(ownedBrowser.isConnected());
            factory.destroy();
            // Playwright.close() closes the transport, but isConnected() is a cached
            // event flag in Playwright 1.48. Probe the protocol, not that stale flag.
            PlaywrightException closed = assertThrows(PlaywrightException.class, () -> {
                try (BrowserContext unexpected = ownedBrowser.newContext()) {
                    fail("Owned driver still accepts contexts after shutdown");
                }
            });
            assertEquals("Playwright connection closed", closed.getMessage());
            factory.destroy();
        } finally {
            factory.destroyObject(new DefaultPooledObject<>(context));
        }
    }

    @Test
    void persistentContextLifecycleWorksWithTemporaryProfile() throws Exception {
        try (Playwright playwright = Playwright.create()) {
            BrowserContext context = playwright.chromium().launchPersistentContext(temporaryProfile,
                    new BrowserType.LaunchPersistentContextOptions().setChannel("chrome").setHeadless(true));
            try {
                BrowserContextPooledObjectFactory factory = factory();
                DefaultPooledObject<BrowserContext> pooled = new DefaultPooledObject<>(context);
                assertTrue(factory.validateObject(pooled));
                context.addCookies(List.of(new Cookie("fixture", "value").setUrl("https://example.invalid")));
                factory.activateObject(pooled);
                assertTrue(context.cookies().isEmpty());
                factory.passivateObject(pooled);
                assertTrue(context.pages().isEmpty());
                context.close();
                assertFalse(factory.validateObject(pooled));
            } finally {
                context.close();
            }
        }
    }
}

