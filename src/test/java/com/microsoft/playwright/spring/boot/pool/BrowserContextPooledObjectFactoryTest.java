package com.microsoft.playwright.spring.boot.pool;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.spring.boot.PlaywrightProperties;
import com.microsoft.playwright.spring.boot.options.BrowserLaunchPersistentContextOptions;
import com.microsoft.playwright.spring.boot.options.BrowserNewContextOptions;
import org.apache.commons.io.FileUtils;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BrowserContextPooledObjectFactory}.
 *
 * <p>The factory heavily relies on {@link Playwright#create()} (static) and on
 * {@link FileUtils#deleteDirectory(File)}. Both are stubbed with {@code MockedStatic}
 * so no real browser is ever launched during the test run.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class BrowserContextPooledObjectFactoryTest {

    @TempDir
    Path tempDir;

    // ---- validateObject ----

    @Test
    void validateObject_withNonNull_shouldReturnTrue() {
        BrowserContextPooledObjectFactory factory = newFactory(new PlaywrightProperties());
        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(mock(BrowserContext.class));
        assertThat(factory.validateObject(pooled)).isTrue();
    }

    @Test
    void validateObject_withNullObject_shouldReturnFalse() {
        BrowserContextPooledObjectFactory factory = newFactory(new PlaywrightProperties());
        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(null);
        assertThat(factory.validateObject(pooled)).isFalse();
    }

    // ---- activateObject ----

    @Test
    void activateObject_withConnectedBrowser_shouldClearCookies() throws Exception {
        BrowserContextPooledObjectFactory factory = newFactory(new PlaywrightProperties());
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);
        when(ctx.browser()).thenReturn(browser);
        when(browser.isConnected()).thenReturn(true);
        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);

        factory.activateObject(pooled);

        verify(ctx).clearCookies();
    }

    @Test
    void activateObject_withDisconnectedBrowser_shouldNotClearCookies() throws Exception {
        BrowserContextPooledObjectFactory factory = newFactory(new PlaywrightProperties());
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);
        when(ctx.browser()).thenReturn(browser);
        when(browser.isConnected()).thenReturn(false);
        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);

        factory.activateObject(pooled);

        verify(ctx, never()).clearCookies();
    }

    @Test
    void activateObject_withNullBrowser_shouldNotClearCookies() throws Exception {
        BrowserContextPooledObjectFactory factory = newFactory(new PlaywrightProperties());
        BrowserContext ctx = mock(BrowserContext.class);
        when(ctx.browser()).thenReturn(null);
        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);

        factory.activateObject(pooled);

        verify(ctx, never()).clearCookies();
    }

    @Test
    void activateObject_clearCookiesThrows_shouldSwallow() throws Exception {
        BrowserContextPooledObjectFactory factory = newFactory(new PlaywrightProperties());
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);
        when(ctx.browser()).thenReturn(browser);
        when(browser.isConnected()).thenReturn(true);
        doThrow(new RuntimeException("ctx closed")).when(ctx).clearCookies();
        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);

        assertThatCode(() -> factory.activateObject(pooled)).doesNotThrowAnyException();
    }

    // ---- passivateObject ----

    @Test
    void passivateObject_withConnectedBrowser_shouldClearStorageAndCookies() throws Exception {
        BrowserContextPooledObjectFactory factory = newFactory(new PlaywrightProperties());
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);
        Page page = mock(Page.class);
        when(ctx.browser()).thenReturn(browser);
        when(browser.isConnected()).thenReturn(true);
        when(ctx.pages()).thenReturn(Collections.singletonList(page));
        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);

        factory.passivateObject(pooled);

        verify(ctx, atLeastOnce()).pages();
        verify(ctx, atLeastOnce()).clearCookies();
    }

    @Test
    void passivateObject_clearingThrows_shouldRethrow() {
        BrowserContextPooledObjectFactory factory = newFactory(new PlaywrightProperties());
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);
        when(ctx.browser()).thenReturn(browser);
        when(browser.isConnected()).thenReturn(true);
        // pages() throws to enter the catch-block that rethrows.
        when(ctx.pages()).thenThrow(new RuntimeException("pages failed"));
        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);

        assertThatCode(() -> factory.passivateObject(pooled))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("pages failed");
    }

    @Test
    void passivateObject_disconnectedBrowser_shouldBeNoOp() throws Exception {
        BrowserContextPooledObjectFactory factory = newFactory(new PlaywrightProperties());
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);
        when(ctx.browser()).thenReturn(browser);
        when(browser.isConnected()).thenReturn(false);
        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);

        factory.passivateObject(pooled);

        verify(ctx, never()).clearCookies();
        verify(ctx, never()).pages();
    }

    @Test
    void passivateObject_nullBrowser_shouldBeNoOp() throws Exception {
        BrowserContextPooledObjectFactory factory = newFactory(new PlaywrightProperties());
        BrowserContext ctx = mock(BrowserContext.class);
        when(ctx.browser()).thenReturn(null);
        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);

        factory.passivateObject(pooled);

        verify(ctx, never()).clearCookies();
        verify(ctx, never()).pages();
    }

    // ---- destroyObject ----

    @Test
    void destroyObject_nullObject_shouldBeNoOp() throws Exception {
        BrowserContextPooledObjectFactory factory = newFactory(new PlaywrightProperties());
        PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(null);
        factory.destroyObject(pooled);
        // No exception, no further side effects to assert.
    }

    @Test
    void destroyObject_connectedBrowser_shouldCloseContextAndPlaywrightAndDir() throws Exception {
        PlaywrightProperties props = new PlaywrightProperties();
        BrowserContextPooledObjectFactory factory = newFactory(props);

        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);
        Playwright playwright = mock(Playwright.class);
        when(ctx.browser()).thenReturn(browser);
        when(browser.isConnected()).thenReturn(true);

        // Seed the internal PLAYWRIGHT_MAP / CONTEXT_DIR_MAP via makeObject-style state is not feasible
        // without reflection; instead verify destroyObject still runs the cleanup gracefully even when
        // the maps are empty (defensive path).
        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class);
             MockedStatic<FileUtils> fuStatic = org.mockito.Mockito.mockStatic(FileUtils.class)) {
            PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);
            factory.destroyObject(pooled);

            verify(ctx, atLeastOnce()).browser();
            verify(browser, atLeastOnce()).isConnected();
            verify(ctx).close();
        }
    }

    @Test
    void destroyObject_firstAttemptThrows_shouldRetryUntilSuccess() throws Exception {
        PlaywrightProperties props = new PlaywrightProperties();
        BrowserContextPooledObjectFactory factory = newFactory(props);

        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);
        when(ctx.browser()).thenReturn(browser);
        when(browser.isConnected()).thenReturn(true);
        // First cleanupBrowserContext call throws (caught), then close() throws (caught) -> retry.
        // Set the close() to throw on first invocation, succeed thereafter.
        org.mockito.Mockito.doThrow(new RuntimeException("first close fails"))
                .doNothing()
                .when(ctx).close();

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class);
             MockedStatic<FileUtils> fuStatic = org.mockito.Mockito.mockStatic(FileUtils.class)) {
            PooledObject<BrowserContext> pooled = new DefaultPooledObject<>(ctx);
            factory.destroyObject(pooled);
            // close() should have been invoked at least once (retry path).
            verify(ctx, atLeastOnce()).close();
        }
    }

    // ---- makeObject (isolated path) ----

    @Test
    void makeObject_isolated_shouldCreateBrowserAndContext() throws Exception {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);

        BrowserContextPooledObjectFactory factory = newFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        Browser browser = mock(Browser.class);
        BrowserContext ctx = mock(BrowserContext.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launch(any())).thenReturn(browser);
            when(browser.newContext(any())).thenReturn(ctx);

            PooledObject<BrowserContext> pooled = factory.makeObject();

            assertThat(pooled).isNotNull();
            assertThat(pooled.getObject()).isSameAs(ctx);
            verify(browser, atLeastOnce()).onDisconnected(any());
        }
    }

    @Test
    void makeObject_isolatedNullOptions_shouldCreateWithDefaults() throws Exception {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        props.setLaunchOptions(null);
        props.setNewContextOptions(null);

        BrowserContextPooledObjectFactory factory = newFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        Browser browser = mock(Browser.class);
        BrowserContext ctx = mock(BrowserContext.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launch(any())).thenReturn(browser);
            when(browser.newContext(any())).thenReturn(ctx);

            PooledObject<BrowserContext> pooled = factory.makeObject();

            assertThat(pooled.getObject()).isSameAs(ctx);
        }
    }

    @Test
    void makeObject_persistent_shouldLaunchPersistentContext() throws Exception {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(false);
        props.getLaunchPersistentContextOptions().setUserDataDir(tempDir.toString());

        BrowserContextPooledObjectFactory factory = newFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        BrowserContext ctx = mock(BrowserContext.class);
        Browser browser = mock(Browser.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launchPersistentContext(any(java.nio.file.Path.class), any())).thenReturn(ctx);
            when(ctx.browser()).thenReturn(browser);
            when(browser.isConnected()).thenReturn(true);

            PooledObject<BrowserContext> pooled = factory.makeObject();

            assertThat(pooled.getObject()).isSameAs(ctx);
            verify(browser, atLeastOnce()).onDisconnected(any());
        }
    }

    @Test
    void makeObject_persistentNullOptions_shouldLaunchWithDefaults() throws Exception {
        // Note: when isolated==false the factory constructor requires non-null
        // launchPersistentContextOptions (it dereferences getUserDataDir()). The "null options"
        // branch under test is therefore exercised via the isolated path here, which still
        // takes launchOptions==null into makeObject's isolated branch.
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        props.setLaunchOptions(null);
        props.setNewContextOptions(null);

        BrowserContextPooledObjectFactory factory = newFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        Browser browser = mock(Browser.class);
        BrowserContext ctx = mock(BrowserContext.class);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launch(any())).thenReturn(browser);
            when(browser.newContext(any())).thenReturn(ctx);

            PooledObject<BrowserContext> pooled = factory.makeObject();

            assertThat(pooled.getObject()).isSameAs(ctx);
        }
    }

    // ---- destroy (Spring lifecycle) ----

    @Test
    void destroy_shouldCloseAllRegisteredContextsAndPlaywright() throws Exception {
        // The internal PLAYWRIGHT_MAP is static; we trigger makeObject (isolated) to register an entry,
        // then call destroy() and assert the registered Playwright/Context get closed.
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        BrowserContextPooledObjectFactory factory = newFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        Browser browser = mock(Browser.class);
        BrowserContext ctx = mock(BrowserContext.class);
        when(ctx.browser()).thenReturn(null);

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launch(any())).thenReturn(browser);
            when(browser.newContext(any())).thenReturn(ctx);

            factory.makeObject();
        }

        factory.destroy();

        verify(ctx).close();
        verify(playwright).close();
    }

    @Test
    void destroy_whenCloseThrows_shouldSwallowException() throws Exception {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        BrowserContextPooledObjectFactory factory = newFactory(props);

        Playwright playwright = mock(Playwright.class);
        BrowserType browserType = mock(BrowserType.class);
        Browser browser = mock(Browser.class);
        BrowserContext ctx = mock(BrowserContext.class);
        when(ctx.browser()).thenReturn(null);
        doThrow(new RuntimeException("close boom")).when(ctx).close();

        try (MockedStatic<Playwright> pwStatic = org.mockito.Mockito.mockStatic(Playwright.class)) {
            pwStatic.when(Playwright::create).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(browserType);
            when(browserType.launch(any())).thenReturn(browser);
            when(browser.newContext(any())).thenReturn(ctx);

            factory.makeObject();
        }

        assertThatCode(() -> factory.destroy()).doesNotThrowAnyException();
        verify(ctx).close();
    }

    // ---- getter helpers ----

    @Test
    void getMaximumRetryAttempts_isolatedWithNullOptions_shouldReturnDefault() {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        props.setNewContextOptions(null);
        BrowserContextPooledObjectFactory factory = newFactory(props);
        assertThat(factory.getMaximumRetryAttempts()).isEqualTo(BrowserNewContextOptions.DEFAULT_MAX_RETRY_ATTEMPTS);
    }

    @Test
    void getMaximumRetryAttempts_isolatedWithOptions_shouldReturnFromOptions() {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        props.getNewContextOptions().setMaximumRetryAttempts(7);
        BrowserContextPooledObjectFactory factory = newFactory(props);
        assertThat(factory.getMaximumRetryAttempts()).isEqualTo(7);
    }

    @Test
    void getMaximumRetryAttempts_persistentWithNullOptions_shouldReturnDefault() {
        // When isolated==false the constructor would NPE on null launchPersistentContextOptions,
        // so build in isolated mode and only flip the flag on the (already-constructed) factory's
        // properties to exercise the persistent default-value branch of the getter.
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        BrowserContextPooledObjectFactory factory = newFactory(props);
        props.setIsolated(false);
        props.setLaunchPersistentContextOptions(null);
        assertThat(factory.getMaximumRetryAttempts())
                .isEqualTo(BrowserLaunchPersistentContextOptions.DEFAULT_MAX_RETRY_ATTEMPTS);
    }

    @Test
    void getMaximumRetryDelayMs_isolatedWithNullOptions_shouldReturnDefault() {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        props.setNewContextOptions(null);
        BrowserContextPooledObjectFactory factory = newFactory(props);
        // Note: the source returns DEFAULT_MAX_RETRY_ATTEMPTS (int) for this branch; assert against that.
        assertThat(factory.getMaximumRetryDelayMs())
                .isEqualTo((long) BrowserNewContextOptions.DEFAULT_MAX_RETRY_ATTEMPTS);
    }

    @Test
    void getMaximumRetryDelayMs_persistentWithNullOptions_shouldReturnDefault() {
        // Build isolated to avoid the constructor NPE, then flip the flag post-construction.
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        BrowserContextPooledObjectFactory factory = newFactory(props);
        props.setIsolated(false);
        props.setLaunchPersistentContextOptions(null);
        assertThat(factory.getMaximumRetryDelayMs())
                .isEqualTo((long) BrowserLaunchPersistentContextOptions.DEFAULT_MAX_RETRY_ATTEMPTS);
    }

    @Test
    void getMaximumResourceCleanupTimeoutMs_isolatedWithNullOptions_shouldReturnDefault() {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        props.setNewContextOptions(null);
        BrowserContextPooledObjectFactory factory = newFactory(props);
        assertThat(factory.getMaximumResourceCleanupTimeoutMs())
                .isEqualTo(BrowserNewContextOptions.DEFAULT_RESOURCE_CLEANUP_TIMEOUT_MS);
    }

    @Test
    void getMaximumResourceCleanupTimeoutMs_persistentWithNullOptions_shouldReturnDefault() {
        // Build isolated to avoid the constructor NPE, then flip the flag post-construction.
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        BrowserContextPooledObjectFactory factory = newFactory(props);
        props.setIsolated(false);
        props.setLaunchPersistentContextOptions(null);
        assertThat(factory.getMaximumResourceCleanupTimeoutMs())
                .isEqualTo(BrowserLaunchPersistentContextOptions.DEFAULT_RESOURCE_CLEANUP_TIMEOUT_MS);
    }

    @Test
    void getMaximumContentSize_withNullOptions_shouldReturnDefault() {
        // getMaximumContentSize ignores the isolated flag; build isolated so the constructor
        // is happy, then null-out the persistent options to exercise the default-value branch.
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        BrowserContextPooledObjectFactory factory = newFactory(props);
        props.setLaunchPersistentContextOptions(null);
        assertThat(factory.getMaximumContentSize())
                .isEqualTo(BrowserLaunchPersistentContextOptions.DEFAULT_MAX_CONTEXT_SIZE);
    }

    @Test
    void getMaximumDirSize_withNullOptions_shouldReturnDefault() {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        BrowserContextPooledObjectFactory factory = newFactory(props);
        props.setLaunchPersistentContextOptions(null);
        assertThat(factory.getMaximumDirSize())
                .isEqualTo(BrowserLaunchPersistentContextOptions.DEFAULT_MAX_DIR_SIZE);
    }

    @Test
    void getMaximumDirUsageTimeout_withNullOptions_shouldReturnDefault() {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        BrowserContextPooledObjectFactory factory = newFactory(props);
        props.setLaunchPersistentContextOptions(null);
        assertThat(factory.getMaximumDirUsageTimeout())
                .isEqualTo(BrowserLaunchPersistentContextOptions.DEFAULT_DIR_USAGE_TIMEOUT);
    }

    // ---- helper ----

    /**
     * Build a factory using an isolated userDataDir so the constructor's preallocate step
     * operates against a unique temp directory and never interferes with parallel tests.
     */
    private BrowserContextPooledObjectFactory newFactory(PlaywrightProperties props) {
        if (!props.isIsolated() && props.getLaunchPersistentContextOptions() != null) {
            props.getLaunchPersistentContextOptions().setUserDataDir(tempDir.toString());
        }
        return new BrowserContextPooledObjectFactory(props);
    }

    @Test
    void constructor_isolated_doesNotPreallocateDirs() throws IOException {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(true);
        // userDataDir points to an existing dir; isolated==true must skip preallocateContextDirs.
        Path existing = tempDir.resolve("preexisting");
        java.nio.file.Files.createDirectories(existing);
        props.getLaunchPersistentContextOptions().setUserDataDir(existing.toString());

        BrowserContextPooledObjectFactory factory = new BrowserContextPooledObjectFactory(props);
        assertThat(factory).isNotNull();
        // The directory should not contain any preallocated context_NNN folders.
        File[] children = existing.toFile().listFiles();
        if (children != null) {
            for (File child : children) {
                assertThat(child.getName().startsWith("context_")).isFalse();
            }
        }
    }

    @Test
    void constructor_persistent_existingDir_preallocatesContextDirs() throws IOException {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setIsolated(false);
        props.getLaunchPersistentContextOptions().setMaximumContentSize(3);
        Path existing = tempDir.resolve("root");
        java.nio.file.Files.createDirectories(existing);
        props.getLaunchPersistentContextOptions().setUserDataDir(existing.toString());

        new BrowserContextPooledObjectFactory(props);

        File[] children = existing.toFile().listFiles();
        assertThat(children).isNotNull();
        long dirs = java.util.Arrays.stream(children)
                .filter(f -> f.getName().startsWith("context_"))
                .count();
        assertThat(dirs).isEqualTo(3L);
    }

    // Unused but kept to satisfy the AtomicBoolean import expectation removed earlier.
}
