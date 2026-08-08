package com.microsoft.playwright.spring.boot;

import com.microsoft.playwright.spring.boot.options.BrowserConnectOptions;
import com.microsoft.playwright.spring.boot.options.BrowserLaunchOptions;
import com.microsoft.playwright.spring.boot.options.BrowserLaunchPersistentContextOptions;
import com.microsoft.playwright.spring.boot.options.BrowserNewContextOptions;
import com.microsoft.playwright.spring.boot.options.ElementScreenshotOptions;
import com.microsoft.playwright.spring.boot.options.PageEmulateMediaOptions;
import com.microsoft.playwright.spring.boot.options.PageNavigateOptions;
import com.microsoft.playwright.spring.boot.options.PagePdfOptions;
import com.microsoft.playwright.spring.boot.options.PageScreenshotOptions;
import com.microsoft.playwright.spring.boot.options.PageWaitForSelectorOptions;
import com.microsoft.playwright.spring.boot.pool.BrowserContextPoolConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PlaywrightProperties}.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class PlaywrightPropertiesTest {

    @Test
    void prefix_isPlaywright() {
        assertThat(PlaywrightProperties.PREFIX).isEqualTo("playwright");
    }

    @Test
    void defaults_shouldMatchExpectedValues() {
        PlaywrightProperties props = new PlaywrightProperties();
        assertThat(props.getMemoryThreshold()).isEqualTo(0.85d);
        assertThat(props.isIsolated()).isFalse();
        assertThat(props.getBrowserType()).isEqualTo(PlaywrightProperties.BrowserTypeEnum.chromium);
        assertThat(props.getBrowserContextPool()).isInstanceOf(BrowserContextPoolConfig.class);
        assertThat(props.getConnectOptions()).isInstanceOf(BrowserConnectOptions.class);
        assertThat(props.getLaunchOptions()).isInstanceOf(BrowserLaunchOptions.class);
        assertThat(props.getLaunchPersistentContextOptions()).isInstanceOf(BrowserLaunchPersistentContextOptions.class);
        assertThat(props.getNewContextOptions()).isInstanceOf(BrowserNewContextOptions.class);
        assertThat(props.getPageNavigateOptions()).isInstanceOf(PageNavigateOptions.class);
        assertThat(props.getPageScreenshotOptions()).isInstanceOf(PageScreenshotOptions.class);
        assertThat(props.getPageWaitForSelectorOptions()).isInstanceOf(PageWaitForSelectorOptions.class);
        assertThat(props.getElementScreenshotOptions()).isInstanceOf(ElementScreenshotOptions.class);
        assertThat(props.getPagePdfOptions()).isInstanceOf(PagePdfOptions.class);
    }

    @Test
    void setters_shouldStoreValues() {
        PlaywrightProperties props = new PlaywrightProperties();
        props.setMemoryThreshold(0.5d);
        props.setIsolated(true);
        props.setBrowserType(PlaywrightProperties.BrowserTypeEnum.firefox);

        assertThat(props.getMemoryThreshold()).isEqualTo(0.5d);
        assertThat(props.isIsolated()).isTrue();
        assertThat(props.getBrowserType()).isEqualTo(PlaywrightProperties.BrowserTypeEnum.firefox);
    }

    @Test
    void browserTypeEnum_getBrowserType_shouldReturnCorrectType() {
        // Use a real Playwright instance (no browser launch happens; this only resolves the BrowserType).
        try (com.microsoft.playwright.Playwright playwright = com.microsoft.playwright.Playwright.create()) {
            assertThat(PlaywrightProperties.BrowserTypeEnum.chromium.getBrowserType(playwright))
                    .isSameAs(playwright.chromium());
            assertThat(PlaywrightProperties.BrowserTypeEnum.firefox.getBrowserType(playwright))
                    .isSameAs(playwright.firefox());
            assertThat(PlaywrightProperties.BrowserTypeEnum.webkit.getBrowserType(playwright))
                    .isSameAs(playwright.webkit());
        }
    }

    @Test
    void browserTypeEnum_shouldContainExactlyThreeValues() {
        assertThat(PlaywrightProperties.BrowserTypeEnum.values())
                .containsExactlyInAnyOrder(
                        PlaywrightProperties.BrowserTypeEnum.chromium,
                        PlaywrightProperties.BrowserTypeEnum.firefox,
                        PlaywrightProperties.BrowserTypeEnum.webkit);
    }
}
