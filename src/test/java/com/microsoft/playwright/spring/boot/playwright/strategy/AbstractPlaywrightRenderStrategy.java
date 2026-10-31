package com.microsoft.playwright.spring.boot.playwright.strategy;

import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.net.url.UrlQuery;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.Media;
import com.microsoft.playwright.spring.boot.PlaywrightProperties;
import com.microsoft.playwright.spring.boot.pool.BrowserContextPool;
import com.microsoft.playwright.spring.boot.PlaywrightRenderProperties;
import com.microsoft.playwright.spring.boot.playwright.bo.PageScreenshotTemp;
import com.microsoft.playwright.spring.boot.playwright.bo.WkhtmlRenderBO;
import com.microsoft.playwright.spring.boot.playwright.enums.RenderState;
import com.microsoft.playwright.spring.boot.playwright.enums.ResourceType;
import com.microsoft.playwright.spring.boot.playwright.page.checker.PageScreenshotChecker;
import com.microsoft.playwright.spring.boot.playwright.page.supplier.PagePdfMergeToPdfSupplier;
import com.microsoft.playwright.spring.boot.playwright.page.supplier.PageScreenshotMergeToPdfSupplier;
import com.microsoft.playwright.spring.boot.playwright.redis.BizRedisKey;
import com.microsoft.playwright.spring.boot.playwright.util.ImageUtil;
import com.microsoft.playwright.spring.boot.playwright.util.TimeUtil;
import com.microsoft.playwright.spring.boot.playwright.vo.WkhtmlRenderResultVO;
import hitool.core.format.ByteUnitFormat;
import hitool.core.lang3.StringUtils;
import hitool.core.lang3.time.DateFormats;
import hitool.core.lang3.uid.Sequence;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.redis.core.RedisOperationTemplate;
import java.util.Base64;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * 抽象的 Playwright 处理策略
 */
@Slf4j
public abstract class AbstractPlaywrightRenderStrategy<B extends WkhtmlRenderBO> implements PlaywrightRenderStrategy<B>, InitializingBean, ApplicationEventPublisherAware {

    protected static final int MIN_QUALITY = 0;
    protected static final int MAX_QUALITY = 100;

