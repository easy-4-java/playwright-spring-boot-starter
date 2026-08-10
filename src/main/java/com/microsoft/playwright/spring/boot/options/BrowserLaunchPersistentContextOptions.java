package com.microsoft.playwright.spring.boot.options;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.options.*;
import lombok.Data;
import org.springframework.boot.context.properties.PropertyMapper;
import com.microsoft.playwright.spring.boot.properties.PropertyMapperCompat;
import org.springframework.util.CollectionUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
@Data
public class BrowserLaunchPersistentContextOptions {

    public static final long DEFAULT_DIR_USAGE_TIMEOUT = 30 * 60 * 1000; // 30分钟
    public static final long DEFAULT_MAX_DIR_SIZE = 200 * 1024 * 1024; // 200MB
    public static final int DEFAULT_MAX_CONTEXT_SIZE = 16;
    public static final int DEFAULT_MAX_RETRY_ATTEMPTS = 3;
    public static final long DEFAULT_RETRY_DELAY_MS = 1000;
    public static final long DEFAULT_RESOURCE_CLEANUP_TIMEOUT_MS = 30 * 1000;

    /**
     * Path to a User Data Directory, which stores browser session data like cookies and local storage. More details for <a
     * href="https://chromium.googlesource.com/chromium/src/+/master/docs/user_data_dir.md#introduction">Chromium</a> and <a
     * href="https://developer.mozilla.org/en-US/docs/Mozilla/Command_Line_Options#User_Profile">Firefox</a>. Note that
     * Chromium's user data directory is the **parent** directory of the "Profile Path" seen at {@code chrome://version}. Pass
     * an empty string to use a temporary directory instead.
     */
    public String userDataDir = "/tmp/playwright/";

    /**
     * Maximum number of browser contexts to be created. If the limit is reached, the oldest context will be closed. Defaults 16
     */
    public Integer maximumContentSize = DEFAULT_MAX_CONTEXT_SIZE;

    /**
     * Maximum size of the user data directory. If the limit is reached, the oldest context will be closed. Defaults 200MB
     */
    public Long maximumDirSize = DEFAULT_MAX_DIR_SIZE;

    /**
     * Maximum time to wait for the user data directory to be used. If the limit is reached, the oldest context will be closed.
     * Defaults 30 minutes
     */
    public Long maximumDirUsageTimeout = DEFAULT_DIR_USAGE_TIMEOUT;

    /**
     * Maximum number of retry attempts to create a new context. If the limit is reached, the oldest context will be closed.
     * Defaults 3
     */
    public Integer maximumRetryAttempts = DEFAULT_MAX_RETRY_ATTEMPTS;

    /**
     * Maximum time to wait for the retry delay. If the limit is reached, the oldest context will be closed. Defaults 1 second
     */
    public Long maximumRetryDelayMs = DEFAULT_RETRY_DELAY_MS;

    /**
     * Maximum time to wait for the resource cleanup. If the limit is reached, the oldest context will be closed. Defaults 30
     * seconds
     */
    public Long maximumResourceCleanupTimeoutMs = DEFAULT_RESOURCE_CLEANUP_TIMEOUT_MS;

