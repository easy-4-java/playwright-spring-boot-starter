/*
 * Copyright (c) 2018, hiwepy (https://github.com/hiwepy).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.microsoft.playwright.spring.boot;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.spring.boot.options.*;
import com.microsoft.playwright.spring.boot.pool.BrowserContextPoolConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.function.Function;

/**
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
@ConfigurationProperties(PlaywrightProperties.PREFIX)
@Data
public class PlaywrightProperties {

	// 85% 内存使用率阈值
	private static final double MEMORY_THRESHOLD = 0.85d;
	public static final String PREFIX = "playwright";
	/**
	 * The memory threshold to trigger cleanup. Defaults to {@code 0.85}.
	 */
	private double memoryThreshold = MEMORY_THRESHOLD;
	/**
	 *  Whether to isolate Browser session. Defaults to {@code false}.
	 */
	private boolean isolated = false;
	/**
	 * Browser type. Defaults to {@link BrowserTypeEnum#chromium chromium}.
	 */
	private BrowserTypeEnum browserType = BrowserTypeEnum.chromium;
	/**
	 * Browser Context Pool Config
	 */
	@NestedConfigurationProperty
	private BrowserContextPoolConfig browserContextPool = new BrowserContextPoolConfig();
	/**
	 * Connect Options
	 */
	@NestedConfigurationProperty
	private BrowserConnectOptions connectOptions = new BrowserConnectOptions();
	/**
	 * Launch Options
	 */
	@NestedConfigurationProperty
	private BrowserLaunchOptions launchOptions = new BrowserLaunchOptions();
	/**
	 * Launch Persistent Context Options
	 */
	@NestedConfigurationProperty
	private BrowserLaunchPersistentContextOptions launchPersistentContextOptions = new BrowserLaunchPersistentContextOptions();
	/**
	 * New Context Options
	 */
	@NestedConfigurationProperty
	private BrowserNewContextOptions newContextOptions = new BrowserNewContextOptions();
	/**
	 * Page Navigate Options
	 */
	@NestedConfigurationProperty
	private PageNavigateOptions pageNavigateOptions = new PageNavigateOptions();
	/**
	 * Page Screenshot Options
	 */
	@NestedConfigurationProperty
	private PageScreenshotOptions pageScreenshotOptions = new PageScreenshotOptions();
	/**
	 * Page Wait For Selector Options
	 */
	@NestedConfigurationProperty
	private PageWaitForSelectorOptions pageWaitForSelectorOptions = new PageWaitForSelectorOptions();
	/**
	 * Page Element Screenshot Options
	 */
	@NestedConfigurationProperty
	private ElementScreenshotOptions elementScreenshotOptions = new ElementScreenshotOptions();
	/**
	 * Page Pdf Options
	 */
	@NestedConfigurationProperty
	private PagePdfOptions pagePdfOptions = new PagePdfOptions();

    public enum BrowserTypeEnum {
		chromium(Playwright::chromium),
		firefox(Playwright::firefox),
		webkit(Playwright::webkit);

		final Function<Playwright, BrowserType> function;
		BrowserTypeEnum(Function<Playwright, BrowserType> function) {
			this.function = function;
		}

    /**
     * <p>Returns the browser type.</p>
     * @param playwright
     * @return the get browser type
     */
		public BrowserType getBrowserType(Playwright playwright) {
			return function.apply(playwright);
		}

	}

}
