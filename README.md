# playwright-spring-boot-starter

#### 组件简介

> 基于 [Playwright ](https://github.com/microsoft/playwright) + [Playwright For Java ](https://github.com/microsoft/playwright-java) + commons-pool2 整合的 Starter

> Playwright  [ˈpleɪraɪt]，译意为剧作家，是一个用于自动化浏览器操作的开源工具和框架。它由Microsoft开发，旨在简化浏览器自动化和测试的过程。

- 跨浏览器。Playwright 支持所有现代渲染引擎，包括 Chromium、WebKit（Safari） 和 Firefox。

- 跨平台。在 Windows、Linux 和 macOS 上进行本地测试或在 CI 上进行无头或有头测试。

- 跨语言。在TypeScript、JavaScript、Python、.NET、Java中使用 Playwright API 。

- 测试移动网络。适用于 Android 和 Mobile Safari 的 Google Chrome 浏览器的本机移动仿真。相同的渲染引擎适用于您的桌面和云端。
 
#### 使用说明

##### 1、Spring Boot 项目添加 Maven 依赖

``` xml
<dependency>
	<groupId>io.github.easy4j</groupId>
	<artifactId>playwright-spring-boot-starter</artifactId>
	<version>${project.version}</version>
</dependency>
```

##### 2、使用示例

在`application.yml`文件中增加如下配置

```yaml
################################################################################################################
###Playwright（PlaywrightProperties）配置：
################################################################################################################
playwright:
  browser-pool:
    max-idle: 5
    min-idle: 40
    max-total: 40
    test-on-borrow: true
    test-while-idle: true
    test-on-return: true
  launch-options:
    headless: true
    args:
      - '--start-maximized'
      - '--ignore-certificate-errors'
  new-context-options:
    ignore-https-errors: true
```

创建Java对象 BufferTemp，用于存储处理过程数据

```java
@Data
@Builder
public class BufferTemp {

    /**
     * 原始序号
     */
    private int index;
    /**
     * 原始请求URL
     */
    private String url;
    /**
     * 文件名称
     */
    private String name;
    /**
     * 是否保存到文件
     */
    private Boolean toFile;
    /**
     * 文件存储路径
     */
    private String path;
    /**
     * 截图缓存
     */
    private byte[] buffer;

    public static BufferTemp.BufferTempBuilder from(BufferTemp temp) {
        return BufferTemp.builder()
                .index(temp.getIndex())
                .url(temp.getUrl())
                .buffer(temp.getBuffer())
                .name(temp.getName());
    }

}
```

基于Buffer调用示例（提供了基于CompletableFuture的批量生成pdf和单个生成pdf方法，如果希望合并pdf，可自行搜索 pdfbox、itext相关资料）：
：

```java
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Media;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.spring.boot.pool.BrowserContextPool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@SpringBootApplication
@Slf4j
public class PlaywrightApplication_Test1 implements CommandLineRunner {

    protected static final String BASE_DIR = "D://tmp";
    @Autowired
    private BrowserContextPool browserContextPool;

    public static void main(String[] args) throws Exception {
        SpringApplication.run(PlaywrightApplication_Test1.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            FileUtils.forceMkdir( new File(BASE_DIR));
            // 截图测试
            captureScreenshot(UUID.randomUUID().toString(), BufferTemp.builder().url("https://www.baidu.com").index(1).build(), null).whenComplete((pdf, throwable) -> {
                try(ByteArrayInputStream input = new ByteArrayInputStream(pdf.getBuffer());) {
                    IOUtils.copy(input, new FileOutputStream(new File(BASE_DIR, "test.png")));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).exceptionally(throwable -> {
                throwable.printStackTrace();
                return null;
            }).join();

            // 生成pdf测试
            pageToPdf(UUID.randomUUID().toString(), BufferTemp.builder().url("https://www.baidu.com").index(1).build()).whenComplete((pdf, throwable) -> {
                try(ByteArrayInputStream input = new ByteArrayInputStream(pdf.getBuffer());) {
                    IOUtils.copy(input, new FileOutputStream(new File(BASE_DIR, "test.pdf")));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).exceptionally(throwable -> {
                throwable.printStackTrace();
                return null;
            }).join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 定义一个浏览器内容截图方法
     * @param rendeId
     * @param urlTemps
     * @param selector
     * @return
     */
    protected List<BufferTemp> captureScreenshots(String rendeId, List<BufferTemp> urlTemps, String selector) {
        log.info("Capturing screenshots for urls: ", urlTemps.stream().map(BufferTemp::getUrl).collect(Collectors.toList()));
        // 1、使用CompletableFuture异步处理
        List<CompletableFuture<BufferTemp>> futureList = urlTemps.stream()
                .map(urlTemp -> captureScreenshot(rendeId, urlTemp, selector))
                .collect(Collectors.toList());
        // 2、使用CompletableFuture.allOf()方法，等待所有异步线程执行完毕
        CompletableFuture<Void> allFuture = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()]));
        CompletableFuture<List<BufferTemp>> resultFuture = allFuture
                .thenApply(v -> futureList.stream().map(future -> future.join())
                        .collect(Collectors.toList()));
        return resultFuture.join();
    }

    /**
     * 定义一个浏览器页面截图方法
     * @param rendeId
     * @param urlTemp
     * @param selector
     * @return
     */
    protected CompletableFuture<BufferTemp> captureScreenshot(String rendeId, BufferTemp urlTemp, String selector){
        // 1、使用CompletableFuture.supplyAsync()方法，异步执行截图
        return CompletableFuture.supplyAsync(() -> {
            log.info("Capturing screenshot : rendeId: {}, selector: {}, url : {}", rendeId, selector, urlTemp.getUrl());
            Page page = null;
            try {
                // 从池中获取一个浏览器页面
                page = browserContextPool.borrowObject().newPage();
                //page = browserPagePool.borrowObject();
                // 设置页面加载参数, 并跳转到url
                page.navigate(urlTemp.getUrl(), new Page.NavigateOptions()
                        .setTimeout(60 * 1000)
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));
                // 定义截图输出路径
                String fileName = String.format("%s.png", urlTemp.getIndex());
                log.info("screenshot start for {} : {}", rendeId, fileName);
                // 截图
                byte[] screenshotBuffer;
                if(StringUtils.isEmpty(selector)){
                    Page.ScreenshotOptions options = new Page.ScreenshotOptions()
                            .setFullPage(true)
                            .setOmitBackground(true)
                            .setTimeout(30 * 1000)
                            .setType(ScreenshotType.PNG);
                    screenshotBuffer = page.screenshot(options);
                } else {

                    // 定位到要截图的元素
                    ElementHandle element = page.querySelector(selector);

                    ElementHandle.ScreenshotOptions options = new ElementHandle.ScreenshotOptions()
                            .setOmitBackground(true)
                            .setTimeout(30 * 1000)
                            .setType(ScreenshotType.PNG);

                    // 截取指定元素的屏幕截图
                    screenshotBuffer = element.screenshot(options);
                }
                log.info("screenshot success for {} : {}", rendeId, fileName);
                urlTemp.setBuffer(screenshotBuffer);
                urlTemp.setName(fileName);
                browserContextPool.returnObject(page.context());
                //browserPagePool.returnObject(page);
                return urlTemp;
            } catch (Exception e) {
                throw new RuntimeException("Capture screenshot error: {}", e);
            } finally {
                try {
                    if (Objects.nonNull(page) && !page.isClosed()){
                        page.close();
                    }
                } catch (Exception e) {
                    // ignore error
                }
            }
        });

    }

    /**
     * 定义一个浏览器内容截图方法
     * @param rendeId
     * @param urlTemps
     * @return
     */
    protected List<BufferTemp> pageToPdfs(String rendeId, List<BufferTemp> urlTemps) {
        log.info("Capturing screenshots for urls: ", urlTemps.stream().map(BufferTemp::getUrl).collect(Collectors.toList()));
        // 1、使用CompletableFuture异步处理
        List<CompletableFuture<BufferTemp>> futureList = urlTemps.stream()
                .map(urlTemp -> pageToPdf(rendeId, urlTemp))
                .collect(Collectors.toList());
        // 2、使用CompletableFuture.allOf()方法，等待所有异步线程执行完毕
        CompletableFuture<Void> allFuture = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()]));
        CompletableFuture<List<BufferTemp>> resultFuture = allFuture
                .thenApply(v -> futureList.stream().map(future -> future.join())
                        .collect(Collectors.toList()));
        return resultFuture.join();
    }

    protected CompletableFuture<BufferTemp> pageToPdf(String rendeId, BufferTemp urlTemp) {
        // 1、使用CompletableFuture.supplyAsync()方法，异步执行截图
        return CompletableFuture.supplyAsync(() -> {
            log.info("Generate PDF for url: %s", urlTemp.getUrl());
            Page page = null;
            try {
                // 从池中获取一个浏览器页面
                page = browserContextPool.borrowObject().newPage();
                // 设置页面加载参数, 并跳转到url
                page.navigate(urlTemp.getUrl(), new Page.NavigateOptions()
                        .setTimeout(60 * 1000)
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));
                // 定义截图输出路径
                String fileName = String.format("%s.pdf", urlTemp.getIndex());
                log.info("Generate pdf buffer start for : {}", fileName);
                page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.SCREEN));
                // 生成PDF
                Page.PdfOptions pdfOptions = new Page.PdfOptions()
                        .setScale(1.0)
                        .setPageRanges("1-1")
                        .setFormat("A3")
                        .setPrintBackground(true);
                byte[] pdfBuffer = page.pdf(pdfOptions);
                log.info("Generate pdf buffer success for : {}", fileName);
                urlTemp.setBuffer(pdfBuffer);
                urlTemp.setName(fileName);
                // 释放页面对象
                browserContextPool.returnObject(page.context());
                return urlTemp;
            } catch (Exception e) {
                throw new RuntimeException("Generate PDF error: {}", e);
            } finally {
                try {
                    if (Objects.nonNull(page) && !page.isClosed()){
                        page.close();
                    }
                } catch (Exception e) {
                    // ignore error
                }
            }
        });
    }
}

```

基于File调用示例（提供了基于CompletableFuture的批量生成pdf和单个生成pdf方法，如果希望合并pdf，可自行搜索 pdfbox、itext相关资料）：

```java
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Media;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.spring.boot.pool.BrowserContextPool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@SpringBootApplication
@Slf4j
public class PlaywrightApplication_Test2 implements CommandLineRunner {

    protected static final String BASE_DIR = "D://tmp";

    @Autowired
    private BrowserContextPool browserContextPool;

    public static void main(String[] args) throws Exception {
        SpringApplication.run(PlaywrightApplication_Test2.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            FileUtils.forceMkdir( new File(BASE_DIR));
            // 截图测试
            captureScreenshot(UUID.randomUUID().toString(), BufferTemp.builder().url("https://www.baidu.com").index(1).build(), null).exceptionally(throwable -> {
                throwable.printStackTrace();
                return null;
            }).join();

            // 生成pdf测试
            pageToPdf(UUID.randomUUID().toString(), BufferTemp.builder().url("https://www.baidu.com").index(1).build()).exceptionally(throwable -> {
                throwable.printStackTrace();
                return null;
            }).join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 定义一个浏览器内容截图方法
     * @param rendeId
     * @param urlTemps
     * @param selector
     * @return
     */
    protected List<BufferTemp> captureScreenshots(String rendeId, List<BufferTemp> urlTemps, String selector) {
        log.info("Capturing screenshots for urls: ", urlTemps.stream().map(BufferTemp::getUrl).collect(Collectors.toList()));
        // 1、使用CompletableFuture异步处理
        List<CompletableFuture<BufferTemp>> futureList = urlTemps.stream()
                .map(urlTemp -> captureScreenshot(rendeId, urlTemp, selector))
                .collect(Collectors.toList());
        // 2、使用CompletableFuture.allOf()方法，等待所有异步线程执行完毕
        CompletableFuture<Void> allFuture = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()]));
        CompletableFuture<List<BufferTemp>> resultFuture = allFuture
                .thenApply(v -> futureList.stream().map(future -> future.join())
                        .collect(Collectors.toList()));
        return resultFuture.join();
    }

    /**
     * 定义一个浏览器页面截图方法
     * @param rendeId
     * @param urlTemp
     * @param selector
     * @return
     */
    protected CompletableFuture<BufferTemp> captureScreenshot(String rendeId, BufferTemp urlTemp, String selector){
        // 1、使用CompletableFuture.supplyAsync()方法，异步执行截图
        return CompletableFuture.supplyAsync(() -> {
            log.info("Capturing screenshot : rendeId: {}, selector: {}, url : {}", rendeId, selector, urlTemp.getUrl());
            Page page = null;
            try {
                // 从池中获取一个浏览器页面
                page = browserContextPool.borrowObject().newPage();
                //page = browserPagePool.borrowObject();
                // 设置页面加载参数, 并跳转到url
                page.navigate(urlTemp.getUrl(), new Page.NavigateOptions()
                        .setTimeout(60 * 1000)
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));
                // 定义截图输出路径
                String fileName = String.format("%s.png", urlTemp.getIndex());
                File screenshotFile = new File(BASE_DIR, rendeId + File.separator + fileName);
                log.info("screenshot start for : {}", screenshotFile.getAbsolutePath());
                // 截图
                if(StringUtils.isEmpty(selector)){
                    Page.ScreenshotOptions options = new Page.ScreenshotOptions()
                            .setFullPage(true)
                            .setOmitBackground(true)
                            .setTimeout(30 * 1000)
                            .setType(ScreenshotType.PNG)
                            .setPath(screenshotFile.toPath());
                    page.screenshot(options);
                } else {

                    // 定位到要截图的元素
                    ElementHandle element = page.querySelector(selector);

                    ElementHandle.ScreenshotOptions options = new ElementHandle.ScreenshotOptions()
                            .setOmitBackground(true)
                            .setTimeout(30 * 1000)
                            .setType(ScreenshotType.PNG)
                            .setPath(screenshotFile.toPath());

                    element.screenshot(options);
                }
                log.info("screenshot success for {} : {}", rendeId, screenshotFile.getAbsolutePath());
                urlTemp.setPath(screenshotFile.getAbsolutePath());
                urlTemp.setName(fileName);
                browserContextPool.returnObject(page.context());
                //browserPagePool.returnObject(page);
                return urlTemp;
            } catch (Exception e) {
                throw new RuntimeException("Capture screenshot error: {}", e);
            } finally {
                try {
                    if (Objects.nonNull(page) && !page.isClosed()){
                        page.close();
                    }
                } catch (Exception e) {
                    // ignore error
                }
            }
        });
    }

    /**
     * 定义一个浏览器内容截图方法
     * @param rendeId
     * @param urlTemps
     * @return
     */
    protected List<BufferTemp> pageToPdfs(String rendeId, List<BufferTemp> urlTemps) {
        log.info("Capturing screenshots for urls: ", urlTemps.stream().map(BufferTemp::getUrl).collect(Collectors.toList()));
        // 1、使用CompletableFuture异步处理
        List<CompletableFuture<BufferTemp>> futureList = urlTemps.stream()
                .map(urlTemp -> pageToPdf(rendeId, urlTemp))
                .collect(Collectors.toList());
        // 2、使用CompletableFuture.allOf()方法，等待所有异步线程执行完毕
        CompletableFuture<Void> allFuture = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()]));
        CompletableFuture<List<BufferTemp>> resultFuture = allFuture
                .thenApply(v -> futureList.stream().map(future -> future.join())
                        .collect(Collectors.toList()));
        return resultFuture.join();
    }

    protected CompletableFuture<BufferTemp> pageToPdf(String rendeId, BufferTemp urlTemp) {
        // 1、使用CompletableFuture.supplyAsync()方法，异步执行截图
        return CompletableFuture.supplyAsync(() -> {
            log.info("Generate PDF for url: {}", urlTemp.getUrl());
            Page page = null;
            try {
                // 从池中获取一个浏览器页面
                page = browserContextPool.borrowObject().newPage();
                // 设置页面加载参数, 并跳转到url
                page.navigate(urlTemp.getUrl(), new Page.NavigateOptions()
                        .setTimeout(60 * 1000)
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));
                // 定义截图输出路径
                String fileName = String.format("%s.pdf", urlTemp.getIndex());
                File pdfFile = new File(BASE_DIR, rendeId + File.separator + fileName);
                log.info("Generate pdf file start for : {}", pdfFile.getAbsolutePath());
                page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.SCREEN));
                // 生成PDF
                Page.PdfOptions pdfOptions = new Page.PdfOptions()
                        .setScale(1.0f)
                        .setPageRanges("1-1")
                        .setFormat("A3")
                        .setPrintBackground(true)
                        .setPath(pdfFile.toPath());
                page.pdf(pdfOptions);
                log.info("Generate pdf file success for : {}", pdfFile.getAbsolutePath());
                urlTemp.setPath(pdfFile.getAbsolutePath());
                urlTemp.setName(fileName);
                browserContextPool.returnObject(page.context());
                return urlTemp;
            } catch (Exception e) {
                throw new RuntimeException("Generate PDF error: {}", e);
            } finally {
                try {
                    if (Objects.nonNull(page) && !page.isClosed()){
                        page.close();
                    }
                } catch (Exception e) {
                    // ignore error
                }
            }
        });
    }
}

```

#### 补充说明

> 以上示例仅是我完整html生成pdf/png服务的部分代码，如有进一步需求，请邮件联系！

## Jeebiz 技术社区

Jeebiz 技术社区 **微信公共号**、**小程序**，欢迎关注反馈意见和一起交流，关注公众号回复「Jeebiz」拉你入群。

|公共号|小程序|
|---|---|
| ![](https://raw.githubusercontent.com/hiwepy/static/main/images/qrcode_for_gh_1d965ea2dfd1_344.jpg)| ![](https://raw.githubusercontent.com/hiwepy/static/main/images/gh_09d7d00da63e_344.jpg)|