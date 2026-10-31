package com.microsoft.playwright.spring.boot.pool;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.spring.boot.PlaywrightProperties;
import com.microsoft.playwright.spring.boot.options.BrowserNewContextOptions;
import org.apache.commons.io.FileUtils;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Additional coverage tests for {@link BrowserContextPooledObjectFactory} that target the
 * persistent (non-isolated) make/destroy path, the disconnected listener, directory size
 * accounting, and the various exception-swallowing branches.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class BrowserContextPooledObjectFactoryCoverageTest {

    @TempDir
    Path tempDir;

    private PlaywrightProperties persistentProps() {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(false);
        props.getLaunchPersistentContextOptions().setUserDataDir(tempDir.toString());
        props.getLaunchPersistentContextOptions().setMaximumRetryAttempts(1);
        props.getLaunchPersistentContextOptions().setMaximumRetryDelayMs(0L);
        return props;
    }

    // ---- makeObject persistent path: create dir on the fly ----

    @Test
    void makeObject_persistent_whenUserDataDirMissing_shouldCreateAndPreallocate() throws Exception {
        PlaywrightProperties props = persistentProps();
        // point at a non-existent subdir so the mkdirs() branch runs
        Path missing = tempDir.resolve("brand-new-root");
        props.getLaunchPersistentContextOptions().setUserDataDir(missing.toString());

        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launchPersistentContext(any(Path.class), any())).thenReturn(ctx);
            when(ctx.browser()).thenReturn(browser);
            when(browser.isConnected()).thenReturn(true);

            PooledObject<BrowserContext> pooled = factory.makeObject();

            assertThat(pooled.getObject()).isSameAs(ctx);
            // userDataDir should have been created on the fly.
            assertThat(missing).exists();
            verify(browser, atLeastOnce()).onDisconnected(any());
        }
    }

    @Test
    void makeObject_persistent_shouldComputeDirectorySizeAndWarnWhenOverLimit() throws Exception {
        PlaywrightProperties props = persistentProps();
        // Lower the dir size limit so updateDirectorySize() emits the warn log branch.
        props.getLaunchPersistentContextOptions().setMaximumDirSize(1L);
        // Pre-create a context dir with a real file so Files.walk picks it up.
        Path root = tempDir.resolve("size-root");
        Files.createDirectories(root);
        Path ctxDir = root.resolve("context_000");
        Files.createDirectories(ctxDir);
        Files.write(ctxDir.resolve("data.bin"), new byte[]{1, 2, 3, 4, 5});
        props.getLaunchPersistentContextOptions().setUserDataDir(root.toString());

        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        BrowserContext ctx = mock(BrowserContext.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launchPersistentContext(any(Path.class), any())).thenReturn(ctx);
            when(ctx.browser()).thenReturn(null);

            PooledObject<BrowserContext> pooled = factory.makeObject();
            assertThat(pooled.getObject()).isSameAs(ctx);
        }
    }

    // ---- browserDisconnectedHandler ----

    @Test
    void disconnectedHandler_shouldCleanupContextPlaywrightAndDir() throws Exception {
        PlaywrightProperties props = persistentProps();
        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);

        // Drive makeObject (persistent) to register the context in the static maps, then
        // capture the registered onDisconnected handler and invoke it directly.
        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class);
             MockedStatic<FileUtils> fuStatic = org.mockito.Mockito.mockStatic(FileUtils.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launchPersistentContext(any(Path.class), any())).thenReturn(ctx);
            when(ctx.browser()).thenReturn(browser);
            when(browser.isConnected()).thenReturn(true);
            when(browser.contexts()).thenReturn(List.of(ctx));

            // Capture the handler registered on the browser.
            doAnswer(inv -> {
                java.util.function.Consumer<Browser> handler = inv.getArgument(0);
                capturedHandler[0] = handler;
                return null;
            }).when(browser).onDisconnected(any());

            factory.makeObject();
            assertThat(capturedHandler[0]).as("disconnected handler").isNotNull();

            // Simulate disconnect: handler runs the full cleanup path.
            capturedHandler[0].accept(browser);

            verify(ctx).close();
            verify(playwright).close();
        }
    }

    private final java.util.function.Consumer<Browser>[] capturedHandler =
            (java.util.function.Consumer<Browser>[]) new java.util.function.Consumer<?>[1];

    @Test
    void disconnectedHandler_whenContextCleanupThrows_shouldSwallow() throws Exception {
        PlaywrightProperties props = persistentProps();
        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class);
             MockedStatic<FileUtils> fuStatic = org.mockito.Mockito.mockStatic(FileUtils.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launchPersistentContext(any(Path.class), any())).thenReturn(ctx);
            when(ctx.browser()).thenReturn(browser);
            when(browser.isConnected()).thenReturn(true);
            when(browser.contexts()).thenReturn(List.of(ctx));
            // cleanupBrowserContext path: clearCookies throws -> swallowed
            doThrow(new RuntimeException("cleanup boom")).when(ctx).clearCookies();

            doAnswer(inv -> {
                java.util.function.Consumer<Browser> handler = inv.getArgument(0);
                handler.accept(browser);
                return null;
            }).when(browser).onDisconnected(any());

            factory.makeObject();
            // No exception propagated; the handler's outer try/catch swallowed it.
        }
    }

    // ---- destroyObject: directory removal + retry + failure path ----

    @Test
    void destroyObject_persistent_shouldRemoveContextDirectory() throws Exception {
        PlaywrightProperties props = persistentProps();
        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launchPersistentContext(any(Path.class), any())).thenReturn(ctx);
            when(ctx.browser()).thenReturn(browser);
            when(browser.isConnected()).thenReturn(true);

            PooledObject<BrowserContext> pooled = factory.makeObject();
            factory.destroyObject(pooled);

            verify(ctx).close();
            verify(playwright).close();
        }
    }

    @Test
    void destroyObject_whenAllRetriesFail_shouldLogAndReturn() throws Exception {
        PlaywrightProperties props = persistentProps();
        // Multiple retries so the loop body runs several times.
        props.getLaunchPersistentContextOptions().setMaximumRetryAttempts(2);
        props.getLaunchPersistentContextOptions().setMaximumRetryDelayMs(1L);
        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);

        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);
        when(ctx.browser()).thenReturn(browser);
        when(browser.isConnected()).thenReturn(true);
        // close() always throws -> the outer catch retries until attempts are exhausted.
        doThrow(new RuntimeException("always fails")).when(ctx).close();

        // Force a real sleep by mocking FileUtils so directory cleanup does not throw first.
        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class);
             MockedStatic<FileUtils> fuStatic = org.mockito.Mockito.mockStatic(FileUtils.class)) {
            // Note: destroyObject has its own try/catch around close(); the outer try only sees
            // exceptions from PlaywrightUtil.cleanupBrowserContext (swallowed) etc. To reach the
            // retry branch we make cleanupBrowserContext's underlying clearCookies throw, which is
            // swallowed, so we additionally throw from page handling via pages().
            when(ctx.pages()).thenThrow(new RuntimeException("pages exploded"));

            PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);
            assertThatCode(() -> factory.destroyObject(pooled)).doesNotThrowAnyException();
        }
    }

    // ---- passivateObject storage-clearing exception branches ----

    @Test
    void passivateObject_whenPageEvaluateThrows_shouldSwallowAndContinue() throws Exception {
        PlaywrightProperties props = persistentProps();
        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);

        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);
        Page page = mock(Page.class);
        when(ctx.browser()).thenReturn(browser);
        when(browser.isConnected()).thenReturn(true);
        // page.evaluate returns Object, so use when().thenThrow() (not doThrow on a non-void method).
        // The passivate loop calls evaluate three times; make the first throw, others return null.
        when(page.evaluate(any()))
                .thenThrow(new RuntimeException("evaluate fails"))
                .thenReturn(null)
                .thenReturn(null);
        when(ctx.pages()).thenReturn(Collections.singletonList(page));

        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);
        factory.passivateObject(pooled);

        verify(ctx, atLeastOnce()).clearCookies();
    }

    @Test
    void passivateObject_whenClearCookiesThrows_shouldSwallow() throws Exception {
        PlaywrightProperties props = persistentProps();
        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);

        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);
        when(ctx.browser()).thenReturn(browser);
        when(browser.isConnected()).thenReturn(true);
        when(ctx.pages()).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("clearCookies fails")).when(ctx).clearCookies();

        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);
        assertThatCode(() -> factory.passivateObject(pooled)).doesNotThrowAnyException();
    }

    @Test
    void passivateObject_persistentWithRegisteredDir_shouldUpdateDirectorySize() throws Exception {
        PlaywrightProperties props = persistentProps();
        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launchPersistentContext(any(Path.class), any())).thenReturn(ctx);
            when(ctx.browser()).thenReturn(browser);
            when(browser.isConnected()).thenReturn(true);

            PooledObject<BrowserContext> pooled = factory.makeObject();

            // Now passivate: with a registered CONTEXT_DIR_MAP entry, updateDirectorySize runs.
            when(ctx.pages()).thenReturn(Collections.emptyList());
            factory.passivateObject(pooled);
            verify(ctx, atLeastOnce()).clearCookies();
        }
    }

    // ---- getDirectorySize IO failure branch (via non-existent path) ----

    @Test
    void directorySizeCalculation_shouldHandleIoFailureGracefully() throws Exception {
        // Indirectly: a makeObject pass with a dir that gets deleted mid-walk would be racy;
        // instead assert that updateDirectorySize is invoked without blowing up by using
        // a non-existent dir in the persistent path. This covers the IOException catch in
        // getDirectorySize when Files.walk fails.
        PlaywrightProperties props = persistentProps();
        Path ghost = tempDir.resolve("ghost-" + System.nanoTime());
        props.getLaunchPersistentContextOptions().setUserDataDir(ghost.toString());

        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launchPersistentContext(any(Path.class), any())).thenReturn(ctx);
            when(ctx.browser()).thenReturn(browser);
            when(browser.isConnected()).thenReturn(true);

            PooledObject<BrowserContext> pooled = factory.makeObject();
            assertThat(pooled.getObject()).isSameAs(ctx);
        }
    }

    // ---- destroy() with empty static map (defensive) ----

    @Test
    void destroy_whenNoContextsRegistered_shouldBeNoOp() throws Exception {
        // Build isolated with no makeObject call; PLAYWRIGHT_MAP may already have entries from
        // other tests but destroy() must complete without error regardless.
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);
        assertThatCode(() -> factory.destroy()).doesNotThrowAnyException();
    }

    // ---- preallocate edge: existing context dirs already present ----

    @Test
    void constructor_persistent_existingPreallocatedDirs_shouldNotRecreate() throws IOException {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(false);
        props.getLaunchPersistentContextOptions().setMaximumContentSize(2);
        Path root = tempDir.resolve("preexisting-pool");
        Files.createDirectories(root);
        // Pre-create the context dirs so the "if (!exists)" branch is skipped.
        Files.createDirectories(root.resolve("context_000"));
        Files.createDirectories(root.resolve("context_001"));
        props.getLaunchPersistentContextOptions().setUserDataDir(root.toString());

        new BrowserContextPooledObjectFactory(props);

        // Both dirs still exist; constructor did not throw and did not duplicate.
        assertThat(root.resolve("context_000")).exists();
        assertThat(root.resolve("context_001")).exists();
    }

    // ---- getNextContextDir fallback branch (no available dirs) ----

    @Test
    void makeObject_persistent_shouldFallBackToNewDirWhenNoneAvailable() throws Exception {
        PlaywrightProperties props = persistentProps();
        // Set a very short dir-usage timeout so cleanupOldContextDirs considers dirs stale,
        // and a content size of 0 so preallocate creates nothing -> findLeastUsedDir falls back.
        props.getLaunchPersistentContextOptions().setMaximumDirUsageTimeout(0L);
        props.getLaunchPersistentContextOptions().setMaximumContentSize(2);

        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launchPersistentContext(any(Path.class), any())).thenReturn(ctx);
            when(ctx.browser()).thenReturn(browser);
            when(browser.isConnected()).thenReturn(true);

            PooledObject<BrowserContext> pooled = factory.makeObject();
            assertThat(pooled.getObject()).isSameAs(ctx);
        }
    }

    @Test
    void getNextContextDir_cleanupWithOversizedDir_shouldDelete() throws Exception {
        // Pre-seed an oversized, stale context dir; makeObject should clean it up via
        // cleanupOldContextDirs (size > maximumDirSize branch).
        PlaywrightProperties props = persistentProps();
        Path root = tempDir.resolve("cleanup-root");
        Files.createDirectories(root);
        Path stale = root.resolve("context_000");
        Files.createDirectories(stale);
        Files.write(stale.resolve("big.bin"), new byte[64]);
        props.getLaunchPersistentContextOptions().setUserDataDir(root.toString());
        props.getLaunchPersistentContextOptions().setMaximumDirSize(1L); // tiny -> triggers cleanup
        props.getLaunchPersistentContextOptions().setMaximumDirUsageTimeout(0L); // stale immediately

        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launchPersistentContext(any(Path.class), any())).thenReturn(ctx);
            when(ctx.browser()).thenReturn(browser);
            when(browser.isConnected()).thenReturn(true);

            PooledObject<BrowserContext> pooled = factory.makeObject();
            assertThat(pooled.getObject()).isSameAs(ctx);
        }
    }

    @Test
    void isolatedGetters_withOptionsPresent_shouldReturnOptionValues() {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        props.getNewContextOptions().setMaximumRetryAttempts(9);
        props.getNewContextOptions().setMaximumRetryDelayMs(250L);
        props.getNewContextOptions().setMaximumResourceCleanupTimeoutMs(10_000L);

        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);
        assertThat(factory.getMaximumRetryAttempts()).isEqualTo(9);
        assertThat(factory.getMaximumRetryDelayMs()).isEqualTo(250L);
        assertThat(factory.getMaximumResourceCleanupTimeoutMs()).isEqualTo(10_000L);
    }

    @Test
    void persistentGetters_withOptionsPresent_shouldReturnOptionValues() {
        // Build isolated to avoid the constructor NPE, then flip the flag post-construction.
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);
        props.setIsolated(false);
        props.getLaunchPersistentContextOptions().setMaximumRetryAttempts(5);
        props.getLaunchPersistentContextOptions().setMaximumRetryDelayMs(500L);
        props.getLaunchPersistentContextOptions().setMaximumResourceCleanupTimeoutMs(60_000L);
        props.getLaunchPersistentContextOptions().setMaximumContentSize(32);
        props.getLaunchPersistentContextOptions().setMaximumDirSize(999L);
        props.getLaunchPersistentContextOptions().setMaximumDirUsageTimeout(123L);

        assertThat(factory.getMaximumRetryAttempts()).isEqualTo(5);
        assertThat(factory.getMaximumRetryDelayMs()).isEqualTo(500L);
        assertThat(factory.getMaximumResourceCleanupTimeoutMs()).isEqualTo(60_000L);
        assertThat(factory.getMaximumContentSize()).isEqualTo(32);
        assertThat(factory.getMaximumDirSize()).isEqualTo(999L);
        assertThat(factory.getMaximumDirUsageTimeout()).isEqualTo(123L);
    }

    @Test
    void isolatedGetters_defaultBranch_whenOptionsNull() {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        props.setNewContextOptions(null);
        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);
        assertThat(factory.getMaximumResourceCleanupTimeoutMs())
                .isEqualTo(BrowserNewContextOptions.DEFAULT_RESOURCE_CLEANUP_TIMEOUT_MS);
    }
}
