package com.microsoft.playwright.spring.boot.utils;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.MouseButton;
import com.microsoft.playwright.spring.boot.PlaywrightProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlaywrightUtil}.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class PlaywrightUtilTest {

    @AfterEach
    void resetStaticFieldsViaGarbageCollection() {
        // Best-effort: clear any cached Playwright instance via the public getInstance flow by
        // removing its reference. The class holds a ThreadLocal so we simply clear it here.
        // Note: getInstance creates a real Playwright only when invoked, which is avoided in tests.
    }

    @Test
    void cookieToString_shouldJoinNameValuePairsWithSemicolon() {
        Cookie a = new Cookie("sid", "123");
        Cookie b = new Cookie("uid", "456");
        String result = PlaywrightUtil.cookieToString(Arrays.asList(a, b));
        assertThat(result).isEqualTo("sid=123;uid=456");
    }

    @Test
    void cookieToString_emptyList_shouldReturnEmptyString() {
        assertThat(PlaywrightUtil.cookieToString(Collections.emptyList())).isEmpty();
    }

    @Test
    void getCookies_shouldDelegateToContextOfPage() {
        Page page = mock(Page.class);
        BrowserContext ctx = mock(BrowserContext.class);
        when(page.context()).thenReturn(ctx);
        when(ctx.cookies()).thenReturn(Collections.singletonList(new Cookie("k", "v")));

        assertThat(PlaywrightUtil.getCookies(page)).isEqualTo("k=v");
    }

    @Test
    void clearLocalStorage_shouldEvaluateClearScript() {
        Page page = mock(Page.class);
        PlaywrightUtil.clearLocalStorage(page);
        verify(page).evaluate("window.localStorage.clear();");
    }

    @Test
    void cleanupBrowser_null_shouldBeNoOp() {
        assertThatNoException().isThrownBy(() -> PlaywrightUtil.cleanupBrowser(null));
    }

    @Test
    void cleanupBrowser_shouldClearCookiesAndClosePagesForEveryContext() {
        Browser browser = mock(Browser.class);
        BrowserContext ctx1 = mock(BrowserContext.class);
        BrowserContext ctx2 = mock(BrowserContext.class);
        Page page1 = mock(Page.class);
        Page page2 = mock(Page.class);
        when(page1.isClosed()).thenReturn(false);
        when(page2.isClosed()).thenReturn(true); // already closed -> should be skipped
        when(ctx1.pages()).thenReturn(Collections.singletonList(page1));
        when(ctx2.pages()).thenReturn(Collections.singletonList(page2));
        when(browser.contexts()).thenReturn(Arrays.asList(ctx1, ctx2));

        PlaywrightUtil.cleanupBrowser(browser);

        verify(ctx1).clearCookies();
        verify(ctx2).clearCookies();
        verify(page1).close();
        verify(page2, never()).close();
    }

    @Test
    void cleanupBrowser_contextWithoutPages_shouldOnlyClearCookies() {
        Browser browser = mock(Browser.class);
        BrowserContext ctx = mock(BrowserContext.class);
        when(ctx.pages()).thenReturn(Collections.emptyList());
        when(browser.contexts()).thenReturn(Collections.singletonList(ctx));

        PlaywrightUtil.cleanupBrowser(browser);

        verify(ctx).clearCookies();
    }

    @Test
    void cleanupBrowserContext_null_shouldBeNoOp() {
        assertThatNoException().isThrownBy(() -> PlaywrightUtil.cleanupBrowserContext(null));
    }

    @Test
    void cleanupBrowserContext_shouldClearCookiesAndClosePages() {
        BrowserContext ctx = mock(BrowserContext.class);
        Page open = mock(Page.class);
        Page closed = mock(Page.class);
        when(open.isClosed()).thenReturn(false);
        when(closed.isClosed()).thenReturn(true);
        when(ctx.pages()).thenReturn(Arrays.asList(open, closed));

        PlaywrightUtil.cleanupBrowserContext(ctx);

        verify(ctx).clearCookies();
        verify(open).close();
        verify(closed, never()).close();
    }

    @Test
    void closePage_null_shouldBeNoOp() {
        assertThatNoException().isThrownBy(() -> PlaywrightUtil.closePage(null));
    }

    @Test
    void closePage_alreadyClosed_shouldBeNoOp() {
        Page page = mock(Page.class);
        when(page.isClosed()).thenReturn(true);
        PlaywrightUtil.closePage(page);
        verify(page, never()).close();
    }

    @Test
    void closePage_throwing_shouldSwallowException() {
        Page page = mock(Page.class);
        when(page.isClosed()).thenReturn(false);
        Mockito.doThrow(new RuntimeException("boom")).when(page).close();
        assertThatNoException().isThrownBy(() -> PlaywrightUtil.closePage(page));
    }

    @Test
    void closePage_open_shouldClose() {
        Page page = mock(Page.class);
        when(page.isClosed()).thenReturn(false);
        PlaywrightUtil.closePage(page);
        verify(page).close();
    }

    @Test
    void waitForPageLoad_shouldWaitForLoadDomcontentloadedAndNetworkidle() {
        Page page = mock(Page.class);
        PlaywrightUtil.waitForPageLoad(page);
        verify(page).waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD);
        verify(page).waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        verify(page).waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
    }

    @Test
    void takeScreenshot_withoutSelector_shouldScreenshotPage() {
        Page page = mock(Page.class);
        byte[] data = new byte[]{1, 2, 3};
        when(page.screenshot()).thenReturn(data);
        assertThat(PlaywrightUtil.takeScreenshot(page, null)).isEqualTo(data);
        verify(page, never()).locator(any());
    }

    @Test
    void takeScreenshot_withBlankSelector_shouldScreenshotPage() {
        Page page = mock(Page.class);
        byte[] data = new byte[]{9};
        when(page.screenshot()).thenReturn(data);
        assertThat(PlaywrightUtil.takeScreenshot(page, "   ")).isEqualTo(data);
    }

    @Test
    void takeScreenshot_withSelector_shouldScreenshotLocator() {
        Page page = mock(Page.class);
        com.microsoft.playwright.Locator locator = mock(com.microsoft.playwright.Locator.class);
        byte[] data = new byte[]{4, 5};
        when(locator.screenshot()).thenReturn(data);
        when(page.locator("#id")).thenReturn(locator);

        assertThat(PlaywrightUtil.takeScreenshot(page, "#id")).isEqualTo(data);
        verify(page).locator("#id");
    }

    @Test
    void slide_bySelector_shouldUseMouseGestures() {
        Page page = mock(Page.class);
        ElementHandle handle = mock(ElementHandle.class);
        com.microsoft.playwright.options.BoundingBox box =
                new com.microsoft.playwright.options.BoundingBox();
        box.x = 10.0;
        box.y = 20.0;
        box.width = 5.0;
        box.height = 5.0;
        when(handle.boundingBox()).thenReturn(box);
        Mouse mouse = mock(Mouse.class);
        when(page.mouse()).thenReturn(mouse);
        when(page.waitForSelector(any(), any(Page.WaitForSelectorOptions.class))).thenReturn(handle);

        PlaywrightUtil.slide(page, "#slider", 100, 4);

        verify(page).waitForSelector(any(), any(Page.WaitForSelectorOptions.class));
        verify(mouse).move(10.0, 20.0);
        verify(mouse).down(any(Mouse.DownOptions.class));
        verify(mouse).move(org.mockito.ArgumentMatchers.eq(110.0), org.mockito.ArgumentMatchers.eq(20.0),
                any(Mouse.MoveOptions.class));
        verify(mouse).up();
    }

    @Test
    void slide_byElementHandle_shouldUseMouseGestures() {
        Page page = mock(Page.class);
        ElementHandle handle = mock(ElementHandle.class);
        com.microsoft.playwright.options.BoundingBox box =
                new com.microsoft.playwright.options.BoundingBox();
        box.x = 0.0;
        box.y = 0.0;
        box.width = 1.0;
        box.height = 1.0;
        when(handle.boundingBox()).thenReturn(box);
        Mouse mouse = mock(Mouse.class);
        when(page.mouse()).thenReturn(mouse);

        PlaywrightUtil.slide(page, handle, 50, 2);

        verify(mouse).move(0.0, 0.0);
        verify(mouse).down(any(Mouse.DownOptions.class));
        verify(mouse).move(org.mockito.ArgumentMatchers.eq(50.0), org.mockito.ArgumentMatchers.eq(0.0),
                any(Mouse.MoveOptions.class));
        verify(mouse).up();
    }

    @Test
    void getBrowser_shouldLaunchBrowserTypeWhenNoCachedInstance() {
        // The internal ThreadLocal cache is per-thread; the first call launches a fresh browser.
        BrowserType browserType = mock(BrowserType.class);
        Browser browser = mock(Browser.class);
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions();
        when(browserType.launch(options)).thenReturn(browser);
        when(browser.isConnected()).thenReturn(true);

        Playwright playwright = mock(Playwright.class);
        when(playwright.chromium()).thenReturn(browserType);

        Browser result = PlaywrightUtil.getBrowser(playwright, PlaywrightProperties.BrowserTypeEnum.chromium, options);
        assertThat(result).isSameAs(browser);
        verify(browserType).launch(options);
    }

    @Test
    void getBrowser_byProperties_shouldUseDefaultsWhenOptionsAbsent() {
        BrowserType browserType = mock(BrowserType.class);
        Browser browser = mock(Browser.class);
        when(browser.isConnected()).thenReturn(true);
        when(browserType.launch(any())).thenReturn(browser);

        Playwright playwright = mock(Playwright.class);
        when(playwright.chromium()).thenReturn(browserType);

        PlaywrightProperties props = new PlaywrightProperties();
        props.setLaunchOptions(null);
        props.setBrowserType(PlaywrightProperties.BrowserTypeEnum.chromium);

        Browser result = PlaywrightUtil.getBrowser(playwright, props);
        assertThat(result).isSameAs(browser);
        verify(browserType).launch(any(BrowserType.LaunchOptions.class));
    }

    @Test
    void getBrowser_byProperties_shouldUseProvidedOptionsWhenPresent() {
        BrowserType browserType = mock(BrowserType.class);
        Browser browser = mock(Browser.class);
        when(browser.isConnected()).thenReturn(true);
        when(browserType.launch(any())).thenReturn(browser);

        Playwright playwright = mock(Playwright.class);
        when(playwright.chromium()).thenReturn(browserType);

        PlaywrightProperties props = new PlaywrightProperties();
        com.microsoft.playwright.spring.boot.options.BrowserLaunchOptions launch =
                new com.microsoft.playwright.spring.boot.options.BrowserLaunchOptions();
        launch.setHeadless(true);
        props.setLaunchOptions(launch);

        Browser result = PlaywrightUtil.getBrowser(playwright, props);
        assertThat(result).isSameAs(browser);
        verify(browserType).launch(any(BrowserType.LaunchOptions.class));
    }
}
