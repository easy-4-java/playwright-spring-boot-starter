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

/**
 * Commons-Pool2 factory that creates, activates, validates, passivates and destroys
 * pooled {@link BrowserContext} instances. <p>When sessions are isolated a fresh,
 * non-persistent context is created for each object; otherwise persistent contexts
 * backed by rotating user-data directories are reused. The factory also tracks the
 * Playwright instance and on-disk directory associated with every context so that they
 * can be cleaned up on destroy, on browser disconnect and on container shutdown.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 */
@Slf4j
public class BrowserContextPooledObjectFactory implements PooledObjectFactory<BrowserContext>, DisposableBean {

    /**
     * Container tracking the {@link Playwright} instance owning each pooled context.
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


    /**
     * Creates a new factory bound to the supplied Playwright configuration. When
     * persistent (non-isolated) mode is enabled, the configured user-data directories
     * are pre-allocated up-front.
     * @param playwrightProperties the Playwright configuration properties
     */
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
     * Invoked when a pooled {@link BrowserContext} is borrowed from the pool, clearing
     * its cookies before reuse.
     * @param p a {@code PooledObject} wrapping the instance to be activated
     * @throws Exception if there is a problem activating the object
    @Override
    public void activateObject(PooledObject<BrowserContext> p) throws Exception {
        BrowserContext browserContext = p.getObject();
        log.info("Activate BrowserContext Instance '{}'.", browserContext);
        if (Objects.nonNull(browserContext) && browserContext.browser() != null && browserContext.browser().isConnected()) {
            try {
                browserContext.clearCookies();
            } catch (Exception e) {
                log.warn("Failed to clear cookies, browser context might be closed", e);
            }
        }
    }

    /**
     * Validates a pooled {@link BrowserContext} by ensuring the wrapped object is
     * non-{@code null}. Validation runs periodically on idle objects, on borrow and on
     * return; invalid objects are dropped from the pool.
     * @param p a {@code PooledObject} wrapping the instance to be validated
     * @return {@code false} if this object is not currently valid and should be dropped
     *         from the pool, {@code true} otherwise
    @Override
    public boolean validateObject(PooledObject<BrowserContext> p) {
        BrowserContext browserContext = p.getObject();
        log.info("Validate BrowserContext Instance '{}'.", browserContext);
        boolean isValidated = Objects.nonNull(browserContext);
        log.info("Validate BrowserContext : {}, isValidated : {}", browserContext, isValidated);
        return isValidated;
    }

    /**
     * Creates a new pooled {@link BrowserContext}. In isolated mode a non-persistent
     * context is created; otherwise a persistent context backed by the next available
     * user-data directory is launched.
     * @return a new instance that can be served by the pool
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
     * Pre-allocates the configured number of context directories under the user-data
     * root directory.
     * @param userDataRootDir the root user-data directory
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
     * Computes the total size in bytes of the regular files within the given directory.
     * @param dir the directory to inspect
     * @return the directory size in bytes (0 if it cannot be walked)
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
     * Recomputes and records the size of the given context directory, warning when it
     * exceeds the configured maximum.
     * @param dir the directory whose size is updated
     */
    private void updateDirectorySize(Path dir) {
        long size = getDirectorySize(dir);
        CONTEXT_DIR_SIZE.put(dir, size);
        if (size > this.getMaximumDirSize()) {
            log.warn("Context directory {} exceeds size limit: {} bytes", dir, size);
        }
    }

    /**
     * Returns the next available context directory, cleaning up stale directories first
     * and then selecting the least-recently-used candidate.
     * @param userDataRootDir the root user-data directory
     * @return the path of the context directory to use
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
     * Selects the least-recently-used, currently-idle context directory, weighting
     * last-use time against directory size. A new directory is created if none is free.
     * @param userDataRootDir the root user-data directory
     * @return the path of the least-used directory
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
     * Deletes idle context directories that exceed the size or usage-time limit.
     * @param userDataRootDir the root user-data directory
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
     * Returns whether the given context directory is currently idle (neither bound to an
     * active context nor used within the usage-timeout window).
     * @param dir the directory to check
     * @return {@code true} if the directory is not in use, {@code false} otherwise
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
     * Invoked when a pooled {@link BrowserContext} is returned to the pool, clearing
     * session storage, cookies and pages and refreshing the directory-size bookkeeping.
     * @param p a {@code PooledObject} wrapping the instance to be passivated
     * @throws Exception if there is a problem passivating the object
    @Override
    public void passivateObject(PooledObject<BrowserContext> p) throws Exception {
        BrowserContext browserContext = p.getObject();
        log.info("Return BrowserContext Instance '{}'.", browserContext);
        
        if (Objects.nonNull(browserContext) && browserContext.browser() != null && browserContext.browser().isConnected()) {
            try {
                
                // 1. 清理每个页面的数据
                browserContext.pages().forEach(page -> {
                    try {
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

                // 2. 清理 cookies
                try {
                    browserContext.clearCookies();
                } catch (Exception e) {
                    log.warn("Failed to clear cookies");
                }

                // 3 先关闭所有页面
                browserContext.pages().forEach(PlaywrightUtil::closePage);
                
                // 4. 更新目录大小
                Path contextDir = CONTEXT_DIR_MAP.get(browserContext);
                if (contextDir != null) {
                    updateDirectorySize(contextDir);
                }
                
                log.info("Return BrowserContext Instance : clear all session data success");
            } catch (Exception e) {
                log.error("Error clearing session data", e);
                throw e;
            }
        }
    }


    /**
     * Invoked when a pooled {@link BrowserContext} is destroyed, releasing the browser
     * context, the owning Playwright instance and its on-disk directory. The cleanup is
     * retried up to the configured number of attempts.
     * @param p a {@code PooledObject} wrapping the instance to be destroyed
     * @throws Exception if there is a problem destroying the object
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

    /**
     * Invoked by the Spring container on shutdown to close every remaining browser
     * context and its owning Playwright instance.
     * @throws Exception if an error occurs while destroying resources
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