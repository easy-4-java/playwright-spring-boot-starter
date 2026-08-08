package com.microsoft.playwright.spring.boot.playwright.strategy;

import com.microsoft.playwright.spring.boot.playwright.bo.PageScreenshotTemp;
import com.microsoft.playwright.spring.boot.playwright.bo.WkhtmlRenderBO;
import com.microsoft.playwright.spring.boot.playwright.enums.RenderType;
import com.microsoft.playwright.spring.boot.playwright.exception.TaskRuntimeException;
import com.microsoft.playwright.spring.boot.playwright.vo.WkhtmlRenderResultVO;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Playwright 渲染引擎将 HTML 渲染为 PDF 和各种图像格式
 * @author [@Loong Wan](https://github.com/loong10k)
 */
@Slf4j
public class WkhtmlToPdfBufferRenderStrategy extends WkhtmlToImageBufferRenderStrategy {

    @Override
    public RenderType getRenderType() {
        return RenderType.TO_PDF_BUFFER;
    }

    @Override
    public WkhtmlRenderResultVO doPacking(WkhtmlRenderBO renderBO, List<PageScreenshotTemp> screenshots) throws IOException {
        WkhtmlRenderResultVO resultBO = new WkhtmlRenderResultVO();
        this.mergeScreenshotsToPDF(renderBO, screenshots)
                .thenAccept(bufferTemp -> {
                    resultBO.setFileBuffer(bufferTemp.getBuffer());
                    resultBO.setFileName(bufferTemp.getName());
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
            // 使用缓冲输出流保存文件
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                sourceDocument.save(outputStream);
                return new PageScreenshotTemp().setIndex(0).setName(pdfFileName).setBuffer(outputStream.toByteArray());
            } catch (IOException e) {
                throw new TaskRuntimeException("Failed to merge PDF File : " + pdfFileName, e);
            }
        });
    }




}
