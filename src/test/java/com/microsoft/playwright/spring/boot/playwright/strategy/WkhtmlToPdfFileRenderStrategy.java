package com.microsoft.playwright.spring.boot.playwright.strategy;

import com.microsoft.playwright.spring.boot.playwright.bo.PageScreenshotTemp;
import com.microsoft.playwright.spring.boot.playwright.bo.WkhtmlRenderBO;
import com.microsoft.playwright.spring.boot.playwright.enums.RenderType;
import com.microsoft.playwright.spring.boot.playwright.exception.TaskRuntimeException;
import com.microsoft.playwright.spring.boot.playwright.vo.WkhtmlRenderResultVO;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Playwright 渲染引擎将 HTML 渲染为 PDF 和各种图像格式
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
@Slf4j
public class WkhtmlToPdfFileRenderStrategy extends WkhtmlToImageFileRenderStrategy {

    @Override
    public RenderType getRenderType() {
        return RenderType.TO_PDF_FILE;
    }

    @Override
    public WkhtmlRenderResultVO doPacking(WkhtmlRenderBO renderBO, List<PageScreenshotTemp> screenshots) throws IOException {
        WkhtmlRenderResultVO resultBO = new WkhtmlRenderResultVO();
        this.mergeScreenshotsToPDF(renderBO, screenshots).thenAccept(bufferTemp -> {
            resultBO.setFileName(bufferTemp.getName());
            resultBO.setFilePath(bufferTemp.getPath());
        }).join();
        return resultBO;
    }

    /**
     * 定义一个图片合并为PDF方法
     * @param renderBO 渲染参数
     * @param screenshots 截图列表
     * @return 合并后的PDF文件
     */
    protected CompletableFuture<PageScreenshotTemp> mergeScreenshotsToPDF(WkhtmlRenderBO renderBO,
                                                                          List<PageScreenshotTemp> screenshots) {
        return this.mergeScreenshotsToPDF(renderBO, screenshots, (sourceDocument, screenshot) -> {
            String pdfFileName = "document_" + renderBO.getTaskId() + ".pdf";
            log.info("Merging screenshots to PDF: {}", pdfFileName);
            File pdfFile = new File(playwrightRenderProperties.getTmpDir(), pdfFileName);
            try (BufferedOutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(pdfFile.toPath()), 2048)) {
                sourceDocument.save(outputStream);
            } catch (IOException e) {
                throw new TaskRuntimeException("Failed to merge screenshots to PDF File : " + pdfFileName, e);
            }
            return new PageScreenshotTemp()
                    .setIndex(0)
                    .setName(pdfFileName)
                    .setPath(pdfFile.getAbsolutePath());
        });
    }


}