    /**
     * Whether to automatically download all the attachments. Defaults to {@code true} where all the downloads are accepted.
     */
    public Boolean acceptDownloads;
    /**
     * <strong>NOTE:</strong> Use custom browser args at your own risk, as some of them may break Playwright functionality.
     *
     * <p> Additional arguments to pass to the browser instance. The list of Chromium flags can be found <a
     * href="https://peter.sh/experiments/chromium-command-line-switches/">here</a>.
     */
    public List<String> args;
    /**
     * When using {@link com.microsoft.playwright.Page#navigate Page.navigate()}, {@link com.microsoft.playwright.Page#route
     * Page.route()}, {@link com.microsoft.playwright.Page#waitForURL Page.waitForURL()}, {@link
     * com.microsoft.playwright.Page#waitForRequest Page.waitForRequest()}, or {@link
     * com.microsoft.playwright.Page#waitForResponse Page.waitForResponse()} it takes the base URL in consideration by using
     * the <a href="https://developer.mozilla.org/en-US/docs/Web/API/URL/URL">{@code URL()}</a> constructor for building the
     * corresponding URL. Unset by default. Examples:
     * <ul>
     * <li> baseURL: {@code http://localhost:3000} and navigating to {@code /bar.html} results in {@code
     * http://localhost:3000/bar.html}</li>
     * <li> baseURL: {@code http://localhost:3000/foo/} and navigating to {@code ./bar.html} results in {@code
     * http://localhost:3000/foo/bar.html}</li>
     * <li> baseURL: {@code http://localhost:3000/foo} (without trailing slash) and navigating to {@code ./bar.html} results in
     * {@code http://localhost:3000/bar.html}</li>
     * </ul>
     */
    public String baseUrl;
    /**
     * Toggles bypassing page's Content-Security-Policy. Defaults to {@code false}.
     */
    public Boolean bypassCsp;
    /**
     * Browser distribution channel.  Supported values are "chrome", "chrome-beta", "chrome-dev", "chrome-canary", "msedge",
     * "msedge-beta", "msedge-dev", "msedge-canary". Read more about using <a
     * href="https://playwright.dev/java/docs/browsers#google-chrome--microsoft-edge">Google Chrome and Microsoft Edge</a>.
     */
    public String channel;
    /**
     * Enable Chromium sandboxing. Defaults to {@code false}.
     */
    public Boolean chromiumSandbox;
    /**
     * TLS Client Authentication allows the server to request a client certificate and verify it.
     *
     * <p> <strong>Details</strong>
     *
     * <p> An array of client certificates to be used. Each certificate object must have either both {@code certPath} and {@code
     * keyPath}, a single {@code pfxPath}, or their corresponding direct value equivalents ({@code cert} and {@code key}, or
     * {@code pfx}). Optionally, {@code passphrase} property should be provided if the certificate is encrypted. The {@code
     * origin} property should be provided with an exact match to the request origin that the certificate is valid for.
     *
     * <p> <strong>NOTE:</strong> When using WebKit on macOS, accessing {@code localhost} will not pick up client certificates. You can make it work by
     * replacing {@code localhost} with {@code local.playwright}.
     */
    public List<ClientCertificate> clientCertificates;
    /**
     * Emulates {@code "prefers-colors-scheme"} media feature, supported values are {@code "light"}, {@code "dark"}, {@code
     * "no-preference"}. See {@link com.microsoft.playwright.Page#emulateMedia Page.emulateMedia()} for more details. Passing
     * {@code null} resets emulation to system defaults. Defaults to {@code "light"}.
     */
    public ColorScheme colorScheme;
    /**
     * Specify device scale factor (can be thought of as dpr). Defaults to {@code 1}. Learn more about <a
     * href="https://playwright.dev/java/docs/emulation#devices">emulating devices with device scale factor</a>.
     */
    public Double deviceScaleFactor;
    /**
     * If specified, accepted downloads are downloaded into this directory. Otherwise, temporary directory is created and is
     * deleted when browser is closed. In either case, the downloads are deleted when the browser context they were created in
     * is closed.
     */
    public Path downloadsPath;
    /**
     * Specify environment variables that will be visible to the browser. Defaults to {@code process.env}.
     */
    public Map<String, String> env;
    /**
     * Path to a browser executable to run instead of the bundled one. If {@code executablePath} is a relative path, then it is
     * resolved relative to the current working directory. Note that Playwright only works with the bundled Chromium, Firefox
     * or WebKit, use at your own risk.
     */
    public Path executablePath;
    /**
     * An object containing additional HTTP headers to be sent with every request. Defaults to none.
     */
    public Map<String, String> extraHttpHeaders;
    /**
     * Firefox user preferences. Learn more about the Firefox user preferences at <a
     * href="https://support.mozilla.org/en-US/kb/about-config-editor-firefox">{@code about:config}</a>.
     */
    public Map<String, Object> firefoxUserPrefs;
    /**
     * Emulates {@code "forced-colors"} media feature, supported values are {@code "active"}, {@code "none"}. See {@link
     * com.microsoft.playwright.Page#emulateMedia Page.emulateMedia()} for more details. Passing {@code null} resets emulation
     * to system defaults. Defaults to {@code "none"}.
     */
    public ForcedColors forcedColors;
    public Geolocation geolocation;
    /**
     * Close the browser process on SIGHUP. Defaults to {@code true}.
     */
    public Boolean handleSighup;
    /**
     * Close the browser process on Ctrl-C. Defaults to {@code true}.
     */
    public Boolean handleSigint;
    /**
     * Close the browser process on SIGTERM. Defaults to {@code true}.
     */
    public Boolean handleSigterm;
    /**
     * Specifies if viewport supports touch events. Defaults to false. Learn more about <a
     * href="https://playwright.dev/java/docs/emulation#devices">mobile emulation</a>.
     */
    public Boolean hasTouch;
    /**
     * Whether to run browser in headless mode. More details for <a
     * href="https://developers.google.com/web/updates/2017/04/headless-chrome">Chromium</a> and <a
     * href="https://developer.mozilla.org/en-US/docs/Mozilla/Firefox/Headless_mode">Firefox</a>. Defaults to {@code true}
     * unless the {@code devtools} option is {@code true}.
     */
    public Boolean headless;
    /**
     * Credentials for <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Authentication">HTTP authentication</a>. If
     * no origin is specified, the username and password are sent to any servers upon unauthorized responses.
     */
    public HttpCredentials httpCredentials;
    /**
     * If {@code true}, Playwright does not pass its own configurations args and only uses the ones from {@code args}.
     * Dangerous option; use with care. Defaults to {@code false}.
     */
    public Boolean ignoreAllDefaultArgs;
    /**
     * If {@code true}, Playwright does not pass its own configurations args and only uses the ones from {@code args}.
     * Dangerous option; use with care.
     */
    public List<String> ignoreDefaultArgs;
    /**
     * Whether to ignore HTTPS errors when sending network requests. Defaults to {@code false}.
     */
    public Boolean ignoreHttpsErrors;
    /**
     * Whether the {@code meta viewport} tag is taken into account and touch events are enabled. isMobile is a part of device,
     * so you don't actually need to set it manually. Defaults to {@code false} and is not supported in Firefox. Learn more
     * about <a href="https://playwright.dev/java/docs/emulation#ismobile">mobile emulation</a>.
     */
    public Boolean isMobile;
    /**
     * Whether or not to enable JavaScript in the context. Defaults to {@code true}. Learn more about <a
     * href="https://playwright.dev/java/docs/emulation#javascript-enabled">disabling JavaScript</a>.
     */
    public Boolean javaScriptEnabled;
    /**
     * Specify user locale, for example {@code en-GB}, {@code de-DE}, etc. Locale will affect {@code navigator.language} value,
     * {@code Accept-Language} request header value as well as number and date formatting rules. Defaults to the system default
     * locale. Learn more about emulation in our <a
     * href="https://playwright.dev/java/docs/emulation#locale--timezone">emulation guide</a>.
     */
    public String locale;
    /**
     * Whether to emulate network being offline. Defaults to {@code false}. Learn more about <a
     * href="https://playwright.dev/java/docs/emulation#offline">network emulation</a>.
     */
    public Boolean offline;
    /**
     * A list of permissions to grant to all pages in this context. See {@link
     * com.microsoft.playwright.BrowserContext#grantPermissions BrowserContext.grantPermissions()} for more details. Defaults
     * to none.
     */
    public List<String> permissions;
    /**
     * Network proxy settings.
     */
    public Proxy proxy;
    /**
     * Optional setting to control resource content management. If {@code omit} is specified, content is not persisted. If
     * {@code attach} is specified, resources are persisted as separate files and all of these files are archived along with
     * the HAR file. Defaults to {@code embed}, which stores content inline the HAR file as per HAR specification.
     */
    public HarContentPolicy recordHarContent;
    /**
     * When set to {@code minimal}, only record information necessary for routing from HAR. This omits sizes, timing, page,
     * cookies, security and other types of HAR information that are not used when replaying from HAR. Defaults to {@code
     * full}.
     */
    public HarMode recordHarMode;
    /**
     * Optional setting to control whether to omit request content from the HAR. Defaults to {@code false}.
     */
    public Boolean recordHarOmitContent;
    /**
     * Enables <a href="http://www.softwareishard.com/blog/har-12-spec">HAR</a> recording for all pages into the specified HAR
     * file on the filesystem. If not specified, the HAR is not recorded. Make sure to call {@link
     * com.microsoft.playwright.BrowserContext#close BrowserContext.close()} for the HAR to be saved.
     */
    public Path recordHarPath;
    public String recordHarUrlFilter;
    /**
     * Enables video recording for all pages into the specified directory. If not specified videos are not recorded. Make sure
     * to call {@link com.microsoft.playwright.BrowserContext#close BrowserContext.close()} for videos to be saved.
     */
    public Path recordVideoDir;
    /**
     * Dimensions of the recorded videos. If not specified the size will be equal to {@code viewport} scaled down to fit into
     * 800x800. If {@code viewport} is not configured explicitly the video size defaults to 800x450. Actual picture of each
     * page will be scaled down if necessary to fit the specified size.
     */
    public RecordVideoSize recordVideoSize;
    /**
     * Emulates {@code "prefers-reduced-motion"} media feature, supported values are {@code "reduce"}, {@code "no-preference"}.
     * See {@link com.microsoft.playwright.Page#emulateMedia Page.emulateMedia()} for more details. Passing {@code null} resets
     * emulation to system defaults. Defaults to {@code "no-preference"}.
     */
    public ReducedMotion reducedMotion;
    /**
     * Emulates consistent window screen size available inside web page via {@code window.screen}. Is only used when the {@code
     * viewport} is set.
     */
    public ScreenSize screenSize;
    /**
     * Whether to allow sites to register Service workers. Defaults to {@code "allow"}.
     * <ul>
     * <li> {@code "allow"}: <a href="https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API">Service Workers</a> can
     * be registered.</li>
     * <li> {@code "block"}: Playwright will block all registration of Service Workers.</li>
     * </ul>
     */
    public ServiceWorkerPolicy serviceWorkers;
    /**
     * Slows down Playwright operations by the specified amount of milliseconds. Useful so that you can see what is going on.
     */
    public Double slowMo;
    /**
     * If set to true, enables strict selectors mode for this context. In the strict selectors mode all operations on selectors
     * that imply single target DOM element will throw when more than one element matches the selector. This option does not
     * affect any Locator APIs (Locators are always strict). Defaults to {@code false}. See {@code Locator} to learn more about
     * the strict mode.
     */
    public Boolean strictSelectors;
    /**
     * Maximum time in milliseconds to wait for the browser instance to start. Defaults to {@code 30000} (30 seconds). Pass
     * {@code 0} to disable timeout.
     */
    public Double timeout = 30 * 1000.0;
    /**
     * Changes the timezone of the context. See <a
     * href="https://cs.chromium.org/chromium/src/third_party/icu/source/data/misc/metaZones.txt?rcl=faee8bc70570192d82d2978a71e2a615788597d1">ICU's
     * metaZones.txt</a> for a list of supported timezone IDs. Defaults to the system timezone.
     */
    public String timezoneId;
    /**
     * If specified, traces are saved into this directory.
     */
    public Path tracesDir;
    /**
     * Specific user agent to use in this context.
     */
    public String userAgent;
    /**
     * Emulates consistent viewport for each page. Defaults to an 1280x720 viewport. Use {@code null} to disable the consistent
     * viewport emulation. Learn more about <a href="https://playwright.dev/java/docs/emulation#viewport">viewport
     * emulation</a>.
     *
     * <p> <strong>NOTE:</strong> The {@code null} value opts out from the default presets, makes viewport depend on the host window size defined by the
     * operating system. It makes the execution of the tests non-deterministic.
     */
    public ViewportSize viewportSize;

