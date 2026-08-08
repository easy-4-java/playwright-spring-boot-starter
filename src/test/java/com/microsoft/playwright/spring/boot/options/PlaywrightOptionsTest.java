package com.microsoft.playwright.spring.boot.options;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.Media;
import com.microsoft.playwright.options.ReducedMotion;
import com.microsoft.playwright.options.ScreenshotScale;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Playwright option POJOs under
 * {@code com.microsoft.playwright.spring.boot.options}.
 *
 * <p>Each {@code toOptions()} method exercises both the empty (default) path and
 * the populated path so that every {@code map.from(...).to(...)} line is covered.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class PlaywrightOptionsTest {

    @Test
    void browserConnectOptions_defaultsAndPopulated() {
        BrowserConnectOptions opts = new BrowserConnectOptions();
        assertThat(opts.getHeaders()).isNull();
        assertThat(opts.getSlowMo()).isEqualTo(0.0);
        assertThat(opts.getTimeout()).isEqualTo(0.0);
        assertThat(opts.toOptions()).isNotNull();

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Test", "1");
        opts.setHeaders(headers);
        opts.setSlowMo(2.0);
        opts.setTimeout(1000.0);
        BrowserType.ConnectOptions result = opts.toOptions();
        assertThat(result).isNotNull();
    }

    @Test
    void browserLaunchOptions_defaultsAndPopulated() {
        BrowserLaunchOptions opts = new BrowserLaunchOptions();
        assertThat(opts.getTimeout()).isEqualTo(30 * 1000.0);
        // default -> toOptions (everything null)
        assertThat(opts.toOptions()).isNotNull();

        opts.setArgs(List.of("--no-sandbox"));
        opts.setChannel("chrome");
        opts.setChromiumSandbox(false);
        opts.setDevtools(true);
        opts.setDownloadsPath(java.nio.file.Paths.get("/tmp/dl"));
        Map<String, String> env = new HashMap<>();
        env.put("FOO", "bar");
        opts.setEnv(env);
        opts.setExecutablePath(java.nio.file.Paths.get("/usr/bin/chrome"));
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("k", 1);
        opts.setFirefoxUserPrefs(prefs);
        opts.setHandleSighup(true);
        opts.setHandleSigint(true);
        opts.setHandleSigterm(true);
        opts.setHeadless(true);
        opts.setIgnoreAllDefaultArgs(true);
        opts.setIgnoreDefaultArgs(List.of("--ignore"));
        com.microsoft.playwright.options.Proxy proxy =
                new com.microsoft.playwright.options.Proxy("http://proxy");
        opts.setProxy(proxy);
        opts.setSlowMo(1.0);
        opts.setTimeout(5000.0);
        opts.setTracesDir(java.nio.file.Paths.get("/tmp/traces"));

        assertThat(opts.toOptions()).isNotNull();
    }

    @Test
    void browserLaunchOptions_emptyCollectionsAndEmptyProxy_shouldNotBlowUp() {
        BrowserLaunchOptions opts = new BrowserLaunchOptions();
        opts.setArgs(Collections.emptyList());
        opts.setEnv(Collections.emptyMap());
        opts.setFirefoxUserPrefs(Collections.emptyMap());
        opts.setIgnoreDefaultArgs(Collections.emptyList());
        opts.setProxy(null);
        assertThat(opts.toOptions()).isNotNull();
    }

    @Test
    void browserLaunchPersistentContextOptions_defaultsAndPopulated() {
        BrowserLaunchPersistentContextOptions opts = new BrowserLaunchPersistentContextOptions();
        assertThat(opts.getUserDataDir()).isEqualTo("/tmp/playwright/");
        assertThat(opts.getMaximumContentSize()).isEqualTo(BrowserLaunchPersistentContextOptions.DEFAULT_MAX_CONTEXT_SIZE);
        assertThat(opts.getMaximumDirSize()).isEqualTo(BrowserLaunchPersistentContextOptions.DEFAULT_MAX_DIR_SIZE);
        // default path
        assertThat(opts.toOptions()).isNotNull();

        opts.setAcceptDownloads(true);
        opts.setArgs(List.of("--headless"));
        opts.setBaseUrl("http://localhost");
        opts.setBypassCsp(true);
        opts.setChannel("chrome");
        opts.setChromiumSandbox(false);
        opts.setClientCertificates(Collections.emptyList());
        opts.setColorScheme(ColorScheme.DARK);
        opts.setDeviceScaleFactor(2.0);
        opts.setDownloadsPath(java.nio.file.Paths.get("/tmp/dl"));
        Map<String, String> env = new HashMap<>();
        env.put("A", "B");
        opts.setEnv(env);
        opts.setExecutablePath(java.nio.file.Paths.get("/usr/bin/chrome"));
        Map<String, String> headers = new HashMap<>();
        headers.put("H", "1");
        opts.setExtraHttpHeaders(headers);
        opts.setFirefoxUserPrefs(Collections.singletonMap("k", "v"));
        opts.setForcedColors(com.microsoft.playwright.options.ForcedColors.ACTIVE);
        opts.setGeolocation(new com.microsoft.playwright.options.Geolocation(1.0, 2.0));
        opts.setHandleSighup(true);
        opts.setHandleSigint(true);
        opts.setHandleSigterm(true);
        opts.setHasTouch(true);
        opts.setHeadless(true);
        opts.setHttpCredentials(new com.microsoft.playwright.options.HttpCredentials("u", "p"));
        opts.setIgnoreAllDefaultArgs(false);
        opts.setIgnoreDefaultArgs(List.of("--x"));
        opts.setIgnoreHttpsErrors(true);
        opts.setIsMobile(true);
        opts.setJavaScriptEnabled(false);
        opts.setLocale("en-GB");
        opts.setOffline(true);
        opts.setPermissions(List.of("geolocation"));
        opts.setProxy(new com.microsoft.playwright.options.Proxy("http://proxy"));
        opts.setRecordHarContent(com.microsoft.playwright.options.HarContentPolicy.ATTACH);
        opts.setRecordHarMode(com.microsoft.playwright.options.HarMode.MINIMAL);
        opts.setRecordHarOmitContent(true);
        opts.setRecordHarPath(java.nio.file.Paths.get("/tmp/h.har"));
        opts.setRecordHarUrlFilter("*");
        opts.setRecordVideoDir(java.nio.file.Paths.get("/tmp/vid"));
        opts.setRecordVideoSize(new com.microsoft.playwright.options.RecordVideoSize(640, 480));
        opts.setReducedMotion(ReducedMotion.REDUCE);
        opts.setScreenSize(new com.microsoft.playwright.options.ScreenSize(1920, 1080));
        opts.setServiceWorkers(com.microsoft.playwright.options.ServiceWorkerPolicy.ALLOW);
        opts.setSlowMo(1.0);
        opts.setStrictSelectors(false);
        opts.setTimeout(42_000.0);
        opts.setTimezoneId("UTC");
        opts.setTracesDir(java.nio.file.Paths.get("/tmp/traces"));
        opts.setUserAgent("UA");
        opts.setViewportSize(new com.microsoft.playwright.options.ViewportSize(800, 600));

        assertThat(opts.toOptions()).isNotNull();
    }

    @Test
    void browserLaunchPersistentContextOptions_emptyCollections_shouldHandleGracefully() {
        BrowserLaunchPersistentContextOptions opts = new BrowserLaunchPersistentContextOptions();
        opts.setArgs(Collections.emptyList());
        opts.setClientCertificates(Collections.emptyList());
        opts.setEnv(Collections.emptyMap());
        opts.setExtraHttpHeaders(Collections.emptyMap());
        opts.setFirefoxUserPrefs(Collections.emptyMap());
        opts.setIgnoreDefaultArgs(Collections.emptyList());
        opts.setPermissions(Collections.emptyList());
        assertThat(opts.toOptions()).isNotNull();
    }

    @Test
    void browserNewContextOptions_defaultsAndPopulated() {
        BrowserNewContextOptions opts = new BrowserNewContextOptions();
        assertThat(opts.getMaximumRetryAttempts()).isEqualTo(BrowserNewContextOptions.DEFAULT_MAX_RETRY_ATTEMPTS);
        assertThat(opts.getMaximumRetryDelayMs()).isEqualTo(BrowserNewContextOptions.DEFAULT_RETRY_DELAY_MS);
        assertThat(opts.getMaximumResourceCleanupTimeoutMs()).isEqualTo(BrowserNewContextOptions.DEFAULT_RESOURCE_CLEANUP_TIMEOUT_MS);
        // defaults
        assertThat(opts.toOptions()).isNotNull();

        opts.setAcceptDownloads(true);
        opts.setBaseUrl("http://localhost");
        opts.setBypassCsp(true);
        opts.setColorScheme(ColorScheme.LIGHT);
        opts.setDeviceScaleFactor(1.0);
        opts.setExtraHttpHeaders(Collections.singletonMap("X", "1"));
        opts.setForcedColors(com.microsoft.playwright.options.ForcedColors.NONE);
        opts.setGeolocation(new com.microsoft.playwright.options.Geolocation(1.0, 2.0));
        opts.setHasTouch(false);
        opts.setHttpCredentials(new com.microsoft.playwright.options.HttpCredentials("u", "p"));
        opts.setIgnoreHttpsErrors(false);
        opts.setIsMobile(false);
        opts.setJavaScriptEnabled(true);
        opts.setLocale("en-US");
        opts.setOffline(false);
        opts.setPermissions(List.of("geolocation"));
        opts.setProxy(new com.microsoft.playwright.options.Proxy("http://proxy"));
        opts.setRecordHarContent(com.microsoft.playwright.options.HarContentPolicy.EMBED);
        opts.setRecordHarMode(com.microsoft.playwright.options.HarMode.FULL);
        opts.setRecordHarOmitContent(false);
        opts.setRecordHarPath(java.nio.file.Paths.get("/tmp/h.har"));
        opts.setRecordHarUrlFilter("api");
        opts.setRecordVideoDir(java.nio.file.Paths.get("/tmp/vid"));
        opts.setRecordVideoSize(new com.microsoft.playwright.options.RecordVideoSize(640, 480));
        opts.setReducedMotion(ReducedMotion.REDUCE);
        opts.setScreenSize(new com.microsoft.playwright.options.ScreenSize(1024, 768));
        opts.setServiceWorkers(com.microsoft.playwright.options.ServiceWorkerPolicy.BLOCK);
        opts.setStorageState("{\"k\":1}");
        opts.setStorageStatePath(java.nio.file.Paths.get("/tmp/state.json"));
        opts.setStrictSelectors(true);
        opts.setTimezoneId("UTC");
        opts.setUserAgent("UA");
        opts.setViewportSize(new com.microsoft.playwright.options.ViewportSize(800, 600));

        assertThat(opts.toOptions()).isNotNull();
    }

    @Test
    void browserNewContextOptions_emptyPermissions_shouldBeSkipped() {
        BrowserNewContextOptions opts = new BrowserNewContextOptions();
        opts.setPermissions(Collections.emptyList());
        assertThat(opts.toOptions()).isNotNull();
    }

    @Test
    void elementScreenshotOptions_defaultsAndPopulated() {
        ElementScreenshotOptions opts = new ElementScreenshotOptions();
        assertThat(opts.getOmitBackground()).isTrue();
        assertThat(opts.getQuality()).isEqualTo(100);
        assertThat(opts.getScale()).isEqualTo(ScreenshotScale.DEVICE);
        assertThat(opts.getType()).isEqualTo(ScreenshotType.PNG);

        // defaults (PNG -> quality skipped)
        assertThat(opts.toOptions()).isNotNull();

        opts.setAnimations(com.microsoft.playwright.options.ScreenshotAnimations.DISABLED);
        opts.setCaret(com.microsoft.playwright.options.ScreenshotCaret.HIDE);
        opts.setMask(Collections.emptyList());
        opts.setMaskColor("#FF00FF");
        opts.setOmitBackground(false);
        opts.setPath(java.nio.file.Paths.get("/tmp/x.png"));
        opts.setQuality(80);
        opts.setScale(ScreenshotScale.CSS);
        opts.setTimeout(1000.0);
        opts.setType(ScreenshotType.JPEG); // non-PNG -> quality applied

        assertThat(opts.toOptions()).isNotNull();
    }

    @Test
    void pageEmulateMediaOptions_defaultsAndPopulated() {
        PageEmulateMediaOptions opts = new PageEmulateMediaOptions();
        assertThat(opts.toOptions()).isNotNull();

        opts.setColorScheme(ColorScheme.DARK);
        opts.setForcedColors(com.microsoft.playwright.options.ForcedColors.ACTIVE);
        opts.setMedia(Media.PRINT);
        opts.setReducedMotion(ReducedMotion.REDUCE);

        assertThat(opts.toOptions()).isNotNull();
    }

    @Test
    void pageNavigateOptions_defaultsAndPopulated() {
        PageNavigateOptions opts = new PageNavigateOptions();
        assertThat(opts.getWaitUntil()).isEqualTo(WaitUntilState.NETWORKIDLE);
        assertThat(opts.toOptions()).isNotNull();

        opts.setReferer("http://referer");
        opts.setTimeout(100.0);
        opts.setWaitUntil(WaitUntilState.LOAD);

        Page.NavigateOptions result = opts.toOptions();
        assertThat(result).isNotNull();
    }

    @Test
    void pagePdfOptions_defaultsAndPopulated() {
        PagePdfOptions opts = new PagePdfOptions();
        assertThat(opts.toOptions()).isNotNull();

        opts.setDisplayHeaderFooter(true);
        opts.setFooterTemplate("footer");
        opts.setFormat("A4");
        opts.setHeaderTemplate("header");
        opts.setHeight("297mm");
        opts.setLandscape(true);
        opts.setMargin(new com.microsoft.playwright.options.Margin()
                .setTop("10mm").setRight("10mm").setBottom("10mm").setLeft("10mm"));
        opts.setOutline(true);
        opts.setPageRanges("1-3");
        opts.setPath(java.nio.file.Paths.get("/tmp/o.pdf"));
        opts.setPreferCssPageSize(true);
        opts.setPrintBackground(true);
        opts.setScale(1.5);
        opts.setTagged(true);
        opts.setWidth("210mm");

        Page.PdfOptions result = opts.toOptions();
        assertThat(result).isNotNull();
    }

    @Test
    void pageScreenshotOptions_defaultsAndPopulated() {
        PageScreenshotOptions opts = new PageScreenshotOptions();
        assertThat(opts.getFullPage()).isTrue();
        assertThat(opts.getType()).isEqualTo(ScreenshotType.PNG);
        assertThat(opts.toOptions()).isNotNull();

        opts.setAnimations(com.microsoft.playwright.options.ScreenshotAnimations.DISABLED);
        opts.setCaret(com.microsoft.playwright.options.ScreenshotCaret.HIDE);
        opts.setClip(new com.microsoft.playwright.options.Clip(0, 0, 100, 100));
        opts.setFullPage(false);
        opts.setMask(Collections.emptyList());
        opts.setMaskColor("#000000");
        opts.setOmitBackground(false);
        opts.setPath(java.nio.file.Paths.get("/tmp/s.png"));
        opts.setQuality(50);
        opts.setScale(ScreenshotScale.CSS);
        opts.setTimeout(2000.0);
        opts.setType(ScreenshotType.JPEG); // non-PNG -> quality applied

        assertThat(opts.toOptions()).isNotNull();
    }

    @Test
    void pageWaitForSelectorOptions_defaultsAndPopulated() {
        PageWaitForSelectorOptions opts = new PageWaitForSelectorOptions();
        assertThat(opts.getState()).isEqualTo(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED);
        assertThat(opts.toOptions()).isNotNull();

        opts.setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE);
        opts.setStrict(true);
        opts.setTimeout(500.0);

        Page.WaitForSelectorOptions result = opts.toOptions();
        assertThat(result).isNotNull();
    }
}
