package com.microsoft.playwright.spring.boot.playwright.page.checker;

import com.microsoft.playwright.spring.boot.playwright.bo.PageScreenshotTemp;
import com.microsoft.playwright.spring.boot.playwright.enums.PDPageSize;
import org.springframework.core.Ordered;

import java.awt.image.BufferedImage;

/**
 * 页面截图检查器
 * @author [@Loong Wan](https://github.com/loong10k)
 */
public interface PageScreenshotChecker extends Ordered, Comparable<PageScreenshotChecker> {

    /**
     * 页面截图后检查，用于检查页面截图是否符合要求
     * @param urlTemp 页面截图信息
     * @return 是否检查通过
     */
    default boolean afterPageScreenShot(PageScreenshotTemp urlTemp) {
        return true;
    }

    /**
     * 添加PDF页面前检查，用于检查PDF图片是否符合要求
     * @param urlTemp 页面截图信息
     * @param pdfImage PDF图片
     * @param pdfPageSize 页面大小
     * @param maxSingleColorPercent 最大单色占比
     * @param maxSimilarity 最大相似度
     * @return 是否检查通过
     */
    default boolean beforePdfPageAdd(PageScreenshotTemp urlTemp, BufferedImage pdfImage, PDPageSize pdfPageSize, float maxSingleColorPercent, float maxSimilarity){
        return true;
    }

    @Override
    default int compareTo(PageScreenshotChecker o){
        return Integer.compare(this.getOrder(), o.getOrder());
    }

}
