package com.microsoft.playwright.spring.boot.pool;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.spring.boot.PlaywrightProperties;
import com.microsoft.playwright.spring.boot.options.BrowserLaunchPersistentContextOptions;
import com.microsoft.playwright.spring.boot.options.BrowserNewContextOptions;
import com.microsoft.playwright.spring.boot.utils.PlaywrightUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.springframework.beans.factory.DisposableBean;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
public class BrowserContextPooledObjectFactory implements PooledObjectFactory<BrowserContext>, DisposableBean {

    /**
     * Playwright管理容器
     */
    private static final Map<BrowserContext, Playwright> PLAYWRIGHT_MAP = new ConcurrentHashMap<>();
    private static final Map<BrowserContext, Path> CONTEXT_DIR_MAP = new ConcurrentHashMap<>();
    private static final Map<Path, Long> CONTEXT_DIR_LAST_USED = new ConcurrentHashMap<>();
    private static final Map<Path, Long> CONTEXT_DIR_SIZE = new ConcurrentHashMap<>();
    private static final String CONTEXT_DIR_PREFIX = "context_";
    private static final AtomicInteger contextCounter = new AtomicInteger(0);
    private static final ReentrantLock dirCreationLock = new ReentrantLock();
    private final PlaywrightProperties playwrightProperties;
    private final Consumer<Browser> browserDisconnectedHandler = browser -> {
        log.info("Browser disconnected, cleaning up resources...");
        browser.contexts().forEach(context -> {
            try {
                // 获取相关的 BrowserContext
                if (Objects.nonNull(context)) {
                    try {
                        // 清理浏览器上下文
                        PlaywrightUtil.cleanupBrowserContext(context);
                    } catch (Exception e) {
                        log.warn("Error cleaning up browser context on disconnect", e);
                    }

                    try {
                        if (context.browser() != null && context.browser().isConnected()) {
                            context.close();
                        }
                    } catch (Exception e) {
                        log.warn("Error closing browser context on disconnect", e);
                    }

                    // 从 Map 中移除并关闭 Playwright
                    Playwright pw = PLAYWRIGHT_MAP.remove(context);
                    if (Objects.nonNull(pw)) {
                        try {
                            pw.close();
                            log.info("Cleaned up Playwright instance on browser disconnect");
                        } catch (Exception e) {
                            log.warn("Error closing Playwright instance on disconnect", e);
                        }
                    }

                    Path contextDir = CONTEXT_DIR_MAP.remove(context);
                    if (contextDir != null) {
                        try {
                            FileUtils.deleteDirectory(contextDir.toFile());
                            log.info("Cleaned up Playwright context directory on disconnect");
                        } catch (Exception e) {
                            log.error("Error cleaning up Playwright context directory on disconnect", e);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error cleaning up resources on browser disconnect", e);
            }
        });
    };


    public BrowserContextPooledObjectFactory(PlaywrightProperties playwrightProperties) {
        this.playwrightProperties = playwrightProperties;
        // 在构造函数中预分配目录
        if (!playwrightProperties.isIsolated()) {
            File userDataRootDir = new File(playwrightProperties.getLaunchPersistentContextOptions().getUserDataDir());
            if (userDataRootDir.exists()) {
                preallocateContextDirs(userDataRootDir);
            }
        }
    }

    /**
     * 1、从池中取出一个池中物（playwright）时调用
     * @param p a {@code PooledObject} wrapping the instance to be activated
     *
     * @throws Exception if there is a problem activating {@code obj}
     */
    @Override
    public void activateObject(PooledObject<BrowserContext> p) throws Exception {
        BrowserContext browserContext = requireConnectedContext(p);
        // A failed reset must reach Commons Pool so it destroys this object.
        browserContext.clearCookies();
    }

    private BrowserContext requireConnectedContext(PooledObject<BrowserContext> p) {
        BrowserContext context = p == null ? null : p.getObject();
        if (context == null) {
            throw new IllegalStateException("Browser context is missing");
        }
        Browser browser = context.browser();
        // Persistent contexts can have no Browser facade; probe the context itself.
        if (browser != null && !browser.isConnected()) {
            throw new IllegalStateException("Browser is disconnected");
        }
        return context;
    }

    /**
     * 2、检测对象是否"有效";Pool中不能保存无效的"对象",因此"后台检测线程"会周期性的检测Pool中"对象"的有效性,如果对象无效则会导致此对象从Pool中移除,并destroy;此外在调用者从Pool获取一个"对象"时,也会检测"对象"的有效性,确保不能讲"无效"的对象输出给调用者;当调用者使用完毕将"对象归还"到Pool时,仍然会检测对象的有效性.所谓有效性,就是此"对象"的状态是否符合预期,是否可以对调用者直接使用;如果对象是Socket,那么它的有效性就是socket的通道是否畅通/阻塞是否超时等.
     * 这里若要检测，需要在PoolConfig中配置检测项目。
     * true：检测正常，符合预期；false：异常，销毁对象
     * @param p a {@code PooledObject} wrapping the instance to be validated
     *
     * @return {@code false} if this object is not currently valid and should be dropped from the pool, {@code true} otherwise.
     */
    @Override
    public boolean validateObject(PooledObject<BrowserContext> p) {
        try {
            BrowserContext context = requireConnectedContext(p);
            // browser.isConnected() and pages() alone cannot detect a closed context.
            // This read-only protocol round trip also works without a Browser facade.
            // Do not retain or log the returned cookies.
            context.cookies();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 3、创建池中物（playwright）
     * @return a new instance that can be served by the pool
     */
    @Override
    public PooledObject<BrowserContext> makeObject() {
        log.info("Create Playwright Instance .");
        Playwright playwright = Playwright.create();
        log.info("Create Playwright Instance '{}' Success.", playwright);
        // Browser Type
        PlaywrightProperties.BrowserTypeEnum browserTypeEnum = Objects.nonNull(playwrightProperties.getBrowserType()) ? playwrightProperties.getBrowserType() : PlaywrightProperties.BrowserTypeEnum.chromium;
        // Get Browser Launch Options
        BrowserType.LaunchOptions launchOptions = Objects.nonNull(playwrightProperties.getLaunchOptions()) ? playwrightProperties.getLaunchOptions().toOptions() : new BrowserType.LaunchOptions().setHeadless(true);
        // Get Browser
        log.info("Create Browser Instance .");
        BrowserType browserType = browserTypeEnum.getBrowserType(playwright);
        // 判断浏览器会话是否隔离
        if(playwrightProperties.isIsolated()){
            Browser browser = browserType.launch(launchOptions);
            // 添加断开连接监听器
            browser.onDisconnected(browserDisconnectedHandler);
            log.info("Create Browser Instance {} Success.", browser);
            // Get Browser New Context Options
            Browser.NewContextOptions newContextOptions = Objects.nonNull(playwrightProperties.getNewContextOptions()) ?
                    playwrightProperties.getNewContextOptions().toOptions() : new Browser.NewContextOptions();
            // 使用 Browser.newContext() 方法创建隔离的非持久性浏览器上下文。非持久性浏览器上下文不会将任何浏览数据写入磁盘。
            BrowserContext browserContext = browser.newContext(newContextOptions);
            log.info("Create BrowserContext Instance '{}', browserType : {} , Success.", browserContext, browserType);
            PLAYWRIGHT_MAP.put(browserContext, playwright);
            return new DefaultPooledObject<>(browserContext);
        } else {
            // Get Browser Launch Options
            BrowserType.LaunchPersistentContextOptions launchPersistentContextOptions = Objects.nonNull(playwrightProperties.getLaunchPersistentContextOptions()) ?
                    playwrightProperties.getLaunchPersistentContextOptions().toOptions() : new BrowserType.LaunchPersistentContextOptions().setHeadless(true);
            File userDataRootDir = new File(playwrightProperties.getLaunchPersistentContextOptions().getUserDataDir());
            if (!userDataRootDir.exists()) {
                userDataRootDir.mkdirs();
                log.info("Create User Data Root Directory '{}' Success.", userDataRootDir);
                // 创建目录后预分配上下文目录
                preallocateContextDirs(userDataRootDir);
            }
            // 获取下一个可用的上下文目录
            Path contextDir = getNextContextDir(userDataRootDir);
            // 更新目录使用时间
            CONTEXT_DIR_LAST_USED.put(contextDir, System.currentTimeMillis());
            // 更新目录大小
            updateDirectorySize(contextDir);
            // Launches browser that uses persistent storage located at userDataDir and returns the only context. Closing this context will automatically close the browser.
            BrowserContext browserContext = browserType.launchPersistentContext(contextDir, launchPersistentContextOptions);
            // 添加断开连接监听器
            if (Objects.nonNull(browserContext) && Objects.nonNull(browserContext.browser())) {
                browserContext.browser().onDisconnected(browserDisconnectedHandler);
            }
            log.info("Create Persistent BrowserContext Instance '{}', browserType : {} , Success.", browserContext, browserType);
            PLAYWRIGHT_MAP.put(browserContext, playwright);
            CONTEXT_DIR_MAP.put(browserContext, contextDir);
            return new DefaultPooledObject<>(browserContext);
        }
    }

    /**
     * 预分配目录
     * @param userDataRootDir 用户数据根目录
     */
    private void preallocateContextDirs(File userDataRootDir) {
        dirCreationLock.lock();
        try {
            for (int i = 0; i < this.getMaximumContentSize(); i++) {
                String dirName = CONTEXT_DIR_PREFIX + String.format("%03d", i);
                Path dirPath = Paths.get(userDataRootDir.getAbsolutePath(), dirName);
                if (!dirPath.toFile().exists()) {
                    dirPath.toFile().mkdirs();
                    CONTEXT_DIR_LAST_USED.put(dirPath, System.currentTimeMillis());
                    CONTEXT_DIR_SIZE.put(dirPath, 0L);
                }
            }
        } finally {
            dirCreationLock.unlock();
        }
    }

    /**
     * 获取目录大小
     * @param dir 要检查的目录
     * @return 目录大小
     */
    private long getDirectorySize(Path dir) {
        try {
            return Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            log.error("Error getting file size: {}", p, e);
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            log.error("Error calculating directory size: {}", dir, e);
            return 0L;
        }
    }

    /**
     * 更新目录大小
     * @param dir 要更新的目录
     */
    private void updateDirectorySize(Path dir) {
        long size = getDirectorySize(dir);
        CONTEXT_DIR_SIZE.put(dir, size);
        if (size > this.getMaximumDirSize()) {
            log.warn("Context directory {} exceeds size limit: {} bytes", dir, size);
        }
    }

    /**
     * 获取下一个可用的上下文目录
     * @param userDataRootDir 用户数据根目录
     * @return 新的上下文目录路径
     */
    private Path getNextContextDir(File userDataRootDir) {
        dirCreationLock.lock();
        try {
            // 清理旧的目录
            cleanupOldContextDirs(userDataRootDir);
            
            // 使用负载均衡策略选择目录
            return findLeastUsedDir(userDataRootDir);
        } finally {
            dirCreationLock.unlock();
        }
    }

    /**
     * 查找使用最少的目录
     * @param userDataRootDir 用户数据根目录
     * @return 使用最少的目录路径
     */
    private Path findLeastUsedDir(File userDataRootDir) {
        return CONTEXT_DIR_LAST_USED.entrySet().stream()
                .filter(entry -> isDirectoryNotUsed(entry.getKey()))
                .min(Comparator.comparingLong(entry -> {
                    Path dir = entry.getKey();
                    long lastUsed = entry.getValue();
                    long size = CONTEXT_DIR_SIZE.getOrDefault(dir, 0L);
                    // 综合考虑最后使用时间和目录大小
                    return lastUsed + (size * 1000); // 每1MB增加1秒的权重
                }))
                .map(Map.Entry::getKey)
                .orElseGet(() -> {
                    // 如果没有可用目录，创建新目录
                    int nextNumber = contextCounter.getAndIncrement() % this.getMaximumContentSize();
                    String dirName = CONTEXT_DIR_PREFIX + String.format("%03d", nextNumber);
                    Path newDir = Paths.get(userDataRootDir.getAbsolutePath(), dirName);
                    if (!newDir.toFile().exists()) {
                        newDir.toFile().mkdirs();
                    }
                    CONTEXT_DIR_LAST_USED.put(newDir, System.currentTimeMillis());
                    CONTEXT_DIR_SIZE.put(newDir, 0L);
                    return newDir;
                });
    }

    /**
     * 清理旧的上下文目录
     * @param userDataRootDir 用户数据根目录
     */
    private void cleanupOldContextDirs(File userDataRootDir) {
        File[] contextDirs = userDataRootDir.listFiles((dir, name) -> name.startsWith(CONTEXT_DIR_PREFIX));
        if (contextDirs != null) {
            for (File dir : contextDirs) {
                Path dirPath = dir.toPath();
                if (isDirectoryNotUsed(dirPath)) {
                    long lastUsed = CONTEXT_DIR_LAST_USED.getOrDefault(dirPath, 0L);
                    long size = CONTEXT_DIR_SIZE.getOrDefault(dirPath, 0L);
                    
                    // 如果目录超过大小限制或超时未使用，则清理
                    if (size > this.getMaximumDirSize() || System.currentTimeMillis() - lastUsed > this.getMaximumDirUsageTimeout()) {
                        try {
                            FileUtils.deleteDirectory(dir);
                            CONTEXT_DIR_LAST_USED.remove(dirPath);
                            CONTEXT_DIR_SIZE.remove(dirPath);
                            log.info("Cleaned up context directory: {}, size: {} bytes, last used: {} ms ago", 
                                    dir.getAbsolutePath(), size, System.currentTimeMillis() - lastUsed);
                        } catch (Exception e) {
                            log.error("Error cleaning up context directory: {}", dir.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }
    }

    /**
     * 检查目录是否正在使用
     * @param dir 要检查的目录
     * @return 如果目录正在使用返回true，否则返回false
     */
    private boolean isDirectoryNotUsed(Path dir) {
        // 检查目录是否在CONTEXT_DIR_MAP中
        if (CONTEXT_DIR_MAP.containsValue(dir)) {
            return false;
        }
        
        // 检查目录最后使用时间
        Long lastUsed = CONTEXT_DIR_LAST_USED.get(dir);
        if (lastUsed != null) {
            // 如果目录在超时时间内被使用过，认为它仍在被使用
            return System.currentTimeMillis() - lastUsed >= this.getMaximumDirUsageTimeout();
        }
        
        return true;
    }

    /**
     * 归还一个池中物（playwright）时调用，不应该activateObject冲突
     * @param p a {@code PooledObject} wrapping the instance to be passivated
     *
     * @throws Exception if there is a problem passivating {@code obj}
     */
    @Override
    public void passivateObject(PooledObject<BrowserContext> p) throws Exception {
        BrowserContext browserContext = requireConnectedContext(p);
        // Reset through the protocol even when there are no pages (or no Browser facade).
        // Propagate failure instead of returning an unusable context to the idle pool.
        browserContext.clearCookies();

        browserContext.pages().forEach(page -> {
            try {
                // Best effort for opaque origins such as about:blank, which deny storage.
                page.evaluate("() => window.localStorage.clear()");
                page.evaluate("() => window.sessionStorage.clear()");
                page.evaluate("() => { " +
                        "const databases = window.indexedDB.databases(); " +
                        "databases.then(dbs => dbs.forEach(db => window.indexedDB.deleteDatabase(db.name))); " +
                        "}");
            } catch (Exception e) {
                log.warn("Failed to clear storage");
            }
        });
        // Unlike best-effort destruction, passivation must report page-close failures.
        browserContext.pages().forEach(page -> page.close());

        Path contextDir = CONTEXT_DIR_MAP.get(browserContext);
        if (contextDir != null) {
            updateDirectorySize(contextDir);
        }
    }


    /**
     * 销毁一个池中物（playwright）时调用
     * @param p a {@code PooledObject} wrapping the instance to be destroyed
     *
     * @throws Exception if there is a problem destroying {@code obj}
     */
    @Override
    public void destroyObject(PooledObject<BrowserContext> p) throws Exception {
        BrowserContext browserContext = p.getObject();
        if (Objects.isNull(browserContext)) {
            return;
        }

        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < this.getMaximumRetryAttempts()) {
            try {
                // 1. 清理浏览器上下文
                try {
                    PlaywrightUtil.cleanupBrowserContext(browserContext);
                } catch (Exception e) {
                    log.warn("Error cleaning up browser context: {}", e.getMessage());
                }

                // 2. 关闭上下文
                try {
                    if (browserContext.browser() != null && browserContext.browser().isConnected()) {
                        browserContext.close();
                    }
                } catch (Exception e) {
                    log.warn("Error closing browser context: {}", e.getMessage());
                }

                // 3. 清理 Playwright 实例
                Playwright playwright = PLAYWRIGHT_MAP.remove(browserContext);
                if (playwright != null) {
                    try {
                        playwright.close();
                        log.info("Cleaned up Playwright instance in destroyObject");
                    } catch (Exception e) {
                        log.warn("Error closing Playwright instance: {}", e.getMessage());
                    }
                }

                // 4. 清理目录
                Path contextDir = CONTEXT_DIR_MAP.remove(browserContext);
                if (contextDir != null) {
                    CONTEXT_DIR_LAST_USED.remove(contextDir);
                    CONTEXT_DIR_SIZE.remove(contextDir);
                    try {
                        FileUtils.deleteDirectory(contextDir.toFile());
                        log.info("Cleaned up Playwright context directory in destroyObject");
                    } catch (Exception e) {
                        log.error("Error cleaning up Playwright context directory: {}", e.getMessage());
                    }
                }
                
                return; // 清理成功，直接返回
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                if (retryCount < this.getMaximumRetryAttempts()) {
                    log.warn("Retry {} of {} for resource cleanup", retryCount, this.getMaximumRetryAttempts());
                    Thread.sleep(this.getMaximumRetryDelayMs());
                }
            }
        }

        // 所有重试都失败
        log.error("Failed to cleanup resources after {} attempts", this.getMaximumRetryAttempts(), lastException);
    }

    @Override
    public void destroy() throws Exception {
        PLAYWRIGHT_MAP.forEach((browserContext, playwright) -> {
            try {
                PlaywrightUtil.cleanupBrowserContext(browserContext);
                browserContext.close();
                if (playwright != null) {
                    playwright.close();
                    log.info("Destroy browserContext of Playwright Instance '{}' Success.", playwright);
                }
            } catch (Exception e) {
                log.error("Error destroying browser context", e);
            }
        });
    }



    /**
     * Maximum number of retry attempts to create a new context. If the limit is reached, the oldest context will be closed.
     * Defaults 3
     */
    public Integer getMaximumRetryAttempts(){
        if(playwrightProperties.isIsolated()){
            return Objects.nonNull(playwrightProperties.getNewContextOptions()) ?
                    playwrightProperties.getNewContextOptions().getMaximumRetryAttempts() : BrowserNewContextOptions.DEFAULT_MAX_RETRY_ATTEMPTS;
        } else {
            return Objects.nonNull(playwrightProperties.getLaunchPersistentContextOptions()) ?
                    playwrightProperties.getLaunchPersistentContextOptions().getMaximumRetryAttempts() : BrowserLaunchPersistentContextOptions.DEFAULT_MAX_RETRY_ATTEMPTS;
        }
    };

    /**
     * Maximum time to wait for the retry delay. If the limit is reached, the oldest context will be closed. Defaults 1 second
     */
    public Long getMaximumRetryDelayMs(){
        if(playwrightProperties.isIsolated()){
            return Objects.nonNull(playwrightProperties.getNewContextOptions()) ?
                    playwrightProperties.getNewContextOptions().getMaximumRetryDelayMs() : BrowserNewContextOptions.DEFAULT_MAX_RETRY_ATTEMPTS;
        } else {
            return Objects.nonNull(playwrightProperties.getLaunchPersistentContextOptions()) ?
                    playwrightProperties.getLaunchPersistentContextOptions().getMaximumRetryDelayMs() : BrowserLaunchPersistentContextOptions.DEFAULT_MAX_RETRY_ATTEMPTS;
        }
    }


    /**
     * Maximum time to wait for the resource cleanup. If the limit is reached, the oldest context will be closed. Defaults 30
     * seconds
     */
    public Long getMaximumResourceCleanupTimeoutMs(){
        if(playwrightProperties.isIsolated()){
            return Objects.nonNull(playwrightProperties.getNewContextOptions()) ?
                    playwrightProperties.getNewContextOptions().getMaximumResourceCleanupTimeoutMs() : BrowserNewContextOptions.DEFAULT_RESOURCE_CLEANUP_TIMEOUT_MS;
        } else {
            return Objects.nonNull(playwrightProperties.getLaunchPersistentContextOptions()) ?
                    playwrightProperties.getLaunchPersistentContextOptions().getMaximumResourceCleanupTimeoutMs(): BrowserLaunchPersistentContextOptions.DEFAULT_RESOURCE_CLEANUP_TIMEOUT_MS;
        }
    }

    /**
     * Maximum number of browser contexts to be created. If the limit is reached, the oldest context will be closed. Defaults 16
     */
    public Integer getMaximumContentSize(){
        return Objects.nonNull(playwrightProperties.getLaunchPersistentContextOptions()) ?
                playwrightProperties.getLaunchPersistentContextOptions().getMaximumContentSize(): BrowserLaunchPersistentContextOptions.DEFAULT_MAX_CONTEXT_SIZE;
    }

    /**
     * Maximum size of the user data directory. If the limit is reached, the oldest context will be closed. Defaults 200MB
     */
    public Long getMaximumDirSize(){
        return Objects.nonNull(playwrightProperties.getLaunchPersistentContextOptions()) ?
                playwrightProperties.getLaunchPersistentContextOptions().getMaximumDirSize(): BrowserLaunchPersistentContextOptions.DEFAULT_MAX_DIR_SIZE;
    }

    /**
     * Maximum time to wait for the user data directory to be used. If the limit is reached, the oldest context will be closed.
     * Defaults 30 minutes
     */
    public Long getMaximumDirUsageTimeout(){
        return Objects.nonNull(playwrightProperties.getLaunchPersistentContextOptions()) ?
                playwrightProperties.getLaunchPersistentContextOptions().getMaximumDirUsageTimeout(): BrowserLaunchPersistentContextOptions.DEFAULT_DIR_USAGE_TIMEOUT;
    }

}
