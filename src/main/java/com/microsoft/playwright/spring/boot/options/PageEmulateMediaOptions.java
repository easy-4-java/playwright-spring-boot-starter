package com.microsoft.playwright.spring.boot.options;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.ForcedColors;
import com.microsoft.playwright.options.Media;
import com.microsoft.playwright.options.ReducedMotion;
import lombok.Data;
import org.springframework.boot.context.properties.PropertyMapper;
import com.microsoft.playwright.spring.boot.properties.PropertyMapperCompat;

@Data
public class PageEmulateMediaOptions {

    /**
     * Emulates {@code "prefers-colors-scheme"} media feature, supported values are {@code "light"}, {@code "dark"}, {@code
     * "no-preference"}. Passing {@code null} disables color scheme emulation.
     */
    public ColorScheme colorScheme;
    /**
     * Emulates {@code "forced-colors"} media feature, supported values are {@code "active"} and {@code "none"}. Passing {@code
     * null} disables forced colors emulation.
     */
    public ForcedColors forcedColors;
    /**
     * Changes the CSS media type of the page. The only allowed values are {@code "screen"}, {@code "print"} and {@code null}.
     * Passing {@code null} disables CSS media emulation.
     */
    public Media media;
    /**
     * Emulates {@code "prefers-reduced-motion"} media feature, supported values are {@code "reduce"}, {@code "no-preference"}.
     * Passing {@code null} disables reduced motion emulation.
     */
    public ReducedMotion reducedMotion;

    public Page.EmulateMediaOptions toOptions() {
        PropertyMapper map = PropertyMapperCompat.alwaysApplyingWhenNonNull();
        Page.EmulateMediaOptions options = new Page.EmulateMediaOptions();
        map.from(this.getColorScheme()).to(options::setColorScheme);
        map.from(this.getForcedColors()).to(options::setForcedColors);
        map.from(this.getMedia()).to(options::setMedia);
        map.from(this.getReducedMotion()).to(options::setReducedMotion);
        return options;
    }

}
