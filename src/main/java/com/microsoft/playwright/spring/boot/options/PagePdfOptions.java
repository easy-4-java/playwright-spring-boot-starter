package com.microsoft.playwright.spring.boot.options;


import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Margin;
import lombok.Data;
import org.springframework.boot.context.properties.PropertyMapper;
import com.microsoft.playwright.spring.boot.properties.PropertyMapperCompat;

import java.nio.file.Path;

@Data
public class PagePdfOptions {

    /**
     * Display header and footer. Defaults to {@code false}.
     */
    public Boolean displayHeaderFooter;
    /**
     * HTML template for the print footer. Should use the same format as the {@code headerTemplate}.
     */
    public String footerTemplate;
    /**
     * Paper format. If set, takes priority over {@code width} or {@code height} options. Defaults to 'Letter'.
     */
    public String format;
    /**
     * HTML template for the print header. Should be valid HTML markup with following classes used to inject printing values
     * into them:
     * <ul>
     * <li> {@code "date"} formatted print date</li>
     * <li> {@code "title"} document title</li>
     * <li> {@code "url"} document location</li>
     * <li> {@code "pageNumber"} current page number</li>
     * <li> {@code "totalPages"} total pages in the document</li>
     * </ul>
     */
    public String headerTemplate;
    /**
     * Paper height, accepts values labeled with units.
     */
    public String height;
    /**
     * Paper orientation. Defaults to {@code false}.
     */
    public Boolean landscape;
    /**
     * Paper margins, defaults to none.
     */
    public Margin margin;
    /**
     * Whether or not to embed the document outline into the PDF. Defaults to {@code false}.
     */
    public Boolean outline;
    /**
     * Paper ranges to print, e.g., '1-5, 8, 11-13'. Defaults to the empty string, which means print all pages.
     */
    public String pageRanges;
    /**
     * The file path to save the PDF to. If {@code path} is a relative path, then it is resolved relative to the current
     * working directory. If no path is provided, the PDF won't be saved to the disk.
     */
    public Path path;
    /**
     * Give any CSS {@code @page} size declared in the page priority over what is declared in {@code width} and {@code height}
     * or {@code format} options. Defaults to {@code false}, which will scale the content to fit the paper size.
     */
    public Boolean preferCssPageSize;
    /**
     * Print background graphics. Defaults to {@code false}.
     */
    public Boolean printBackground;
    /**
     * Scale of the webpage rendering. Defaults to {@code 1}. Scale amount must be between 0.1 and 2.
     */
    public Double scale;
    /**
     * Whether or not to generate tagged (accessible) PDF. Defaults to {@code false}.
     */
    public Boolean tagged;
    /**
     * Paper width, accepts values labeled with units.
     */
    public String width;

    public Page.PdfOptions toOptions(){
        PropertyMapper map = PropertyMapperCompat.alwaysApplyingWhenNonNull();
        Page.PdfOptions options = new Page.PdfOptions();
        map.from(this.getDisplayHeaderFooter()).to(options::setDisplayHeaderFooter);
        map.from(this.getFooterTemplate()).whenHasText().to(options::setFooterTemplate);
        map.from(this.getFormat()).whenHasText().to(options::setFormat);
        map.from(this.getHeaderTemplate()).whenHasText().to(options::setHeaderTemplate);
        map.from(this.getHeight()).whenHasText().to(options::setHeight);
        map.from(this.getLandscape()).to(options::setLandscape);
        map.from(this.getMargin()).to(options::setMargin);
        map.from(this.getOutline()).to(options::setOutline);
        map.from(this.getPageRanges()).whenHasText().to(options::setPageRanges);
        map.from(this.getPreferCssPageSize()).to(options::setPreferCSSPageSize);
        map.from(this.getPrintBackground()).to(options::setPrintBackground);
        map.from(this.getScale()).to(options::setScale);
        map.from(this.getTagged()).to(options::setTagged);
        map.from(this.getWidth()).whenHasText().to(options::setWidth);
        return options;
    };

}
