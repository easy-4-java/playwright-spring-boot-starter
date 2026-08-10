package com.microsoft.playwright.spring.boot;


import com.microsoft.playwright.spring.boot.playwright.page.checker.DefaultPageScreenshotChecker;
import com.microsoft.playwright.spring.boot.playwright.page.checker.PageScreenshotChecker;
import com.microsoft.playwright.spring.boot.playwright.strategy.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Playwright 渲染引擎自动配置
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
@Configuration
@EnableConfigurationProperties(PlaywrightRenderProperties.class)
public class PlaywrightRenderConfiguration {

    @Bean
    public WkhtmlToImageBufferRenderStrategy wkhtmlToImageBufferRenderStrategy(
            ObjectProvider<PageScreenshotChecker> pageScreenshotCheckProvider) {
        WkhtmlToImageBufferRenderStrategy renderStrategy = new WkhtmlToImageBufferRenderStrategy();
        renderStrategy.setPageScreenshotCheckers(pageScreenshotCheckProvider.stream().sorted().collect(Collectors.toList()));
        return renderStrategy;
    }

    @Bean
    public WkhtmlToImageFileRenderStrategy wkhtmlToImageFileRenderStrategy(
            ObjectProvider<PageScreenshotChecker> pageScreenshotCheckProvider) {
        WkhtmlToImageFileRenderStrategy renderStrategy = new WkhtmlToImageFileRenderStrategy();
        renderStrategy.setPageScreenshotCheckers(pageScreenshotCheckProvider.stream().sorted().collect(Collectors.toList()));
        return renderStrategy;
    }

    @Bean
    public WkhtmlToPdfBufferRenderStrategy wkhtmlToPdfBufferRenderStrategy(
            ObjectProvider<PageScreenshotChecker> pageScreenshotCheckProvider) {
        WkhtmlToPdfBufferRenderStrategy renderStrategy = new WkhtmlToPdfBufferRenderStrategy();
        renderStrategy.setPageScreenshotCheckers(pageScreenshotCheckProvider.stream().sorted().collect(Collectors.toList()));
        return renderStrategy;
    }

    @Bean
    public WkhtmlToPdfFileRenderStrategy wkhtmlToPdfFileRenderStrategy(
            ObjectProvider<PageScreenshotChecker> pageScreenshotCheckProvider) {
        WkhtmlToPdfFileRenderStrategy renderStrategy = new WkhtmlToPdfFileRenderStrategy();
        renderStrategy.setPageScreenshotCheckers(pageScreenshotCheckProvider.stream().sorted().collect(Collectors.toList()));
        return renderStrategy;
    }

    @Bean
    public WkhtmlToPdfMergerBufferRenderStrategy wkhtmlToPdfMergerBufferRenderStrategy(
            ObjectProvider<PageScreenshotChecker> pageScreenshotCheckProvider) {
        WkhtmlToPdfMergerBufferRenderStrategy  renderStrategy = new WkhtmlToPdfMergerBufferRenderStrategy();
        renderStrategy.setPageScreenshotCheckers(pageScreenshotCheckProvider.stream().sorted().collect(Collectors.toList()));
        return renderStrategy;
    }

    @Bean
    public WkhtmlToPdfMergerFileRenderStrategy wkhtmlToPdfMergerFileRenderStrategy(
            ObjectProvider<PageScreenshotChecker> pageScreenshotCheckProvider) {
        WkhtmlToPdfMergerFileRenderStrategy renderStrategy = new WkhtmlToPdfMergerFileRenderStrategy();
        renderStrategy.setPageScreenshotCheckers(pageScreenshotCheckProvider.stream().sorted().collect(Collectors.toList()));
        return renderStrategy;
    }

    @Bean
    public PlaywrightRenderStrategyRouter playwrightRenderStrategyRouter(
            ObjectProvider<PlaywrightRenderStrategy> playwrightRenderStrategyObjectProvider) {
        return new PlaywrightRenderStrategyRouter(playwrightRenderStrategyObjectProvider.stream().collect(Collectors.toList()));
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultPageScreenshotChecker defaultPageScreenshotChecker(PlaywrightRenderProperties renderProperties) {
        PlaywrightRenderProperties.RenderCache cache = Objects.nonNull(renderProperties.getCache()) ? renderProperties.getCache() : new PlaywrightRenderProperties.RenderCache();
        return new DefaultPageScreenshotChecker(cache.getExpireAfterWrite(), cache.getInitialCapacity(), cache.getMaximumSize());
    }

}
