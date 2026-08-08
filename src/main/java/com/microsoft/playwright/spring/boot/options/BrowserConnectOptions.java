package com.microsoft.playwright.spring.boot.options;

import com.microsoft.playwright.BrowserType;
import lombok.Data;
import org.springframework.boot.context.properties.PropertyMapper;
import com.microsoft.playwright.spring.boot.properties.PropertyMapperCompat;

import java.util.Map;

@Data
public class BrowserConnectOptions {

    /**
     * Additional HTTP headers to be sent with web socket connect request. Optional.
     */
    public Map<String, String> headers;
    /**
     * Slows down Playwright operations by the specified amount of milliseconds. Useful so that you can see what is going on.
     * Defaults to 0.
     */
    public Double slowMo = 0.0;
    /**
     * Maximum time in milliseconds to wait for the connection to be established. Defaults to {@code 0} (no timeout).
     */
    public Double timeout = 0.0;

    public BrowserType.ConnectOptions toOptions() {
        PropertyMapper map = PropertyMapperCompat.alwaysApplyingWhenNonNull();
        BrowserType.ConnectOptions options = new BrowserType.ConnectOptions();
        map.from(this.getHeaders()).to(options::setHeaders);
        map.from(this.getSlowMo()).to(options::setSlowMo);
        map.from(this.getTimeout()).to(options::setTimeout);
        return options;
    }

}
