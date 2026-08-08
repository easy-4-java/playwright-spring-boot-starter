package com.microsoft.playwright.spring.boot.options;


import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.Data;
import org.springframework.boot.context.properties.PropertyMapper;
import com.microsoft.playwright.spring.boot.properties.PropertyMapperCompat;

@Data
public class PageWaitForSelectorOptions {

    /**
     * Defaults to {@code "visible"}. Can be either:
     * <ul>
     * <li> {@code "attached"} - wait for element to be present in DOM.</li>
     * <li> {@code "detached"} - wait for element to not be present in DOM.</li>
     * <li> {@code "visible"} - wait for element to have non-empty bounding box and no {@code visibility:hidden}. Note that element
     * without any content or with {@code display:none} has an empty bounding box and is not considered visible.</li>
     * <li> {@code "hidden"} - wait for element to be either detached from DOM, or have an empty bounding box or {@code
     * visibility:hidden}. This is opposite to the {@code "visible"} option.</li>
     * </ul>
     */
    public WaitForSelectorState state = WaitForSelectorState.ATTACHED;
    /**
     * When true, the call requires selector to resolve to a single element. If given selector resolves to more than one
     * element, the call throws an exception.
     */
    public Boolean strict;
    /**
     * Maximum time in milliseconds. Defaults to {@code 30000} (30 seconds). Pass {@code 0} to disable timeout. The default
     * value can be changed by using the {@link com.microsoft.playwright.BrowserContext#setDefaultTimeout
     * BrowserContext.setDefaultTimeout()} or {@link com.microsoft.playwright.Page#setDefaultTimeout Page.setDefaultTimeout()}
     * methods.
     */
    public Double timeout = 30 * 1000.0;

    public Page.WaitForSelectorOptions toOptions(){
        PropertyMapper map = PropertyMapperCompat.alwaysApplyingWhenNonNull();
        Page.WaitForSelectorOptions options = new Page.WaitForSelectorOptions();
        map.from(this.getTimeout()).to(options::setTimeout);
        map.from(this.getState()).to(options::setState);
        map.from(this.getStrict()).to(options::setStrict);
        return options;
    };

}
