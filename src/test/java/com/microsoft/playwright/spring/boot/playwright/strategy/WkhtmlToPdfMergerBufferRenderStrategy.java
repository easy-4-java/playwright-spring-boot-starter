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
import java.util.stream.Collectors;

/**
 * Playwright 渲染引擎将 HTML 渲染为 PDF 和各种图像格式
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
@Slf4j
public class WkhtmlToPdfMergerBufferRenderStrategy extends AbstractPlaywrightRenderStrategy<WkhtmlRenderBO> {

    @Override
    public RenderType getRenderType() {
        return RenderType.TO_PDF_MERGER_BUFFER;
    }

    @Override
    protected List<PageScreenshotTemp> doGenerate(WkhtmlRenderBO renderBO) throws IOException {
        log.info("Generate PDF for urls: {}", renderBO.getUrls().stream().map(PageScreenshotTemp::getUrl).collect(Collectors.toList()));
        if(renderBO.isAsync()){
            return this.pageToPdfFutureAsync(renderBO);
        } else {
            return this.pageToPdfFutureSync(renderBO);
        }
    }

    @Override
    protected List<PageScreenshotTemp> doCompress(WkhtmlRenderBO renderBO, List<PageScreenshotTemp> pdfs) {
        // 1、获取压缩质量，如果压缩质量不在范围内，则不压缩
        Integer quality = renderBO.getQuality();
        if((quality > MAX_QUALITY || quality < MIN_QUALITY)){
            log.info("Compressing pdf ignore.");
            return pdfs;
        }
        // 2、异步压缩pdf
        return pdfs;
    }

    @Override
    protected WkhtmlRenderResultVO doPacking(WkhtmlRenderBO renderBO, List<PageScreenshotTemp> pdfs) throws IOException {
        WkhtmlRenderResultVO resultBO = new WkhtmlRenderResultVO();
        this.mergePdfsToPDF(renderBO, pdfs).thenAccept(bufferTemp -> {
            resultBO.setFileBuffer(bufferTemp.getBuffer());
            resultBO.setFilePath(bufferTemp.getPath());
            resultBO.setFileName(bufferTemp.getName());
        }).join();
        return resultBO;
    }

    /**
     * 定义一个PDF合并为PDF方法
     * @param renderBO 渲染参数
     * @param pdfs Pdf 列表
     * @return 合并后的PDF文件
     */
    protected CompletableFuture<PageScreenshotTemp> mergePdfsToPDF(WkhtmlRenderBO renderBO,
                                                                  List<PageScreenshotTemp> pdfs) {
        return this.mergePdfsToPDF(renderBO, pdfs, (mergePdf, screenshot) -> {
            // 设置合并生成pdf文件名称
            String pdfFileName = "document_" + renderBO.getTaskId() + ".pdf";
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                mergePdf.setDestinationStream(outputStream);
                // 使用主内存进行PDF合并处理
                //mergePdf.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
                // 或者使用磁盘临时文件进行处理
                //mergePdf.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
                // 或者结合使用主内存和磁盘临时文件进行处理（这里设置8MB）
                //mergePdf.mergeDocuments(MemoryUsageSetting.setupMixed(8 * 1024 * 1024));
                // Since v3.0.2
                mergePdf.mergeDocuments(null);
                // 返回合并后的pdf文件
                return new PageScreenshotTemp().setIndex(0).setName(pdfFileName).setBuffer(outputStream.toByteArray());
            }  catch (IOException e) {
                throw new TaskRuntimeException("Failed to merge PDF File : " + pdfFileName, e);
            }
        });
    }

}
