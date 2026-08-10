package com.microsoft.playwright.spring.boot.playwright.strategy;


import com.microsoft.playwright.spring.boot.playwright.bo.PageScreenshotTemp;
import com.microsoft.playwright.spring.boot.playwright.bo.WkhtmlRenderBO;
import com.microsoft.playwright.spring.boot.playwright.enums.RenderType;
import com.microsoft.playwright.spring.boot.playwright.exception.TaskRuntimeException;
import com.microsoft.playwright.spring.boot.playwright.page.supplier.PageScreenshotMergeToZipFileSupplier;
import com.microsoft.playwright.spring.boot.playwright.page.supplier.PageScreenshotPackToZipFileSupplier;
import com.microsoft.playwright.spring.boot.playwright.vo.WkhtmlRenderResultVO;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 使用 Playwright 渲染引擎将 HTML 渲染为各种图像格式
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
@Slf4j
public class WkhtmlToImageFileRenderStrategy extends WkhtmlToImageBufferRenderStrategy {

    @Override
    public RenderType getRenderType() {
        return RenderType.TO_IMAGE_FILE;
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
     * @param screenshot 截图
     * @param quality 压缩质量
     * @return 压缩后的截图
     */
    @Override
    protected CompletableFuture<PageScreenshotTemp> compressScreenshot(PageScreenshotTemp screenshot, Integer quality) {
        // 判断压缩质量是否在范围内
        if(quality < MAX_QUALITY && quality > MIN_QUALITY){
            return CompletableFuture.supplyAsync(() -> {
                try{
                    log.info("Compressing screenshot file : {}", screenshot.getPath());
                    File sourceFile = new File(screenshot.getPath());
                    File outFile = new File(playwrightRenderProperties.getTmpDir(), screenshot.getName());
                    Thumbnails.of(sourceFile)
                            .allowOverwrite(true)
                            .scale(1f)
                            .outputQuality(quality / 100f)
                            .toFile(outFile);
                    screenshot.setPath(outFile.getAbsolutePath());
                    log.info("Compressing screenshot file success : {}", screenshot.getName());
                } catch (Exception e) {
                    throw new TaskRuntimeException("Compressing screenshot file error: " +  e.getMessage());
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
        // 1、判断操作系统，如果是windows，则使用mergeScreenshotsToZip方法，否则使用packScreenshotsToZip方法
        if(SystemUtils.IS_OS_WINDOWS){
            this.mergeScreenshotsToZip(renderBO, screenshots).thenAccept(bufferTemp -> {
                resultBO.setFilePath(bufferTemp.getPath());
                resultBO.setFileName(bufferTemp.getName());
            }).join();
        } else {
            this.packScreenshotsToZip(renderBO, screenshots).thenAccept(bufferTemp -> {
                resultBO.setFilePath(bufferTemp.getPath());
                resultBO.setFileName(bufferTemp.getName());
            }).join();;
        }
        return resultBO;
    }

    /**
     * 定义一个图片打包为Zip方法
     * @param renderBO 渲染参数
     * @param screenshots 截图列表
     * @return 打包后的Zip文件
     */
    @Override
    protected CompletableFuture<PageScreenshotTemp> mergeScreenshotsToZip(WkhtmlRenderBO renderBO, List<PageScreenshotTemp> screenshots) {
        return CompletableFuture.supplyAsync(new PageScreenshotMergeToZipFileSupplier(playwrightRenderProperties, renderBO, screenshots), dtpToImageZipExecutor);
    }

    /**
     * 定义一个图片打包为Zip方法
     * @param renderBO 渲染参数
     * @param screenshots 截图列表
     * @return 打包后的Zip文件
     */
    protected CompletableFuture<PageScreenshotTemp> packScreenshotsToZip(WkhtmlRenderBO renderBO, List<PageScreenshotTemp> screenshots) {
        return CompletableFuture.supplyAsync(new PageScreenshotPackToZipFileSupplier(playwrightRenderProperties, renderBO, screenshots), dtpToImageZipExecutor);
    }

}