    protected static final String DATE_PATTERN = "yyyyMMddHHmmssS";
    protected static final String REPORT_URLS_PARAM_NAME = "report_urls";
    protected static final String REPORT_PARAM_UNIQUEID_NAME = "uniqueId";
    protected static final String DATA_RENDER_ATTR  = "data-render-result";
    protected static final String DATA_BACKGROUND_ATTR  = "data-background-url";
    protected static final String DATA_RENDER_SUCCESS  = "success";
    protected static final String DATA_RENDER_ERROR  = "error";
    protected static final DateTimeFormatter FILE_NAME_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);
    protected static final DateTimeFormatter DIRECTORY_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DateFormats.DATE_FORMAT_TWO);

    @Resource
    protected Sequence sequence;
    @Autowired
    protected PlaywrightProperties playwrightProperties;
    @Autowired
    protected PlaywrightRenderProperties playwrightRenderProperties;
    @Autowired
    protected BrowserContextPool browserContextPool;
    @Resource
    protected ThreadPoolExecutor dtpToImageExecutor;
    @Resource
    protected ThreadPoolExecutor dtpToImageCompressExecutor;
    @Resource
    protected ThreadPoolExecutor dtpToImageZipExecutor;
    @Resource
    protected ThreadPoolExecutor dtpToPdfExecutor;
    @Resource
    protected ThreadPoolExecutor dtpToPdfMergeExecutor;
    @Getter
    @Autowired
    protected RedisOperationTemplate redisOperation;
    @Getter
    protected List<PageScreenshotChecker> pageScreenshotCheckers;

    protected ApplicationEventPublisher eventPublisher;

    @Override
    public void afterPropertiesSet() throws Exception {
        // TODO Auto-generated method stub
    }

    @Override
    public void setPageScreenshotCheckers(List<PageScreenshotChecker> pageScreenshotCheckers) {
        this.pageScreenshotCheckers = pageScreenshotCheckers;
    }

    @Override
    public WkhtmlRenderResultVO render(B renderBO) throws Exception {
        log.info("=================Playwright 渲染 HTML:开始=================");
        String taskId = StringUtils.hasText(renderBO.getTaskId()) ? renderBO.getTaskId() :  String.valueOf(getSequence().nextId());
        renderBO.setTaskId(taskId);
        StopWatch stopWatch = new StopWatch(taskId);
        WkhtmlRenderResultVO resultBO = null;
        try {
            // 1、执行参数校验
            if(StringUtils.hasText(renderBO.getParam())){
                String param = new String(Base64.getDecoder().decode(renderBO.getParam()), Charset.defaultCharset());
                JSONObject jsonObject = JSON.parseObject(param);
                List<String> urls = jsonObject.getList(REPORT_URLS_PARAM_NAME, String.class);
                if(CollectionUtils.isEmpty(urls)){
                    throw new PlaywrightException("report_urls is empty");
                }
                // 去除空白URL
                urls.removeIf(StringUtils::isBlank);
                // 报告单渲染状态缓存
                String rdsKey = BizRedisKey.RENDER_STATE.getKey(taskId);
                Map<Object, Object> stateMap = redisOperation.hmGet(rdsKey);
                boolean hasStateAdd = false;
                // 网页URL数组
                List<PageScreenshotTemp> tempList = Lists.newArrayList();
                for (int i = 0; i < urls.size(); i++) {
                    PageScreenshotTemp screenshotTemp = new PageScreenshotTemp()
                            .setIndex(i)
                            .setUrl(Objects.toString(playwrightRenderProperties.getUrlPrefix(), StringUtils.EMPTY) + urls.get(i))
                            .setRenderState(RenderState.WAITING);
                    UrlBuilder urlBuilder = UrlBuilder.of(urls.get(i));
                    UrlQuery urlQuery = urlBuilder.getQuery();
                    if(Objects.nonNull(urlQuery)){
                        String uniqueId = Objects.toString(urlQuery.get(REPORT_PARAM_UNIQUEID_NAME), StringUtils.EMPTY);
                        if(StringUtils.isNotBlank(uniqueId)){
                            // 设置唯一ID
                            screenshotTemp.setUniqueId(uniqueId);
                            // 没有缓存则表示是初次渲染或缓存过期了
                            if (Objects.isNull(stateMap)) {
                                stateMap = Maps.newHashMap();
                            }
                            // 如果缓存中存在渲染状态，则根据渲染状态进行处理实际的逻辑处理
                            if (stateMap.containsKey(uniqueId)) {
                                String renderState = MapUtils.getString(stateMap, uniqueId, StringUtils.EMPTY);
                                if(StringUtils.isBlank(renderState)){
                                    hasStateAdd = true;
                                    stateMap.put(uniqueId, RenderState.WAITING.name());
                                    log.info("页面渲染状态不存在，重新渲染，TaskId: {}, url : {}", taskId, urls.get(i));
                                }
                                else if (RenderState.SUCCESS.name().equals(renderState)) {
                                    screenshotTemp.setRenderState(RenderState.SUCCESS);
                                    log.info("页面渲染成功，标记状态，以便后面逻辑使用，TaskId: {}, url : {}", taskId, urls.get(i));
                                } else if (RenderState.FAIL.name().equals(renderState)) {
                                    screenshotTemp.setRenderState(RenderState.FAIL);
                                    log.info("页面渲染失败，重新渲染，TaskId: {}, url : {}", taskId, urls.get(i));
                                } else if (RenderState.CHECK_FAIL.name().equals(renderState)) {
                                    screenshotTemp.setRenderState(RenderState.CHECK_FAIL);
                                    log.info("页面检查不通过，重新渲染，TaskId: {}, url : {}", taskId, urls.get(i));
                                } else if (RenderState.GENERATING.name().equals(renderState)) {
                                    screenshotTemp.setRenderState(RenderState.GENERATING);
                                    log.info("页面在其他任务中，正在生成，TaskId: {}, url : {}", taskId, urls.get(i));
                                }
                            } else {
                                hasStateAdd = true;
                                stateMap.put(uniqueId, RenderState.WAITING.name());
                                log.info("页面渲染状态不存在，重新渲染，TaskId: {}, url : {}", taskId, urls.get(i));
                            }

                        }
                    }
                    tempList.add(screenshotTemp);
                }
                renderBO.setUrls(tempList);
                // 有渲染状态缓存时候
                if (MapUtils.isNotEmpty(stateMap) && hasStateAdd) {
                    redisOperation.hmSet(rdsKey, stateMap, Duration.ofDays(2));
                }
            }
            renderBO.setCompress(Objects.nonNull(renderBO.getCompress()) ? renderBO.getCompress() : Boolean.FALSE);
            renderBO.setToFile(Objects.nonNull(renderBO.getToFile()) ? renderBO.getToFile() : Boolean.FALSE);
            // 2、执行内容生成逻辑
            List<PageScreenshotTemp> pageScreenshotTemps;
            try {
                log.info("=================Playwright 渲染 PDF/Image:开始=================");
                stopWatch.start("Playwright 渲染 PDF/Image");
                pageScreenshotTemps = this.doGenerate(renderBO);
            } finally {
                stopWatch.stop();
                log.info("=================Playwright 渲染 PDF/Image:结束=================");
            }
            // 2、执行打包内容生成逻辑
            try {
                log.info("=================压缩 Image:开始=================");
                stopWatch.start("Playwright 渲染 PDF/Image");
                pageScreenshotTemps = this.doCompress(renderBO, pageScreenshotTemps);
            } finally {
                stopWatch.stop();
                log.info("=================Playwright 渲染 PDF/Image:结束=================");
            }
            // 3、执行打包逻辑
            try {
                log.info("=================PDF/Image文件zip压缩:开始=================");
                stopWatch.start("PDF/Image文件zip压缩");
                resultBO = this.doPacking(renderBO, pageScreenshotTemps);
            } finally {
                stopWatch.stop();
                log.info("=================PDF/Image文件zip压缩:结束=================");
            }
            resultBO.setScreenshots(pageScreenshotTemps);
            return resultBO;
        } catch (Exception e) {
            this.afterException(renderBO, resultBO);
            throw e;
        } finally {
            if(stopWatch.isRunning()){
                stopWatch.stop();
            }
            log.info(stopWatch.prettyPrint());
            log.info("=================Playwright 渲染 HTML:结束=================");
        }
    }

    protected abstract List<PageScreenshotTemp> doGenerate(B renderBO) throws IOException;

    protected abstract List<PageScreenshotTemp> doCompress(B renderBO, List<PageScreenshotTemp> urlTemps) throws IOException;

    protected abstract WkhtmlRenderResultVO doPacking(B renderBO, List<PageScreenshotTemp> urlTemps) throws IOException;

    protected void afterException(B renderBO, WkhtmlRenderResultVO resultBO) throws IOException {
        this.cleanTemporary(renderBO, resultBO);
    }

    /**
     * 异步截图
     * @param renderBO 渲染参数 BO
     * @return 截图结果
     */
    protected List<PageScreenshotTemp> captureScreenshotAsync(B renderBO){
        if (CollectionUtils.isEmpty(renderBO.getUrls())) {
            return Lists.newArrayList();
        }
        BrowserContext browserContext = null;
        try {
            // 1、获取浏览器上下文
            browserContext = browserContextPool.borrowObject();
            // 2、使用CompletableFuture异步处理
            List<CompletableFuture<PageScreenshotTemp>> futureList = new ArrayList<>();
            List<PageScreenshotTemp> tempRtList = new ArrayList<>();
            for (PageScreenshotTemp screenshotTemp : renderBO.getUrls()) {
                // 如果url为空，则跳过
                if (StringUtils.isBlank(screenshotTemp.getUrl())) {
                    screenshotTemp.setRenderState(RenderState.FAIL);
                    screenshotTemp.setFailedReason("页面截图失败，失败原因：Url 为空");
                    continue;
                }
                // 3、异步截图
                CompletableFuture<PageScreenshotTemp> completableFuture = this.captureScreenshotAsync(browserContext, renderBO.getTaskId(), renderBO.getSelector(), screenshotTemp);
                // 4、异步截图任务执行完成
                completableFuture.whenComplete((pageScreenshot, e) -> {
                    if (Objects.nonNull(e)) {
                        log.error("异步截图任务执行异常，异常信息：", e);
                    } else {
                        log.info("异步截图任务执行完成，TaskId: {}, url : {}, pageName: {}, fileSize: {}", renderBO.getTaskId(), pageScreenshot.getUrl(), pageScreenshot.getName(),
                                ByteUnitFormat.B.to(ByteUnitFormat.K, pageScreenshot.getFileSize()));
                        tempRtList.add(pageScreenshot);
                    }
                });
                futureList.add(completableFuture);
            }
            if (CollectionUtils.isEmpty(futureList)) {
                return Lists.newArrayList();
            }
            // 2、使用CompletableFuture.allOf()方法，等待所有异步线程执行完毕
            log.info("等待截图异步任务完成，TaskId: {}", renderBO.getTaskId());
            CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).join();
            log.info("异步截图任务执行完毕，TaskId: {}", renderBO.getTaskId());
            return tempRtList;
        } catch (Exception e) {
            log.error("Async Capture screenshot error: ", e);
            if(e instanceof PlaywrightException){
                throw ExceptionUtils.throwableOfType(e, PlaywrightException.class);
            }
            throw new PlaywrightException("Async Capture screenshot error", e);
        } finally {
            if(Objects.nonNull(browserContext)){
                browserContextPool.returnObject(browserContext);
            }
        }
    }

    /**
     * 异步截图
     * @param browserContext 浏览器上下文
     * @param rendeId 渲染ID
     * @param selector 选择器
     * @param screenshotTemp 截图临时对象
     * @return 截图结果 
     */
    protected final CompletableFuture<PageScreenshotTemp> captureScreenshotAsync(BrowserContext browserContext, String rendeId, String selector, PageScreenshotTemp screenshotTemp){
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("异步截图任务开始执行...");
                screenshotTemp.setRenderState(RenderState.GENERATING);
                try(Page page = browserContext.newPage()) {
                    // 跳转到url
                    log.info("Async Capturing Screenshot start for rendeId: {}, selector: {}, url : {}", rendeId, selector, screenshotTemp.getUrl());
                    PageScreenshotTemp pageScreenshot = this.loadPageWithCallback(page, rendeId, selector, screenshotTemp, this.doPageScreenShot(rendeId, selector));
                    log.info("Async Capturing Screenshot completed for rendeId: {}, selector: {}, url : {}, pageName: {}, fileSize: {}KB", rendeId, selector, screenshotTemp.getUrl(),
                            pageScreenshot.getName(), ByteUnitFormat.B.to(ByteUnitFormat.K, pageScreenshot.getFileSize()));
                    return pageScreenshot;
                }
            } catch (Exception e) {
                screenshotTemp.setRenderState(RenderState.FAIL);
                screenshotTemp.setFailedReason(String.format("页面截图失败，失败原因：%s", e.getMessage()));
                log.error("Async Capture screenshot error: ", e);
                if(e instanceof PlaywrightException){
                    throw ExceptionUtils.throwableOfType(e, PlaywrightException.class);
                }
                throw new PlaywrightException("Async Capture screenshot error", e);
            }
        }, dtpToImageExecutor);
    }

    /**
     * 同步截图
     * @param renderBO 渲染参数 BO
     * @return 截图结果
     */
    protected List<PageScreenshotTemp> captureScreenshotSync(B renderBO){
        BrowserContext browserContext = null;
        try {
            // 1、获取浏览器上下文
            browserContext = browserContextPool.borrowObject();
            // 2、同步截图
            List<PageScreenshotTemp> tempRtList = new ArrayList<>();
            for (PageScreenshotTemp screenshotTemp : renderBO.getUrls()) {
                 // 如果url为空，则跳过
                 if (StringUtils.isBlank(screenshotTemp.getUrl())) {
                    screenshotTemp.setRenderState(RenderState.FAIL);
                    screenshotTemp.setFailedReason("页面截图失败，失败原因：Url 为空");
                    continue;
                }
                tempRtList.add(this.captureScreenshotSync(browserContext, renderBO.getTaskId(), renderBO.getSelector(), screenshotTemp));
            }
            return tempRtList;
        } catch (Exception e) {
            log.error("Async Capture screenshot error: ", e);
            if(e instanceof PlaywrightException){
                throw ExceptionUtils.throwableOfType(e, PlaywrightException.class);
            }
            throw new PlaywrightException("Async Capture screenshot error", e);
        } finally {
            if(Objects.nonNull(browserContext)){
                browserContextPool.returnObject(browserContext);
            }
        }
    }

    /**
     * 同步截图
     * @param browserContext 浏览器上下文
     * @param rendeId 渲染ID
     * @param selector 选择器
     * @param screenshotTemp 截图临时对象
     * @return 截图结果
     */
    protected PageScreenshotTemp captureScreenshotSync(BrowserContext browserContext, String rendeId, String selector, PageScreenshotTemp screenshotTemp){
        // 获取浏览器Page对象
        try (Page page = browserContext.newPage()) {
            screenshotTemp.setRenderState(RenderState.GENERATING);
            // 跳转到url
            log.info("Sync Capturing screenshot start for rendeId: {}, selector: {}, url : {}", rendeId, selector, screenshotTemp.getUrl());
            PageScreenshotTemp pageScreenshot = this.loadPageWithCallback(page, rendeId, selector, screenshotTemp, this.doPageScreenShot(rendeId, selector));
            log.info("Sync Capturing screenshot completed for rendeId: {}, selector: {}, url : {}, pageName: {}, fileSize: {}KB", rendeId, selector, screenshotTemp.getUrl(),
                    pageScreenshot.getName(), ByteUnitFormat.B.to(ByteUnitFormat.K, pageScreenshot.getFileSize()));
            return pageScreenshot;
        } catch (Exception e) {
            screenshotTemp.setRenderState(RenderState.FAIL);
            screenshotTemp.setFailedReason(String.format("页面截图失败，失败原因：%s", e.getMessage()));
            log.error("Sync Capture screenshot error: ", e);
            if(e instanceof PlaywrightException){
                throw ExceptionUtils.throwableOfType(e, PlaywrightException.class);
            }
            throw new PlaywrightException("Sync Capture screenshot error", e);
        }
    }

    /**
     * 判断截图/生成PDF是否可用
     * @param screenshotTemp url信息
     * @return 是否可用
     */
    protected boolean isPresentable(PageScreenshotTemp screenshotTemp){
        // 如果path为空，且buffer为空，则不符合要求
        if(StringUtils.isBlank(screenshotTemp.getPath()) && Objects.isNull(screenshotTemp.getBuffer())){
            return false;
        }
        // 如果path不为空，且文件不存在，则不符合要求
        if(StringUtils.isNotBlank(screenshotTemp.getPath())){
            File screenshotFile = new File(screenshotTemp.getPath());
            if(!screenshotFile.exists()){
                return false;
            }
        }
        // 如果截图文件大小小于指定大小，则不符合要求 ONE_KB * playwrightRenderProperties.getLowerLimit()
        long lowerLimitBytes = ByteUnitFormat.K.toLong(ByteUnitFormat.B, playwrightRenderProperties.getLowerLimit());
        long lowerLimit = Math.max(ImageUtil.WHITE_A4_SIZE, lowerLimitBytes);
        if(screenshotTemp.getFileSize() < lowerLimit){
            return false;
        }
        // 如果自定义判断方法，则执行自定义判断方法
        if(!CollectionUtils.isEmpty(pageScreenshotCheckers)){
            return pageScreenshotCheckers.stream().filter(Objects::nonNull)
                    .allMatch(checker -> checker.afterPageScreenShot(screenshotTemp));
        }
        return true;
    }

    /**
     * 加载页面并执行回调函数
     * @param page 浏览器Page对象
     * @param rendeId 渲染ID
     * @param selector 选择器
     * @param screenshotTemp 截图临时对象
     * @param callback 回调函数
     * @return 截图结果
     * @throws Exception 异常信息
     */
    protected PageScreenshotTemp loadPageWithCallback(Page page, String rendeId, String selector, PageScreenshotTemp screenshotTemp, BiFunction<Page, PageScreenshotTemp, PageScreenshotTemp> callback) throws Exception {
        
        // 默认设置截图文件大小为0
        screenshotTemp.setFileSize(0L);
        // 默认设置不需要重新加载
        screenshotTemp.setNeedReload(false);
        // 默认设置不需要重新加载
        screenshotTemp.setReload(false);
        // 默认设置加载超时时间为页面导航超时时间
        screenshotTemp.setReloadTimeout(playwrightProperties.getPageNavigateOptions().getTimeout());
        // 监听页面加载完成事件
        page.onLoad(page1 -> {
            if(screenshotTemp.isReload()){
                log.debug("Reload page for : {}", page1.url());
            } else {
                log.debug("Load page for : {}", page1.url());
            }
        });
        // 监听页面请求事件
        page.onRequest(request -> {
            if(screenshotTemp.isReload()){
                log.debug("Reload Request url: {}, resource type：{}, method：{}, postData：{}", request.url(), request.resourceType(),
                        request.method(), request.postData());
            } else {
                log.debug("Request url: {}, resource type：{}, method：{}, postData：{}", request.url(), request.resourceType(),
                        request.method(), request.postData());
            }
        });
        // 监听页面请求失败事件
        page.onRequestFailed(request -> {
            if(screenshotTemp.isReload()){
                log.error("Reload Request failed: {}, resource type：{}, reason：{}", request.url(), request.resourceType(), request.failure());
            } else {
                log.error("Request failed: {}, resource type：{}, reason：{}", request.url(), request.resourceType(), request.failure());
            }
            // 渲染引擎所感知的请求的资源类型。ResourceType将是以下之一： document, stylesheet, image, media, font, script, texttrack, xhr, fetch, eventsource, websocket, manifest, other.
            ResourceType resourceType = ResourceType.getByName(request.resourceType());
            if (Objects.nonNull(resourceType) && resourceType.isNeedRetry()) {
                screenshotTemp.setNeedReload(true);
                log.debug("Page need reload for url : {}", page.url());
            }
        });
        // 监听页面响应事件
        page.onResponse(response -> {
            if(screenshotTemp.isReload()){
                log.debug("Reload Response url: {}, status：{}, headers：{}", response.url(), response.status(), response.headers());
            } else {
                log.debug("Response url: {}, status：{}, headers：{}", response.url(), response.status(), response.headers());
            }
        });
        // 监听页面错误事件
        page.onPageError(exception -> {
            log.error("page error: {}", exception);
            screenshotTemp.setNeedReload(true);
            log.debug("page need retry for url : {}", page.url());
        });
        // 监听页面崩溃事件
        page.onCrash(page1 -> {
            log.error("page crash for url : {}", page1.url());
            screenshotTemp.setNeedReload(true);
            log.debug("page crash and need reload for url : {}", page1.url());
        });
        // 设置页面加载参数, 并跳转到url
        Page.NavigateOptions navigateOptions = playwrightProperties.getPageNavigateOptions().toOptions();
        page.navigate(screenshotTemp.getUrl(), navigateOptions);
        // 设置了元素等待选择器，则等待元素加载完成
        String waitForSelector = playwrightRenderProperties.getWaitForSelector();
        if (StringUtils.isNotBlank(waitForSelector)) {
            log.debug("The page waitForSelector for : {}", waitForSelector);
            ElementHandle elementHandle = null;
            try {
                // 等待元素加载完成
                elementHandle = page.waitForSelector(waitForSelector, playwrightProperties.getPageWaitForSelectorOptions().toOptions());
                if(Objects.isNull(elementHandle)){
                    throw new PlaywrightException("The page waitForSelector element is not found for : " + waitForSelector);
                }
                // 如果元素加载完成，则获取元素的 data-render-result 属性
                if(DATA_RENDER_ERROR.equalsIgnoreCase(elementHandle.getAttribute(DATA_RENDER_ATTR))){
                    throw new PlaywrightException("页面渲染失败: " + elementHandle.textContent());
                }
            } finally {
                if(Objects.nonNull(elementHandle)){
                    // ElementHandle 会阻止 DOM 元素进行垃圾回收，除非使用 JSHandle.dispose() 处理该句柄。当其原始框架被导航时，ElementHandles 会被自动处理。
                    elementHandle.dispose();
                }
            }
        }
        // 如果设置了加载等待时间，则等待一段时间
        if(playwrightRenderProperties.isLoadWait() && Objects.nonNull(playwrightRenderProperties.getLoadWaitDuration()) && playwrightRenderProperties.getLoadWaitDuration().toMillis() > 0){
            // 人为的等待一段时间
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    log.debug("The page load wait {} milliseconds for : {}", playwrightRenderProperties.getLoadWaitDuration().toMillis(), page.url());
                    TimeUnit.MILLISECONDS.sleep(playwrightRenderProperties.getLoadWaitDuration().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Thread was interrupted", e);
                }
            });
            // 等待异步任务完成
            future.join();
            log.debug("The page load wait completed for : {}", page.url());
        }
        log.debug("The page load completed for : {}", page.url());
        // 定位到要截图的元素，找到背景图元素
        if(StringUtils.isNotBlank(selector)){
            ElementHandle elementHandle = null;
            try {
                elementHandle = page.querySelector(selector);
                if(Objects.nonNull(elementHandle)){
                    String backgroundUrl = elementHandle.getAttribute(DATA_BACKGROUND_ATTR);
                    if(StringUtils.isNotBlank(backgroundUrl)){
                        // 获取背景图片
                        log.debug("The page background image is : {}", backgroundUrl);
                        screenshotTemp.setBgUrl(backgroundUrl);
                    }
                }
            } catch (Exception e){
                screenshotTemp.setRenderState(RenderState.FAIL);
                screenshotTemp.setFailedReason(String.format("页面截图失败，失败原因：%s", e.getMessage()));
                if(e instanceof PlaywrightException){
                    throw ExceptionUtils.throwableOfType(e, PlaywrightException.class);
                }
                throw new PlaywrightException("Browser Context error", e);
            } finally {
                if(Objects.nonNull(elementHandle)){
                    // ElementHandle 会阻止 DOM 元素进行垃圾回收，除非使用 JSHandle.dispose() 处理该句柄。当其原始框架被导航时，ElementHandles 会被自动处理。
                    elementHandle.dispose();
                }
            }
        }
        // 执行回调函数（截图、单页生成pdf）
        PageScreenshotTemp applyTemp = callback.apply(page, screenshotTemp);
        // 判断回调处理后的结果是否可用，如果可用则返回，无需在进行重试
        if(this.isPresentable(applyTemp)){
            screenshotTemp.setRenderState(RenderState.SUCCESS);
            if(Objects.nonNull(screenshotTemp.getUniqueId())){
                String rdsKey = BizRedisKey.RENDER_STATE.getKey(rendeId);
                redisOperation.hSet(rdsKey, screenshotTemp.getUniqueId(), RenderState.SUCCESS.name(), Duration.ofDays(2));
            }
            return applyTemp;
        } else {
            screenshotTemp.setNeedReload(true);
        }
        // 结果不符合要求，补充重试机制，多次打开页面
        AtomicInteger loadRetry = new AtomicInteger(0);
        while ( playwrightRenderProperties.isReloadAble() && screenshotTemp.isNeedReload() && loadRetry.incrementAndGet() < playwrightRenderProperties.getReloadLimit()) {
            // 动态调整超时时间
            if(Objects.nonNull(screenshotTemp.getReloadTimeout())){
                screenshotTemp.setReloadTimeout(TimeUtil.getRetryTimeout(screenshotTemp.getReloadTimeout()));
            }
            // 重置重新加载标识
            screenshotTemp.setNeedReload(false);
            log.debug("The page reloading for : {} , reloadTimes: {}, reloadTimeout: {}", page.url(), loadRetry.get(), screenshotTemp.getReloadTimeout());
            Page.ReloadOptions reloadOptions = new Page.ReloadOptions()
                    .setTimeout(screenshotTemp.getReloadTimeout())
                    .setWaitUntil(playwrightProperties.getPageNavigateOptions().getWaitUntil());
            page.reload(reloadOptions);
            // 设置重新加载状态为false
            screenshotTemp.setReload(false);
            // 设置了元素等待选择器，则等待元素加载完成
            if (StringUtils.isNotBlank(waitForSelector)) {
                log.debug("The page waitForSelector for : {}", waitForSelector);
                ElementHandle elementHandle = null;
                try {
                    elementHandle = page.waitForSelector(waitForSelector, playwrightProperties.getPageWaitForSelectorOptions().toOptions());
                    // 如果元素加载完成，则获取元素的 data-render-result 属性
                    if(DATA_RENDER_ERROR.equalsIgnoreCase(elementHandle.getAttribute(DATA_RENDER_ATTR))){
                        screenshotTemp.setNeedReload(true);
                        log.debug("The page waitForSelector element is not found for : {}", waitForSelector);
                        continue;
                    }
                } finally {
                    if(Objects.nonNull(elementHandle)){
                        // ElementHandle 会阻止 DOM 元素进行垃圾回收，除非使用 JSHandle.dispose() 处理该句柄。当其原始框架被导航时，ElementHandles 会被自动处理。
                        elementHandle.dispose();
                    }
                }
            }
            // 如果是重试，则等待一段时间
            if(playwrightRenderProperties.isReloadWait() && Objects.nonNull(playwrightRenderProperties.getReloadWaitDuration()) && playwrightRenderProperties.getReloadWaitDuration().toMillis() > 0){
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        log.debug("The page reload wait {} milliseconds for : {}", playwrightRenderProperties.getReloadWaitDuration().toMillis(), page.url());
                        TimeUnit.MILLISECONDS.sleep(playwrightRenderProperties.getReloadWaitDuration().toMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Thread was interrupted", e);
                    }
                });
                // 等待异步任务完成
                future.join();
                log.debug("The page reload wait completed for : {}", page.url());
            }
            log.debug("The page reload completed for : {} , reloadTimes: {}, reloadTimeout: {}", page.url(), loadRetry.get(), screenshotTemp.getReloadTimeout());
            // 判断重新加载，再次处理后的结果是否可用，如果可用则返回，无需在进行重试
            if(this.isPresentable(applyTemp)){
                screenshotTemp.setNeedReload(false);
                screenshotTemp.setRenderState(RenderState.SUCCESS);
                if(Objects.nonNull(screenshotTemp.getUniqueId())){
                    String rdsKey = BizRedisKey.RENDER_STATE.getKey(rendeId);
                    redisOperation.hSet(rdsKey, screenshotTemp.getUniqueId(), RenderState.SUCCESS.name(), Duration.ofDays(2));
                }
                return applyTemp;
            } else {
                screenshotTemp.setNeedReload(true);
            }
        }
        return screenshotTemp;
    }

    /**
     * 执行页面截图动作
     * @param rendeId 渲染ID
     * @param selector 元素选择权
     * @return 截图结果
     */
    protected BiFunction<Page, PageScreenshotTemp, PageScreenshotTemp> doPageScreenShot(String rendeId, String selector){
        return (page, screenshotTemp) -> {
            // 定义截图输出路径
            String fileName = String.format("%s.png", screenshotTemp.getIndex());
            screenshotTemp.setName(fileName);
            try {
                // 截图
                if(StringUtils.isEmpty(selector)){
                    Page.ScreenshotOptions screenshotOptions = playwrightProperties.getPageScreenshotOptions().toOptions();
                    if(playwrightRenderProperties.isWriteToFile()){
                        File screenshotFile = new File(playwrightRenderProperties.getTmpDir(), rendeId + File.separator + fileName);
                        log.info("Page screenshot start for rendeId : {}, renderType : {}, to path: {}", rendeId, getRenderType(), screenshotFile.getAbsolutePath());
                        screenshotOptions.setPath(screenshotFile.toPath());
                        page.screenshot(screenshotOptions);
                        screenshotTemp.setPath(screenshotFile.getAbsolutePath());
                        screenshotTemp.setFileSize(screenshotFile.length());
                        log.info("Page screenshot success for rendeId : {}, renderType : {}, to path: {}, fileSize: {}KB", rendeId, getRenderType(), screenshotFile.getAbsolutePath(),
                                ByteUnitFormat.B.to(ByteUnitFormat.K, screenshotTemp.getFileSize()));
                    } else {
                        log.info("Page screenshot start for rendeId : {}, renderType : {}, to buffer", rendeId, getRenderType());
                        byte[] screenshotBuffer = page.screenshot(screenshotOptions);
                        screenshotTemp.setBuffer(screenshotBuffer);
                        screenshotTemp.setFileSize((long) screenshotBuffer.length);
                        log.info("Page screenshot success for rendeId : {}, renderType : {}, to buffer, fileSize: {}KB", rendeId, getRenderType(),
                                ByteUnitFormat.B.to(ByteUnitFormat.K, screenshotTemp.getFileSize()));
                    }
                } else {
                    // 定位到要截图的元素
                    ElementHandle element = page.querySelector(selector);
                    if(Objects.nonNull(element)) {
                        if (element.isVisible()) {
                            // 滚动到元素位置
                            element.scrollIntoViewIfNeeded();
                        }
                        ElementHandle.ScreenshotOptions screenshotOptions = playwrightProperties.getElementScreenshotOptions().toOptions();
                        if(playwrightRenderProperties.isWriteToFile()){
                            File screenshotFile = new File(playwrightRenderProperties.getTmpDir(), rendeId + File.separator + fileName);
                            log.info("Page screenshot start for rendeId : {}, renderType : {}, with selector : {}, to path: {}", rendeId, getRenderType(), selector, screenshotFile.getAbsolutePath());
                            screenshotOptions.setPath(screenshotFile.toPath());
                            element.screenshot(screenshotOptions);
                            screenshotTemp.setPath(screenshotFile.getAbsolutePath());
                            screenshotTemp.setFileSize(screenshotFile.length());
                            log.info("Page screenshot success for rendeId : {}, renderType : {}, with selector: {}, to path: {}, fileSize: {}KB", rendeId, getRenderType(), selector,
                                    screenshotFile.getAbsolutePath(), ByteUnitFormat.B.to(ByteUnitFormat.K, screenshotTemp.getFileSize()));
                        } else {
                            log.info("Page screenshot start for rendeId : {}, renderType : {}, with selector : {}, to buffer", rendeId, getRenderType(), selector);
                            byte[] screenshotBuffer = element.screenshot(screenshotOptions);
                            screenshotTemp.setBuffer(screenshotBuffer);
                            screenshotTemp.setFileSize((long) screenshotBuffer.length);
                            log.info("Page screenshot success for rendeId : {}, renderType : {}, with selector: {}, to buffer, fileSize: {}KB", rendeId, getRenderType(), selector,
                                    ByteUnitFormat.B.to(ByteUnitFormat.K, screenshotTemp.getFileSize()));
                        }
                    } else {
                        log.error("element not found for selector: {}, url : {}", selector, screenshotTemp.getUrl());
                        screenshotTemp.setRenderState(RenderState.FAIL);
                        screenshotTemp.setFailedReason(String.format("页面截图失败，失败原因：未找到匹配 %s 的元素", selector));
                    }
                }
            } catch (Exception e){
                screenshotTemp.setRenderState(RenderState.FAIL);
                screenshotTemp.setFailedReason(String.format("页面截图失败，失败原因：%s", e.getMessage()));
            }
            return screenshotTemp;
        };
    }

    /**
     * 页面异步保存为 PDF
     * @param renderBO 渲染参数 BO
     * @return PDF
     */
    protected List<PageScreenshotTemp> pageToPdfFutureAsync(B renderBO){
        if (CollectionUtils.isEmpty(renderBO.getUrls())) {
            return Lists.newArrayList();
        }
        BrowserContext browserContext = null;
        try {
            // 1、获取浏览器上下文
            browserContext = browserContextPool.borrowObject();
            // 2、使用CompletableFuture异步处理
            List<CompletableFuture<PageScreenshotTemp>> futureList = new ArrayList<>();
            List<PageScreenshotTemp> tempRtList = new ArrayList<>();
            for (PageScreenshotTemp screenshotTemp : renderBO.getUrls()) {
                // 如果url为空，则跳过
                if (StringUtils.isBlank(screenshotTemp.getUrl())) {
                    screenshotTemp.setRenderState(RenderState.FAIL);
                    screenshotTemp.setFailedReason("页面异步保存为 PDF失败，失败原因：Url 为空");
                    continue;
                }
                // 3、异步截图
                CompletableFuture<PageScreenshotTemp> completableFuture = this.captureScreenshotAsync(browserContext, renderBO.getTaskId(), renderBO.getSelector(), screenshotTemp);
                // 4、异步截图任务执行完成
                completableFuture.whenComplete((pageScreenshot, e) -> {
                    if (Objects.nonNull(e)) {
                        log.error("页面异步保存为 PDF 任务执行异常，异常信息：", e);
                    } else {
                        log.info("页面异步保存为 PDF 任务执行完成，TaskId: {}, url : {}, pageName: {}, fileSize: {}", renderBO.getTaskId(), pageScreenshot.getUrl(), pageScreenshot.getName(),
                                ByteUnitFormat.B.to(ByteUnitFormat.K, pageScreenshot.getFileSize()));
                        tempRtList.add(pageScreenshot);
                    }
                });
                futureList.add(completableFuture);
            }
            if (CollectionUtils.isEmpty(futureList)) {
                return Lists.newArrayList();
            }
            // 2、使用CompletableFuture.allOf()方法，等待所有异步线程执行完毕
            log.info("等待页面保存为 PDF 异步任务完成，TaskId: {}", renderBO.getTaskId());
            CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).join();
            log.info("页面异步保存为 PDF 任务执行完毕，TaskId: {}", renderBO.getTaskId());
            return tempRtList;
        } catch (Exception e) {
            log.error("页面异步保存为 PDF 异常: ", e);
            if(e instanceof PlaywrightException){
                throw ExceptionUtils.throwableOfType(e, PlaywrightException.class);
            }
            throw new PlaywrightException("页面异步保存为 PDF 异常", e);
        } finally {
            if(Objects.nonNull(browserContext)){
                browserContextPool.returnObject(browserContext);
            }
        }
    }

    protected final CompletableFuture<PageScreenshotTemp> pageToPdfFutureAsync(String rendeId, String selector, PageScreenshotTemp screenshotTemp) {
        // 如果url为空，则直接返回
        if(StringUtils.isBlank(screenshotTemp.getUrl())){
            return CompletableFuture.completedFuture(screenshotTemp);
        }
        //HttpServletRequest request = WebUtils.getHttpServletRequest();
        // 1、使用CompletableFuture.supplyAsync()方法，异步执行截图
        return CompletableFuture.supplyAsync(() -> {
            // Page page = null;
            BrowserContext browserContext = null;
            try {
                screenshotTemp.setRenderState(RenderState.GENERATING);
                browserContext = browserContextPool.borrowObject();
                try(Page page = browserContext.newPage()) {
                    log.info("Async Generate pdf start for rendeId: {}, url : {}", rendeId, screenshotTemp.getUrl());
                    PageScreenshotTemp pageToPdf = this.loadPageWithCallback(page, rendeId, selector, screenshotTemp, this.doPageToPdf(rendeId));
                    log.info("Async Generate pdf completed for rendeId: {}, url : {}, pageName: {}, fileSize: {}KB", rendeId, screenshotTemp.getUrl(), pageToPdf.getName(),
                            ByteUnitFormat.B.to(ByteUnitFormat.K, pageToPdf.getFileSize()));
                    return pageToPdf;
                } catch (Exception e) {
                    log.error("Async Generate pdf error: ", e);
                    if(e instanceof PlaywrightException){
                        throw ExceptionUtils.throwableOfType(e, PlaywrightException.class);
                    }
                    throw new PlaywrightException("Async Generate pdf error", e);
                }
            } catch (Exception e) {
                screenshotTemp.setRenderState(RenderState.FAIL);
                screenshotTemp.setFailedReason(String.format("页面截图失败，失败原因：%s", e.getMessage()));
                log.error("Browser Context error: ", e);
                if(e instanceof PlaywrightException){
                    throw ExceptionUtils.throwableOfType(e, PlaywrightException.class);
                }
                throw new PlaywrightException("Browser Context error", e);
            } finally {
                if(Objects.nonNull(browserContext)){
                    browserContextPool.returnObject(browserContext);
                }
            }
        }, dtpToPdfExecutor);
    }

    /**
     * 同步页面保存为PDF
     * @param renderBO 渲染参数 BO
     * @return 保存的结果
     */
    protected List<PageScreenshotTemp> pageToPdfFutureSync(B renderBO){
        BrowserContext browserContext = null;
        try {
            // 1、获取浏览器上下文
            browserContext = browserContextPool.borrowObject();
            // 2、同步截图
            List<PageScreenshotTemp> tempRtList = new ArrayList<>();
            for (PageScreenshotTemp screenshotTemp : renderBO.getUrls()) {
                // 如果url为空，则跳过
                if (StringUtils.isBlank(screenshotTemp.getUrl())) {
                    screenshotTemp.setRenderState(RenderState.FAIL);
                    screenshotTemp.setFailedReason("页面保存为PDF失败，失败原因：Url 为空");
                    continue;
                }
                tempRtList.add(this.pageToPdfFutureSync(browserContext, renderBO.getTaskId(), renderBO.getSelector(), screenshotTemp));
            }
            return tempRtList;
        } catch (Exception e) {
            log.error("Sync Generate pdf error: ", e);
            if(e instanceof PlaywrightException){
                throw ExceptionUtils.throwableOfType(e, PlaywrightException.class);
            }
            throw new PlaywrightException("Sync Generate pdf error", e);
        } finally {
            if(Objects.nonNull(browserContext)){
                browserContextPool.returnObject(browserContext);
            }
        }
    }

    protected PageScreenshotTemp pageToPdfFutureSync(BrowserContext browserContext, String rendeId, String selector, PageScreenshotTemp screenshotTemp) {
        try (Page page = browserContext.newPage()) {
            screenshotTemp.setRenderState(RenderState.GENERATING);
            log.info("Sync Generate pdf start for rendeId: {}, url : {}", rendeId, screenshotTemp.getUrl());
            PageScreenshotTemp pageToPdf = this.loadPageWithCallback(page, rendeId, selector, screenshotTemp, this.doPageToPdf(rendeId));
            log.info("Sync Generate pdf completed for rendeId: {}, url : {}, pageName: {}, fileSize: {}KB", rendeId, screenshotTemp.getUrl(), pageToPdf.getName(),
                    ByteUnitFormat.B.to(ByteUnitFormat.K, pageToPdf.getFileSize()));
            return pageToPdf;
        } catch (Exception e) {
            screenshotTemp.setRenderState(RenderState.FAIL);
            screenshotTemp.setFailedReason(String.format("页面保存为Pdf失败，失败原因：%s", e.getMessage()));
            log.error("Sync Generate pdf error: ", e);
            if(e instanceof PlaywrightException){
                throw ExceptionUtils.throwableOfType(e, PlaywrightException.class);
            }
            throw new PlaywrightException("Sync Generate pdf error", e);
        }
    }

    protected BiFunction<Page, PageScreenshotTemp, PageScreenshotTemp> doPageToPdf(String rendeId) {
        return (page, screenshotTemp) -> {
            try {
                // 定义截图输出路径
                String fileName = String.format("%s.pdf", screenshotTemp.getIndex());
                screenshotTemp.setName(fileName);
                page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.SCREEN));
                if(playwrightRenderProperties.isWriteToFile()){
                    File pdfFile = new File(playwrightRenderProperties.getTmpDir(), rendeId + File.separator + fileName);
                    log.info("Generate pdf file start for rendeId : {}, renderType : {}, to path: {}", rendeId, getRenderType(), pdfFile.getAbsolutePath());
                    Page.PdfOptions pdfOptions = playwrightProperties.getPagePdfOptions().toOptions();
                    pdfOptions.setPath(pdfFile.toPath());
                    page.pdf(pdfOptions);
                    screenshotTemp.setPath(pdfFile.getAbsolutePath());
                    screenshotTemp.setFileSize(pdfFile.length());
                    log.info("Generate pdf file success for rendeId : {}, renderType : {}, to path: {}, fileSize: {}KB", rendeId, getRenderType(), pdfFile.getAbsolutePath(),
                            ByteUnitFormat.B.to(ByteUnitFormat.K, screenshotTemp.getFileSize()));
                } else {
                    log.info("Generate pdf buffer start for rendeId : {}, renderType : {}", rendeId, getRenderType());
                    // 生成PDF
                    Page.PdfOptions pdfOptions = playwrightProperties.getPagePdfOptions().toOptions();
                    byte[] pdfBuffer = page.pdf(pdfOptions);
                    screenshotTemp.setBuffer(pdfBuffer);
                    screenshotTemp.setFileSize((long) pdfBuffer.length);
                    log.info("Generate pdf buffer success for rendeId : {}, renderType : {}, fileSize: {}KB", rendeId, getRenderType(), ByteUnitFormat.B.to(ByteUnitFormat.K, screenshotTemp.getFileSize()));
                }
                return screenshotTemp;
            } catch (Exception e) {
                log.error("Generate PDF error: ", e);
            }
            return screenshotTemp;
        };
    }

    /**
     * 定义一个图片合并为PDF方法
     * @param renderBO 渲染参数 BO
     * @param screenshots 截图列表
     * @return PDF
     */
    protected CompletableFuture<PageScreenshotTemp> mergeScreenshotsToPDF(WkhtmlRenderBO renderBO,
                                                                          List<PageScreenshotTemp> screenshots,
                                                                          BiFunction<PDDocument, List<PageScreenshotTemp>, PageScreenshotTemp> biFunction) {
        return CompletableFuture.supplyAsync(new PageScreenshotMergeToPdfSupplier(playwrightRenderProperties, renderBO, screenshots, pageScreenshotCheckers, biFunction), dtpToPdfMergeExecutor);
    }


    /**
     * 定义一个图片合并为PDF方法
     * @param renderBO 渲染参数
     * @param pdfs Pdf 列表
     */
    protected CompletableFuture<PageScreenshotTemp> mergePdfsToPDF(WkhtmlRenderBO renderBO,
                                                                   List<PageScreenshotTemp> pdfs,
                                                                   BiFunction<PDFMergerUtility, List<PageScreenshotTemp>, PageScreenshotTemp> biFunction) {
        return CompletableFuture.supplyAsync(new PagePdfMergeToPdfSupplier(playwrightRenderProperties, renderBO, pdfs, pageScreenshotCheckers, biFunction), dtpToPdfMergeExecutor);
    }

    @Override
    public void cleanTemporary(B renderBO, WkhtmlRenderResultVO resultBO) {
        log.info("clean Temporary");
        try {
            if(StringUtils.hasText(renderBO.getTaskId())){
                File fileDirectory = new File(playwrightRenderProperties.getTmpDir(), renderBO.getTaskId());
                if(fileDirectory.exists()){
                    log.info("delete Temporary Directory  : {}" , fileDirectory.getAbsolutePath());
                    FileUtils.deleteDirectory(fileDirectory);
                }
            }
            if(Objects.nonNull(resultBO) && StringUtils.hasText(resultBO.getFilePath())){
                File rtFile = new File(resultBO.getFilePath());
                log.info("delete Temporary File  : {}" , rtFile.getAbsolutePath());
                if(rtFile.exists()){
                    Files.delete(rtFile.toPath());
                }
            }
        } catch (Exception e){
            log.error("Failed to delete file", e);
        }
    }

    protected ApplicationEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    protected Sequence getSequence() {
        return sequence;
    }


}
