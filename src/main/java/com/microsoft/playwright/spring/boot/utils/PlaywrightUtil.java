package com.microsoft.playwright.spring.boot.utils;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.MouseButton;
import com.microsoft.playwright.spring.boot.PlaywrightProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Static utility helpers for managing Playwright {@link Playwright} and {@link Browser}
 * instances (thread-local lifecycle, browser launch, cookie/localStorage handling,
 * slider drag, page-load waiting, screenshot capture and cleanup).
 *
 * @author [@Loong Wan](https://github.com/loong10k)
 * @since 1.0.0
 */
@Slf4j
public class PlaywrightUtil {

    private static final String TOKEN_SPLITTER = ";";
    private static ThreadLocal<Browser> browserInstance = new TransmittableThreadLocal<Browser>();
    private static ThreadLocal<Playwright> playwrightInstance = new TransmittableThreadLocal<>();

    /**
     * Returns the thread-local {@link Playwright} instance, creating it lazily.
     * @return the thread-local Playwright instance
     */
    public static synchronized Playwright getInstance() {
        Playwright playwright = playwrightInstance.get();
        if(Objects.isNull(playwright)){
            log.info("Create Playwright Instance .");
            playwright = Playwright.create();
            playwrightInstance.set(playwright);
            log.info("Playwright instance created.");
        }
        return playwright;
    }

    /**
     * Returns a thread-local {@link Browser} launched with the given options, creating
     * it lazily and reusing the cached instance while it is still connected.
     * @param playwright       the Playwright instance used to launch the browser
     * @param browserTypeEnum  the browser type to launch
     * @param launchOptions    the options applied when launching a new browser
     * @return the thread-local Browser instance
     */
    public static Browser getBrowser(Playwright playwright, PlaywrightProperties.BrowserTypeEnum browserTypeEnum, BrowserType.LaunchOptions launchOptions) {
        log.info("Get Browser Instance .");
        Browser browser = browserInstance.get();
        if (Objects.nonNull(browser) && !browser.isConnected()) {
            return browser;
        }
        BrowserType browserType = browserTypeEnum.getBrowserType(playwright);
        browser = browserType.launch(launchOptions);
        log.info("Create Browser Instance .");
        browserInstance.set(browser);
        return browser;
    }

    /**
     * Returns a thread-local {@link Browser} launched from the supplied properties,
     * falling back to headless Chromium when no options are configured.
     * @param playwright          the Playwright instance used to launch the browser
     * @param playwrightProperties the configuration properties driving the launch
     * @return the thread-local Browser instance
     */
    public static Browser getBrowser(Playwright playwright, PlaywrightProperties playwrightProperties) {
        // Browser Type
        PlaywrightProperties.BrowserTypeEnum browserType = Objects.nonNull(playwrightProperties.getBrowserType()) ? playwrightProperties.getBrowserType() : PlaywrightProperties.BrowserTypeEnum.chromium;
        // Get Browser Launch Options
        BrowserType.LaunchOptions launchOptions = Objects.nonNull(playwrightProperties.getLaunchOptions()) ? playwrightProperties.getLaunchOptions().toOptions() : new BrowserType.LaunchOptions().setHeadless(true);
        // Get Browser
        return getBrowser(playwright, browserType, launchOptions);
    }

    /**
     * Releases the resources held by the given browser, clearing cookies and closing
     * every page of every context it owns.
     * @param browser the browser to clean up (may be {@code null})
     */
    public static void cleanupBrowser(Browser browser) {
        if (Objects.isNull(browser)) {
            return;
        }
        browser.contexts().forEach(context -> {
            List<Page> pages = context.pages();
            if (Objects.nonNull(pages) && !pages.isEmpty()) {
                pages.forEach(PlaywrightUtil::closePage);
            }
            context.clearCookies();
        });
    }

    /**
     * Returns the cookies of the given page's context as a semicolon-separated
     * {@code name=value} string.
     * @param page the page whose context cookies are read
     * @return the serialized cookies string
     */
    public static String getCookies(Page page) {
        return cookieToString(page.context().cookies());
    }

    /**
     * Serializes a list of cookies into a semicolon-separated {@code name=value} string.
     * @param cookies the cookies to serialize
     * @return the serialized cookies string
     */
    public static String cookieToString(List<Cookie> cookies) {
        return cookies.stream().map(cookie -> cookie.name + "=" + cookie.value).collect(Collectors.joining(TOKEN_SPLITTER));
    }

    /**
     * Clears the {@code window.localStorage} of the given page.
     * @param page the page whose local storage is cleared
     */
    public static void clearLocalStorage(Page page) {
        page.evaluate("window.localStorage.clear();");
    }

    /**
     * Drags a slider element matched by the given selector by a fixed length, simulating
     * a human-like mouse movement. The target element is awaited with a 5-second
     * timeout before dragging.
     * @param page             the page hosting the slider
     * @param slideElementPath the selector used to locate the slider element
     * @param slideLength      the distance in pixels to drag the slider
     * @param steps            the number of intermediate mouse-move steps to perform
     */
    public static void slide(Page page, String slideElementPath, int slideLength, int steps) {
        slide(page, page.waitForSelector(slideElementPath, new Page.WaitForSelectorOptions().setTimeout(TimeUnit.SECONDS.toMillis(5))), slideLength, steps);
    }

    /**
     * Drags the given element handle by a fixed length, simulating a human-like mouse
     * movement.
     * @param page           the page hosting the slider
     * @param elementHandle  the slider element to drag
     * @param slideLength    the distance in pixels to drag the slider
     * @param steps          the number of intermediate mouse-move steps to perform
     */
    public static void slide(Page page, ElementHandle elementHandle, int slideLength, int steps) {
        Mouse mouse = page.mouse();
        mouse.move(elementHandle.boundingBox().x, elementHandle.boundingBox().y);
        mouse.down(new Mouse.DownOptions().setButton(MouseButton.LEFT));
        mouse.move(elementHandle.boundingBox().x + slideLength, elementHandle.boundingBox().y, new Mouse.MoveOptions().setSteps(steps));
        mouse.up();
    }
    /**
     * Waits for the given page to reach the {@code load}, {@code domcontentloaded} and
     * {@code networkidle} load states.
     * @param page the page to wait on
     */
    public static void waitForPageLoad(Page page) {
        page.waitForLoadState(LoadState.LOAD);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    /**
     * Takes a PNG screenshot of either the element matched by the selector or, when the
     * selector is blank, the whole page.
     * @param page     the page to capture from
     * @param selector the element selector to capture (captured whole page when blank)
     * @return the screenshot image bytes
     */
    public static byte[] takeScreenshot(Page page, String selector) {
        if (StringUtils.hasText(selector)) {
            return page.locator(selector).screenshot();
        }
        return page.screenshot();
    }

    /**
     * Releases the resources held by the given browser context, clearing its cookies
     * and closing every page it owns.
     * @param browserContext the browser context to clean up (may be {@code null})
     */
    public static void cleanupBrowserContext(BrowserContext browserContext) {
        if (Objects.isNull(browserContext)) {
            return;
        }
        log.info("Cleanup BrowserContext Cookies '{}'.", browserContext);
        // 1. 清理 Cookie
        browserContext.clearCookies();
        // 2. 关闭所有页面
        browserContext.pages().forEach(PlaywrightUtil::closePage);
    }

    /**
     * Closes the given page if it is not already closed, swallowing any errors that
     * occur while closing.
     * @param page the page to close (may be {@code null} or already closed)
     */
    public static void closePage(Page page) {
        try {
            if (Objects.nonNull(page) && !page.isClosed()){
                page.close();
                log.debug("Close page Instance Success.");
            }
        } catch (Exception e) {
            log.warn("Failed to close page", e);
            // ignore error
        }

    }

}
