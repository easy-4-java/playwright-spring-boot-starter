package com.microsoft.playwright.spring.boot.options;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotAnimations;
import com.microsoft.playwright.options.ScreenshotCaret;
import com.microsoft.playwright.options.ScreenshotScale;
import com.microsoft.playwright.options.ScreenshotType;
import lombok.Data;
import org.springframework.boot.context.properties.PropertyMapper;
import com.microsoft.playwright.spring.boot.properties.PropertyMapperCompat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@Data
public class ElementScreenshotOptions {

    /**
     * When set to {@code "disabled"}, stops CSS animations, CSS transitions and Web Animations. Animations get different
     * treatment depending on their duration:
     * <ul>
     * <li> finite animations are fast-forwarded to completion, so they'll fire {@code transitionend} event.</li>
     * <li> infinite animations are canceled to initial state, and then played over after the screenshot.</li>
     * </ul>
     *
     * <p> Defaults to {@code "allow"} that leaves animations untouched.
     */
    public ScreenshotAnimations animations;
    /**
     * When set to {@code "hide"}, screenshot will hide text caret. When set to {@code "initial"}, text caret behavior will not
     * be changed.  Defaults to {@code "hide"}.
     */
    public ScreenshotCaret caret;
    /**
     * Specify locators that should be masked when the screenshot is taken. Masked elements will be overlaid with a pink box
     * {@code #FF00FF} (customized by {@code maskColor}) that completely covers its bounding box.
     */
    public List<Locator> mask;
    /**
     * Specify the color of the overlay box for masked elements, in <a
     * href="https://developer.mozilla.org/en-US/docs/Web/CSS/color_value">CSS color format</a>. Default color is pink {@code
     * #FF00FF}.
     */
    public String maskColor;
    /**
     * Hides default white background and allows capturing screenshots with transparency. Not applicable to {@code jpeg}
     * images. Defaults to {@code true}.
     */
    public Boolean omitBackground = Boolean.TRUE;
    /**
     * The file path to save the image to. The screenshot type will be inferred from file extension. If {@code path} is a
     * relative path, then it is resolved relative to the current working directory. If no path is provided, the image won't be
     * saved to the disk.
     */
    public Path path;
    /**
     * The quality of the image, between 0-100. Not applicable to {@code png} images.
     */
    public Integer quality = 100;
    /**
     * When set to {@code "css"}, screenshot will have a single pixel per each css pixel on the page. For high-dpi devices,
     * this will keep screenshots small. Using {@code "device"} option will produce a single pixel per each device pixel, so
     * screenshots of high-dpi devices will be twice as large or even larger.
     *
     * <p> Defaults to {@code "device"}.
     */
    public ScreenshotScale scale = ScreenshotScale.DEVICE;
    /**
     * Maximum time in milliseconds. Defaults to {@code 30000} (30 seconds). Pass {@code 0} to disable timeout. The default
     * value can be changed by using the {@link BrowserContext#setDefaultTimeout BrowserContext.setDefaultTimeout()} or {@link
     * Page#setDefaultTimeout Page.setDefaultTimeout()} methods.
     */
    public Double timeout = 30 * 1000.0;
    /**
     * Specify screenshot type, defaults to {@code png}.
     */
    public ScreenshotType type = ScreenshotType.PNG;

    public ElementHandle.ScreenshotOptions toOptions(){
        PropertyMapper map = PropertyMapperCompat.alwaysApplyingWhenNonNull();
        ElementHandle.ScreenshotOptions options = new ElementHandle.ScreenshotOptions();
        map.from(this.getAnimations()).to(options::setAnimations);
        map.from(this.getCaret()).to(options::setCaret);
        map.from(this.getMask()).to(options::setMask);
        map.from(this.getMaskColor()).whenHasText().to(options::setMaskColor);
        map.from(this.getOmitBackground()).to(options::setOmitBackground);
        map.from(this.getPath()).to(options::setPath);
        map.from(this.getQuality()).when((quality) -> Objects.nonNull(this.getType()) && this.getType() != ScreenshotType.PNG && Objects.nonNull(quality)).to(options::setQuality);
        map.from(this.getScale()).to(options::setScale);
        map.from(this.getTimeout()).to(options::setTimeout);
        map.from(this.getType()).to(options::setType);
        return options;
    };

}