    public BrowserType.LaunchPersistentContextOptions toOptions() {
        PropertyMapper map = PropertyMapperCompat.alwaysApplyingWhenNonNull();
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions();
        map.from(this.getAcceptDownloads()).to(options::setAcceptDownloads);
        map.from(this.getArgs()).when(args -> !CollectionUtils.isEmpty(args)).to(options::setArgs);
        map.from(this.getBaseUrl()).to(options::setBaseURL);
        map.from(this.getBypassCsp()).to(options::setBypassCSP);
        map.from(this.getChannel()).to(options::setChannel);
        map.from(this.getChromiumSandbox()).to(options::setChromiumSandbox);
        map.from(this.getClientCertificates()).when(clientCertificates -> !CollectionUtils.isEmpty(clientCertificates)).to(options::setClientCertificates);
        map.from(this.getColorScheme()).to(options::setColorScheme);
        map.from(this.getDeviceScaleFactor()).to(options::setDeviceScaleFactor);
        map.from(this.getDownloadsPath()).to(options::setDownloadsPath);
        map.from(this.getEnv()).when(env -> !CollectionUtils.isEmpty(env)).to(options::setEnv);
        map.from(this.getExecutablePath()).to(options::setExecutablePath);
        map.from(this.getExtraHttpHeaders()).when(extraHttpHeaders -> !CollectionUtils.isEmpty(extraHttpHeaders)).to(options::setExtraHTTPHeaders);
        map.from(this.getFirefoxUserPrefs()).when(firefoxUserPrefs -> !CollectionUtils.isEmpty(firefoxUserPrefs)).to(options::setFirefoxUserPrefs);
        map.from(this.getForcedColors()).to(options::setForcedColors);
        map.from(this.getGeolocation()).to(options::setGeolocation);
        map.from(this.getHandleSighup()).to(options::setHandleSIGHUP);
        map.from(this.getHandleSigint()).to(options::setHandleSIGINT);
        map.from(this.getHandleSigterm()).to(options::setHandleSIGTERM);
        map.from(this.getHasTouch()).to(options::setHasTouch);
        map.from(this.getHeadless()).to(options::setHeadless);
        map.from(this.getHttpCredentials()).to(options::setHttpCredentials);
        map.from(this.getIgnoreAllDefaultArgs()).to(options::setIgnoreAllDefaultArgs);
        map.from(this.getIgnoreDefaultArgs()).when(ignoreDefaultArgs -> !CollectionUtils.isEmpty(ignoreDefaultArgs)).to(options::setIgnoreDefaultArgs);
        map.from(this.getIgnoreHttpsErrors()).to(options::setIgnoreHTTPSErrors);
        map.from(this.getIsMobile()).to(options::setIsMobile);
        map.from(this.getJavaScriptEnabled()).to(options::setJavaScriptEnabled);
        map.from(this.getLocale()).to(options::setLocale);
        map.from(this.getOffline()).to(options::setOffline);
        map.from(this.getPermissions()).when(permissions -> !CollectionUtils.isEmpty(permissions)).to(options::setPermissions);
        map.from(this.getProxy()).to(options::setProxy);
        map.from(this.getRecordHarContent()).to(options::setRecordHarContent);
        map.from(this.getRecordHarMode()).to(options::setRecordHarMode);
        map.from(this.getRecordHarOmitContent()).to(options::setRecordHarOmitContent);
        map.from(this.getRecordHarPath()).to(options::setRecordHarPath);
        map.from(this.getRecordHarUrlFilter()).to(options::setRecordHarUrlFilter);
        map.from(this.getRecordVideoDir()).to(options::setRecordVideoDir);
        map.from(this.getRecordVideoSize()).to(options::setRecordVideoSize);
        map.from(this.getReducedMotion()).to(options::setReducedMotion);
        map.from(this.getScreenSize()).to(options::setScreenSize);
        map.from(this.getServiceWorkers()).to(options::setServiceWorkers);
        map.from(this.getSlowMo()).to(options::setSlowMo);
        map.from(this.getStrictSelectors()).to(options::setStrictSelectors);
        map.from(this.getTimeout()).to(options::setTimeout);
        map.from(this.getTimezoneId()).to(options::setTimezoneId);
        map.from(this.getTracesDir()).to(options::setTracesDir);
        map.from(this.getUserAgent()).to(options::setUserAgent);
        map.from(this.getViewportSize()).to(options::setViewportSize);
        return options;
    }

}
