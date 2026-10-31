package com.microsoft.playwright.spring.boot;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.spring.boot.utils.PlaywrightUtil;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlaywrightBrowserTest {
    // Shared between all tests in this class.
    static Playwright playwright;
    static Browser browser;

    // New instance for each test method.
    BrowserContext context;
    Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = PlaywrightUtil.getInstance();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome").setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }

    @BeforeEach
    void createContextAndPage() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void shouldClickButton() {
        page.navigate("data:text/html,<script>var result;</script><button onclick='result=\"Clicked\"'>Go</button>");
        page.locator("button").click();
        assertEquals("Clicked", page.evaluate("result"));
    }

    @Test
    void shouldCheckTheBox() {
        page.setContent("<input id='checkbox' type='checkbox'></input>");
        page.locator("input").check();
        assertTrue((Boolean) page.evaluate("() => window['checkbox'].checked"));
    }

    @Test
    void shouldSearchWiki() {
        // Fulfill both pages locally so the interaction does not depend on Wikipedia.
        context.route("**/*", route -> {
            String url = route.request().url();
            if ("https://www.wikipedia.org/".equals(url)) {
                route.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody(
                    "<form onsubmit=\"event.preventDefault(); location.href='https://en.wikipedia.org/wiki/' + this.search.value\">"
                    + "<input name=\"search\"></form>"));
            } else if ("https://en.wikipedia.org/wiki/Playwright".equals(url)) {
                route.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody("<h1>Playwright</h1>"));
            } else {
                route.abort();
            }
        });
        page.navigate("https://www.wikipedia.org/");
        page.locator("input[name=\"search\"]").click();
        page.locator("input[name=\"search\"]").fill("Playwright");
        page.locator("input[name=\"search\"]").press("Enter");
        page.waitForURL("https://en.wikipedia.org/wiki/Playwright");
        assertEquals("https://en.wikipedia.org/wiki/Playwright", page.url());
    }
}
