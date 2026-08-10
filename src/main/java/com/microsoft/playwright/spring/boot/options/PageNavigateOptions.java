package com.microsoft.playwright.spring.boot.options;


import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.Data;
import org.springframework.boot.context.properties.PropertyMapper;
import com.microsoft.playwright.spring.boot.properties.PropertyMapperCompat;

/**
 * Configuration properties for navigating a Playwright page to a URL. <p>Holds the
 * navigation options (referer, timeout and the load-completion state) so they can be
 * bound from Spring configuration and mapped to a {@link Page.NavigateOptions}
 * instance.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 */
@Data
public class PageNavigateOptions {

    /**
     * Referer header value. If provided it will take preference over the referer header value set by {@link
     * Page#setExtraHTTPHeaders Page.setExtraHTTPHeaders()}.
     */
    public String referer;
    /**
     * Maximum operation time in milliseconds, defaults to 30 seconds, pass {@code 0} to disable timeout. The default value can
     * be changed by using the {@link BrowserContext#setDefaultNavigationTimeout BrowserContext.setDefaultNavigationTimeout()},
     * {@link BrowserContext#setDefaultTimeout BrowserContext.setDefaultTimeout()}, {@link Page#setDefaultNavigationTimeout
     * Page.setDefaultNavigationTimeout()} or {@link Page#setDefaultTimeout Page.setDefaultTimeout()} methods.
     */
    public Double timeout = 30 * 1000.0;
    /**
     * When to consider operation succeeded, defaults to {@code load}. Events can be either:
     * <ul>
     * <li> {@code "domcontentloaded"} - consider operation to be finished when the {@code DOMContentLoaded} event is fired.</li>
     * <li> {@code "load"} - consider operation to be finished when the {@code load} event is fired.</li>
     * <li> {@code "networkidle"} - **DISCOURAGED** consider operation to be finished when there are no network connections for at
     * least {@code 500} ms. Don't use this method for testing, rely on web assertions to assess readiness instead.</li>
     * <li> {@code "commit"} - consider operation to be finished when network response is received and the document started loading.</li>
     * </ul>
     */
    public WaitUntilState waitUntil = WaitUntilState.NETWORKIDLE;

    /**
     * Converts these configuration properties into a {@link Page.NavigateOptions}
     * instance, mapping only non-{@code null} values.
     * @return the equivalent Playwright navigation options
     */
    public Page.NavigateOptions toOptions(){
        PropertyMapper map = PropertyMapperCompat.alwaysApplyingWhenNonNull();
        Page.NavigateOptions options = new Page.NavigateOptions();
        map.from(this.getReferer()).whenHasText().to(options::setReferer);
        map.from(this.getTimeout()).to(options::setTimeout);
        map.from(this.getWaitUntil()).to(options::setWaitUntil);
        return options;
    };

}
