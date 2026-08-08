package com.microsoft.playwright.spring.boot.playwright.strategy;


import com.microsoft.playwright.spring.boot.playwright.bo.PageScreenshotTemp;
import com.microsoft.playwright.spring.boot.playwright.bo.WkhtmlRenderBO;
import com.microsoft.playwright.spring.boot.playwright.enums.RenderType;
import com.microsoft.playwright.spring.boot.playwright.exception.TaskRuntimeException;
import com.microsoft.playwright.spring.boot.playwright.page.supplier.PageScreenshotMergeToZipOutputStreamSupplier;
import com.microsoft.playwright.spring.boot.playwright.vo.WkhtmlRenderResultVO;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 使用 Playwright 渲染引擎将 HTML 渲染为各种图像格式
 * @author [@Loong Wan](https://github.com/loong10k)
 */
@Slf4j
public class WkhtmlToImageBufferRenderStrategy extends AbstractPlaywrightRenderStrategy<WkhtmlRenderBO> {

    @Override
    public RenderType getRenderType() {
        return RenderType.TO_IMAGE_BUFFER;
    }

    @Override
    protected List<PageScreenshotTemp> doGenerate(WkhtmlRenderBO renderBO) throws IOException {
        log.info("Capturing screenshots for urls: {}", renderBO.getUrls().stream().map(PageScreenshotTemp::getUrl).collect(Collectors.toList()));
        if(renderBO.isAsync()){
            return this.captureScreenshotAsync(renderBO);
        } else {
            return this.captureScreenshotSync(renderBO);
        }
    }

    @Override
    protected List<PageScreenshotTemp> doCompress(WkhtmlRenderBO renderBO, List<PageScreenshotTemp> screenshots) {
        // 1、获取压缩质量，如果压缩质量不在范围内，则不压缩
        Integer quality = renderBO.getQuality();
        if((quality > MAX_QUALITY || quality <= MIN_QUALITY)){
            log.info("Compressing screenshot ignore.");
            return screenshots;
        }
        // 2、异步压缩图片
        return compressScreenshots(screenshots, quality);
    }

    /**
     * 定义一个图片压缩方法
     * @param screenshots 截图列表
     * @param quality 压缩质量
     * @return 压缩后的截图
     */
    protected List<PageScreenshotTemp> compressScreenshots(List<PageScreenshotTemp> screenshots, Integer quality) {
        if((quality > MAX_QUALITY || quality < MIN_QUALITY)){
            log.info("Compressing screenshot ignore.");
            return screenshots;
        }
        // 1、使用CompletableFuture异步处理
        List<CompletableFuture<PageScreenshotTemp>> futureList = screenshots.stream()
                .map(screenshot -> compressScreenshot(screenshot, quality))
                .collect(Collectors.toList());
        // 2、使用CompletableFuture.allOf()方法，等待所有异步线程执行完毕
        CompletableFuture<Void> allFuture = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]));
        CompletableFuture<List<PageScreenshotTemp>> resultFuture = allFuture
                .thenApply(v -> futureList.stream().map(CompletableFuture::join).collect(Collectors.toList()));
        return resultFuture.join();
    }

    /**
     * 定义一个图片压缩方法
     * @param screenshot 截图
     * @param quality 压缩质量
     * @return 压缩后的截图
     */
    protected CompletableFuture<PageScreenshotTemp> compressScreenshot(PageScreenshotTemp screenshot, Integer quality) {
        // 判断压缩质量和压缩比例是否在范围内
        if(quality < MAX_QUALITY && quality > MIN_QUALITY){
            return CompletableFuture.supplyAsync(() -> {
                log.info("Compressing screenshot buffer: {}", screenshot.getName());
                // 使用Thumbnails进行图片压缩
                try(ByteArrayInputStream input = new ByteArrayInputStream(screenshot.getBuffer());
                    ByteArrayOutputStream output = new ByteArrayOutputStream() ){
                    // 从图片流中读取图片
                    Thumbnails.of(input)
                            .allowOverwrite(true)
                            .scale(1f)
                            .outputQuality(quality / 100f)
                            .toOutputStream(output);
                    screenshot.setBuffer(output.toByteArray());
                    log.info("Compressing screenshot buffer success : {}", screenshot.getName());
                } catch (Exception e) {
                    throw new TaskRuntimeException("Compressing screenshot buffer error: " , e);
                }
                return screenshot ;
            }, dtpToImageCompressExecutor);
        }
        log.info("Compressing screenshot ignore: {}", screenshot.getName());
        return CompletableFuture.completedFuture(screenshot);
    }

    @Override
    public WkhtmlRenderResultVO doPacking(WkhtmlRenderBO renderBO, List<PageScreenshotTemp> screenshots) throws IOException {
        WkhtmlRenderResultVO resultBO = new WkhtmlRenderResultVO();
        this.mergeScreenshotsToZip(renderBO, screenshots).thenAccept(bufferTemp -> {
            resultBO.setFileBuffer(bufferTemp.getBuffer());
            resultBO.setFileName(bufferTemp.getName());
        }).join();
        return resultBO;
    }

    /**
     * 定义一个图片合并为Zip方法
     * @param renderBO 渲染参数
     * @param screenshots 截图列表
     * @return 打包后的Zip文件
     */
    protected CompletableFuture<PageScreenshotTemp> mergeScreenshotsToZip(WkhtmlRenderBO renderBO, List<PageScreenshotTemp> screenshots) {
        return CompletableFuture.supplyAsync(new PageScreenshotMergeToZipOutputStreamSupplier(playwrightRenderProperties, renderBO, screenshots), dtpToImageZipExecutor);
    }

}
