package io.homo.superresolution.common.upscale;

import io.homo.superresolution.api.AbstractAlgorithm;
import io.homo.superresolution.api.InputResourceSet;
import io.homo.superresolution.api.registry.AlgorithmDescription;
import io.homo.superresolution.common.SuperResolution;
import io.homo.superresolution.common.config.SuperResolutionConfig;
import io.homo.superresolution.core.graphics.impl.texture.ITexture;
import io.homo.superresolution.core.math.Vector2f;
import io.homo.superresolution.core.memory.BufferPool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 优化的算法管理器
 * 支持热切换、资源复用和性能监控
 */
public class OptimizedAlgorithmManager {
    private static final AtomicReference<AbstractAlgorithm> currentAlgorithm = new AtomicReference<>();
    private static final AtomicReference<AbstractAlgorithm> nextAlgorithm = new AtomicReference<>();
    private static final AtomicBoolean switching = new AtomicBoolean(false);
    
    // 性能监控
    private static long lastFrameTime = 0;
    private static long totalFrameTime = 0;
    private static int frameCount = 0;
    private static final int PERF_SAMPLE_SIZE = 60; // 60帧采样
    
    // 资源复用
    private static final ResourcePool resourcePool = new ResourcePool();
    
    /**
     * 初始化算法管理器
     */
    public static void init() {
        resourcePool.init();
        BufferPool.warmup();
        SuperResolution.LOGGER.info("优化算法管理器已初始化");
    }
    
    /**
     * 获取当前算法（线程安全）
     */
    public static AbstractAlgorithm getCurrentAlgorithm() {
        AbstractAlgorithm algorithm = currentAlgorithm.get();
        return algorithm != null ? algorithm : SuperResolution.defaultAlgorithm;
    }
    
    /**
     * 热切换算法（不阻塞渲染）
     */
    public static CompletableFuture<Boolean> switchAlgorithmAsync(AlgorithmDescription<?> newDescription) {
        if (switching.get()) {
            SuperResolution.LOGGER.warn("算法切换正在进行中，忽略新的切换请求");
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            if (!switching.compareAndSet(false, true)) {
                return false;
            }
            
            try {
                SuperResolution.LOGGER.info("开始切换算法: {}", newDescription.getDisplayName());
                
                // 在后台创建新算法实例
                AbstractAlgorithm newAlgorithm = newDescription.createNewInstance();
                newAlgorithm.init();
                
                // 复用当前算法的资源配置
                AbstractAlgorithm oldAlgorithm = currentAlgorithm.get();
                if (oldAlgorithm != null) {
                    newAlgorithm.resize(
                        oldAlgorithm.getOutputWidth(),
                        oldAlgorithm.getOutputHeight()
                    );
                }
                
                // 原子性切换
                nextAlgorithm.set(newAlgorithm);
                
                SuperResolution.LOGGER.info("算法切换准备完成: {}", newDescription.getDisplayName());
                return true;
                
            } catch (Exception e) {
                SuperResolution.LOGGER.error("算法切换失败: {}", e.getMessage(), e);
                return false;
            } finally {
                switching.set(false);
            }
        });
    }
    
    /**
     * 在渲染线程中完成算法切换
     */
    public static void completeAlgorithmSwitch() {
        AbstractAlgorithm next = nextAlgorithm.getAndSet(null);
        if (next != null) {
            AbstractAlgorithm old = currentAlgorithm.getAndSet(next);
            if (old != null && old != SuperResolution.defaultAlgorithm) {
                // 延迟销毁旧算法，避免阻塞渲染
                CompletableFuture.runAsync(() -> {
                    try {
                        old.destroy();
                        SuperResolution.LOGGER.info("旧算法已销毁");
                    } catch (Exception e) {
                        SuperResolution.LOGGER.warn("销毁旧算法时出错: {}", e.getMessage());
                    }
                });
            }
            SuperResolution.LOGGER.info("算法切换完成");
        }
    }
    
    /**
     * 优化的算法调度
     */
    public static void dispatch(ITexture color, ITexture depth, ITexture motionVectors, ITexture output) {
        long startTime = System.nanoTime();
        
        try {
            // 检查是否需要完成算法切换
            completeAlgorithmSwitch();
            
            AbstractAlgorithm algorithm = getCurrentAlgorithm();
            if (algorithm == null) return;
            
            // 使用资源池获取临时资源
            try (ResourcePool.ManagedResources resources = resourcePool.acquire()) {
                InputResourceSet inputSet = new InputResourceSet(color, depth, motionVectors);
                
                // 执行算法
                algorithm.dispatch(inputSet, output);
            }
            
        } finally {
            // 性能监控
            long frameTime = System.nanoTime() - startTime;
            updatePerformanceStats(frameTime);
        }
    }
    
    private static void updatePerformanceStats(long frameTime) {
        lastFrameTime = frameTime;
        totalFrameTime += frameTime;
        frameCount++;
        
        if (frameCount >= PERF_SAMPLE_SIZE) {
            double avgFrameTime = totalFrameTime / (double) frameCount / 1_000_000.0; // 转换为毫秒
            SuperResolution.LOGGER.debug("算法平均帧时间: {:.2f}ms", avgFrameTime);
            
            // 重置统计
            totalFrameTime = 0;
            frameCount = 0;
        }
    }
    
    /**
     * 获取性能统计信息
     */
    public static PerformanceStats getPerformanceStats() {
        return new PerformanceStats(
            lastFrameTime / 1_000_000.0, // 转换为毫秒
            frameCount > 0 ? totalFrameTime / (double) frameCount / 1_000_000.0 : 0.0,
            BufferPool.getStats()
        );
    }
    
    /**
     * 清理资源
     */
    public static void destroy() {
        AbstractAlgorithm algorithm = currentAlgorithm.getAndSet(null);
        if (algorithm != null && algorithm != SuperResolution.defaultAlgorithm) {
            algorithm.destroy();
        }
        
        AbstractAlgorithm next = nextAlgorithm.getAndSet(null);
        if (next != null) {
            next.destroy();
        }
        
        resourcePool.destroy();
        BufferPool.cleanup();
        
        SuperResolution.LOGGER.info("优化算法管理器已清理");
    }
    
    /**
     * 资源池管理
     */
    private static class ResourcePool {
        private boolean initialized = false;
        
        public void init() {
            if (!initialized) {
                // 初始化资源池
                initialized = true;
            }
        }
        
        public ManagedResources acquire() {
            return new ManagedResources();
        }
        
        public void destroy() {
            initialized = false;
        }
        
        public static class ManagedResources implements AutoCloseable {
            // 这里可以管理临时纹理、缓冲区等资源
            
            @Override
            public void close() {
                // 释放资源回池中
            }
        }
    }
    
    /**
     * 性能统计信息
     */
    public static class PerformanceStats {
        public final double lastFrameTimeMs;
        public final double avgFrameTimeMs;
        public final BufferPool.MemoryStats memoryStats;
        
        public PerformanceStats(double lastFrameTimeMs, double avgFrameTimeMs, BufferPool.MemoryStats memoryStats) {
            this.lastFrameTimeMs = lastFrameTimeMs;
            this.avgFrameTimeMs = avgFrameTimeMs;
            this.memoryStats = memoryStats;
        }
        
        @Override
        public String toString() {
            return String.format("PerformanceStats{lastFrame=%.2fms, avgFrame=%.2fms, memory=%s}",
                               lastFrameTimeMs, avgFrameTimeMs, memoryStats);
        }
    }
}