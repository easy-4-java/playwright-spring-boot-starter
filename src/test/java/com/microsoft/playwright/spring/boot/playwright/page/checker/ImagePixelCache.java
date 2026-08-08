package com.microsoft.playwright.spring.boot.playwright.page.checker;

import lombok.Data;

import java.awt.image.BufferedImage;

/**
 * @author [@Loong Wan](https://github.com/loong10k)
 */
@Data
public class ImagePixelCache {

    private final int width;
    private final int height;
    private final byte[] redChannel;
    private final byte[] greenChannel;
    private final byte[] blueChannel;

    public ImagePixelCache(BufferedImage image) {
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.redChannel = new byte[width * height];
        this.greenChannel = new byte[width * height];
        this.blueChannel = new byte[width * height];
        // 预缓存像素数据
        this.cacheImageData(image);
    }

    private void cacheImageData(BufferedImage image) {
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                redChannel[index] = (byte) ((rgb >> 16) & 0xFF);
                greenChannel[index] = (byte) ((rgb >> 8) & 0xFF);
                blueChannel[index] = (byte) (rgb & 0xFF);
                index++;
            }
        }
    }

}
