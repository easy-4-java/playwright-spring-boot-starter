package com.microsoft.playwright.spring.boot.playwright.enums;

import lombok.Getter;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * PDF 页面大小，LETTER, LEGAL, A0, A1, A2, A3, A4, A5, A6
 * @author [@Loong Wan](https://github.com/loong10k)
 */
public enum PDPageSize {

    LETTER(PDRectangle.LETTER),
    LEGAL(PDRectangle.LEGAL),
    A0(PDRectangle.A0),
    A1(PDRectangle.A1),
    A2(PDRectangle.A2),
    A3(PDRectangle.A3),
    A4(PDRectangle.A4),
    A5(PDRectangle.A5),
    A6(PDRectangle.A6);

    @Getter
    private PDRectangle rectangle;

    PDPageSize(PDRectangle rectangle) {
        this.rectangle = rectangle;
    }

    public static PDPageSize getByName(String name) {
        for (PDPageSize value : PDPageSize.values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return null;
    }

    public static PDRectangle getPDRectangleByName(String name) {
        for (PDPageSize value : PDPageSize.values()) {
            if (value.name().equals(name)) {
                return value.getRectangle();
            }
        }
        return null;
    }

}
