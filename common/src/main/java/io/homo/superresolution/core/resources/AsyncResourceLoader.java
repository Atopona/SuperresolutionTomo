package io.homo.superresolution.core.resources;

import io.homo.superresolution.common.SuperResolution;
import io.homo.superresolution.core.graphics.shader.ShaderCache;
import io.homo.superresolution.core.memory.BufferPool;
import io.homo.superresolution.core.performance.PerformanceMonitor;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步资源加载器
 * 在后台预加载资源，减少运行时加载延迟
 */
public class AsyncResourceLoader {
    private static final ExecutorService loadingExecutor = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 4),
        new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "AsyncResourceLoader-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1); // 稍低优先级
                return t;
            }
        }
    );
    
    private static final ConcurrentHashMap<String, CompletableFuture<ResourceHandle>> loadingResources = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ResourceHandle> loadedResources = new ConcurrentHashMap<>();
    
    /**
     * 资源句柄接口
     */
    public interface ResourceHandle {
        boolean isLoaded();
        void destroy();
        String getResourceId();
    }
    
    /**
     * 异步加载资源
     */
    public static <T extends ResourceHandle> CompletableFuture<T> loadAsync(String resourceId, ResourceLoader<T> loader) {
        // 检查是否已经加载
        @SuppressWarnings("unchecked")
        T existing = (T) loadedResources.get(resourceId);
        if (existing != null && existing.isLoaded()) {
            return CompletableFuture.completedFuture(existing);
        }
        
        // 检查是否正在加载
        @SuppressWarnings("unchecked")
        CompletableFuture<T> loading = (CompletableFuture<T>) loadingResources.get(resourceId);
        if (loading != null) {
            return loading;
        }
        
        // 开始异步加载
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try (PerformanceMonitor.AutoTimer timer = PerformanceMonitor.startTimer("resource_load")) {
                SuperResolution.LOGGER.debug("开始加载资源: {}", resourceId);
                T resource = loader.load(resourceId);
                SuperResolution.LOGGER.debug("资源加载完成: {}", resourceId);
                return resource;
            } catch (Exception e) {
                SuperResolution.LOGGER.error("资源加载失败: {}", resourceId, e);
                throw new RuntimeException("Failed to load resource: " + resourceId, e);
            }
        }, loadingExecutor);
        
        loadingResources.put(resourceId, (CompletableFuture<ResourceHandle>) future);
        
        future.whenComplete((resource, throwable) -> {
            loadingResources.remove(resourceId);
            if (throwable == null && resource != null) {
                loadedResources.put(resourceId, resource);
                PerformanceMonitor.increment("resources_loaded");
            } else {
                PerformanceMonitor.increment("resource_load_failures");
            }
        });
        
        return future;
    }
    
    /**
     * 同步获取资源（如果未加载则阻塞等待）
     */
    public static <T extends ResourceHandle> T getResource(String resourceId, ResourceLoader<T> loader) {
        try {
            return loadAsync(resourceId, loader).get();
        } catch (InterruptedException | ExecutionException e) {
            SuperResolution.LOGGER.error("获取资源失败: {}", resourceId, e);
            throw new RuntimeException("Failed to get resource: " + resourceId, e);
        }
    }
    
    /**
     * 尝试获取已加载的资源（不会触发加载）
     */
    @SuppressWarnings("unchecked")
    public static <T extends ResourceHandle> T getLoadedResource(String resourceId) {
        return (T) loadedResources.get(resourceId);
    }
    
    /**
     * 预加载资源列表
     */
    public static void preloadResources(String[] resourceIds, ResourceLoader<? extends ResourceHandle> loader) {
        SuperResolution.LOGGER.info("开始预加载 {} 个资源", resourceIds.length);
        
        CompletableFuture<?>[] futures = new CompletableFuture[resourceIds.length];
        for (int i = 0; i < resourceIds.length; i++) {
            futures[i] = loadAsync(resourceIds[i], loader);
        }
        
        // 在后台等待所有资源加载完成
        CompletableFuture.allOf(futures).whenComplete((result, throwable) -> {
            if (throwable == null) {
                SuperResolution.LOGGER.info("资源预加载完成");
            } else {
                SuperResolution.LOGGER.warn("部分资源预加载失败", throwable);
            }
        });
    }
    
    /**
     * 卸载资源
     */
    public static void unloadResource(String resourceId) {
        ResourceHandle resource = loadedResources.remove(resourceId);
        if (resource != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    resource.destroy();
                    SuperResolution.LOGGER.debug("资源已卸载: {}", resourceId);
                } catch (Exception e) {
                    SuperResolution.LOGGER.warn("卸载资源时出错: {}", resourceId, e);
                }
            }, loadingExecutor);
        }
        
        // 取消正在加载的资源
        CompletableFuture<ResourceHandle> loading = loadingResources.remove(resourceId);
        if (loading != null) {
            loading.cancel(true);
        }
    }
    
    /**
     * 清理所有资源
     */
    public static void cleanup() {
        SuperResolution.LOGGER.info("开始清理异步资源加载器...");
        
        // 取消所有正在加载的资源
        for (CompletableFuture<ResourceHandle> future : loadingResources.values()) {
            future.cancel(true);
        }
        loadingResources.clear();
        
        // 销毁所有已加载的资源
        CompletableFuture<?>[] cleanupFutures = loadedResources.values().stream()
            .map(resource -> CompletableFuture.runAsync(() -> {
                try {
                    resource.destroy();
                } catch (Exception e) {
                    SuperResolution.LOGGER.warn("清理资源时出错: {}", resource.getResourceId(), e);
                }
            }, loadingExecutor))
            .toArray(CompletableFuture[]::new);
        
        // 等待清理完成
        try {
            CompletableFuture.allOf(cleanupFutures).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            SuperResolution.LOGGER.warn("资源清理超时", e);
        }
        
        loadedResources.clear();
        loadingExecutor.shutdown();
        
        SuperResolution.LOGGER.info("异步资源加载器清理完成");
    }
    
    /**
     * 获取加载统计信息
     */
    public static LoadingStats getStats() {
        return new LoadingStats(
            loadedResources.size(),
            loadingResources.size(),
            loadingExecutor.isShutdown()
        );
    }
    
    /**
     * 资源加载器接口
     */
    @FunctionalInterface
    public interface ResourceLoader<T extends ResourceHandle> {
        T load(String resourceId) throws Exception;
    }
    
    /**
     * 加载统计信息
     */
    public static class LoadingStats {
        public final int loadedCount;
        public final int loadingCount;
        public final boolean isShutdown;
        
        public LoadingStats(int loadedCount, int loadingCount, boolean isShutdown) {
            this.loadedCount = loadedCount;
            this.loadingCount = loadingCount;
            this.isShutdown = isShutdown;
        }
        
        @Override
        public String toString() {
            return String.format("LoadingStats{loaded=%d, loading=%d, shutdown=%s}",
                               loadedCount, loadingCount, isShutdown);
        }
    }
    
    /**
     * 着色器资源句柄
     */
    public static class ShaderResourceHandle implements ResourceHandle {
        private final String resourceId;
        private final CompletableFuture<Void> shaderFuture;
        private volatile boolean loaded = false;
        
        public ShaderResourceHandle(String resourceId, String vertexSource, String fragmentSource) {
            this.resourceId = resourceId;
            this.shaderFuture = ShaderCache.compileShaderAsync(vertexSource, fragmentSource, 
                ShaderCache.ShaderCompileOptions.asyncOptions())
                .thenRun(() -> loaded = true);
        }
        
        @Override
        public boolean isLoaded() {
            return loaded;
        }
        
        @Override
        public void destroy() {
            if (shaderFuture != null) {
                shaderFuture.cancel(true);
            }
        }
        
        @Override
        public String getResourceId() {
            return resourceId;
        }
        
        public CompletableFuture<Void> getShaderFuture() {
            return shaderFuture;
        }
    }
}