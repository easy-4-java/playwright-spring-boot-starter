package com.microsoft.playwright.spring.boot.initializer;

import com.microsoft.playwright.spring.boot.PlaywrightProperties;
import com.microsoft.playwright.spring.boot.pool.BrowserContextPool;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Runnable that lazily warms up a {@link BrowserContextPool} on application start,
 * triggering browser installation and pool preparation.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 */
@Slf4j
public class BrowserContextInitializer implements Runnable {

	private final BrowserContextPool browserContextPool;
	private final PlaywrightProperties playwrightProperties;

	/**
	 * Creates a new initializer bound to the given pool and properties.
	 * @param browserContextPool   the pool to initialize
	 * @param playwrightProperties the Playwright configuration properties
	 */
	public BrowserContextInitializer(BrowserContextPool browserContextPool, PlaywrightProperties playwrightProperties) {
		this.browserContextPool = browserContextPool;
		this.playwrightProperties = playwrightProperties;
	}

	@Override
    /**
     * <p>Run.</p>
     */
	public void run() {
		if(Objects.nonNull(browserContextPool) && Objects.nonNull(playwrightProperties)){
			try {
				log.info("Browser Context Pool Start initialize ...");
				// 1、触发浏览器安装
				browserContextPool.preparePool();
				log.info("Browser Context Pool is initialize completed.");
			} catch (Exception e) {
				log.error("Browser Context Pool initialize error", e);
			}
		}
	}

}
