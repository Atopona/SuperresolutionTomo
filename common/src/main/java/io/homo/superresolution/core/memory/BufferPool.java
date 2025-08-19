package io.homo.superresolution.core.memory;

import io.homo.superresolution.common.SuperResolution;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能内存缓冲区池
 * 减少频繁的内存分配和释放开销
 */
public class BufferPool {
    private static final int[] POOL_SIZES = {
        1024,           // 1KB
        4096,           // 4KB  
        16384,          // 16KB
        65536,          // 64KB
        262144,         // 256KB
        1048576,        // 1MB
        4194304,        // 4MB
        16777216        // 16MB
    };
    
    private static final int MAX_POOL_SIZE = 32; // 每个大小池最多缓存32个缓冲区
    private static final ConcurrentLinkedQueue<ByteBuffer>[] pools;
    private static final AtomicLong totalAllocated = new AtomicLong(0);
    private static final AtomicLong totalPooled = new AtomicLong(0);
    
    static {
        pools = new ConcurrentLinkedQueue[POOL_SIZES.length];
        for (int i = 0; i < POOL_SIZES.length; i++) {
            pools[i] = new ConcurrentLinkedQueue<>();
        }
    }
    
    /**
     * 获取指定大小的直接内存缓冲区
     */
    public static ByteBuffer acquire(int size) {
        int poolIndex = findPoolIndex(size);
        
        if (poolIndex >= 0) {
            ConcurrentLinkedQueue<ByteBuffer> pool = pools[poolIndex];
            ByteBuffer buffer = pool.poll();
            
            if (buffer != null) {
                buffer.clear();
                totalPooled.decrementAndGet();
                return buffer;
            }
        }
        
        // 池中没有合适的缓冲区，分配新的
        ByteBuffer buffer = MemoryUtil.memAlloc(size);
        totalAllocated.incrementAndGet();
        
        SuperResolution.LOGGER.debug("分配新缓冲区: {}KB, 总分配: {}", 
                                    size / 1024, totalAllocated.get());
        
        return buffer;
    }
    
    /**
     * 释放缓冲区回池中
     */
    public static void release(ByteBuffer buffer) {
        if (buffer == null) return;
        
        int capacity = buffer.capacity();
        int poolIndex = findExactPoolIndex(capacity);
        
        if (poolIndex >= 0) {
            ConcurrentLinkedQueue<ByteBuffer> pool = pools[poolIndex];
            
            if (pool.size() < MAX_POOL_SIZE) {
                buffer.clear();
                pool.offer(buffer);
                totalPooled.incrementAndGet();
                return;
            }
        }
        
        // 池已满或大小不匹配，直接释放
        MemoryUtil.memFree(buffer);
        totalAllocated.decrementAndGet();
    }
    
    /**
     * 批量获取缓冲区
     */
    public static ByteBuffer[] acquireBatch(int[] sizes) {
        ByteBuffer[] buffers = new ByteBuffer[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            buffers[i] = acquire(sizes[i]);
        }
        return buffers;
    }
    
    /**
     * 批量释放缓冲区
     */
    public static void releaseBatch(ByteBuffer[] buffers) {
        for (ByteBuffer buffer : buffers) {
            release(buffer);
        }
    }
    
    private static int findPoolIndex(int size) {
        for (int i = 0; i < POOL_SIZES.length; i++) {
            if (size <= POOL_SIZES[i]) {
                return i;
            }
        }
        return -1; // 超过最大池大小
    }
    
    private static int findExactPoolIndex(int size) {
        for (int i = 0; i < POOL_SIZES.length; i++) {
            if (size == POOL_SIZES[i]) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 预分配缓冲区池
     */
    public static void warmup() {
        SuperResolution.LOGGER.info("预热内存缓冲区池...");
        
        for (int i = 0; i < POOL_SIZES.length; i++) {
            int size = POOL_SIZES[i];
            ConcurrentLinkedQueue<ByteBuffer> pool = pools[i];
            
            // 为每个大小预分配几个缓冲区
            int preAllocCount = Math.min(4, MAX_POOL_SIZE / 2);
            for (int j = 0; j < preAllocCount; j++) {
                ByteBuffer buffer = MemoryUtil.memAlloc(size);
                pool.offer(buffer);
                totalAllocated.incrementAndGet();
                totalPooled.incrementAndGet();
            }
        }
        
        SuperResolution.LOGGER.info("内存缓冲区池预热完成，预分配: {}个缓冲区", totalPooled.get());
    }
    
    /**
     * 清理池中的缓冲区
     */
    public static void cleanup() {
        SuperResolution.LOGGER.info("清理内存缓冲区池...");
        
        long freedCount = 0;
        for (ConcurrentLinkedQueue<ByteBuffer> pool : pools) {
            ByteBuffer buffer;
            while ((buffer = pool.poll()) != null) {
                MemoryUtil.memFree(buffer);
                freedCount++;
            }
        }
        
        totalAllocated.addAndGet(-freedCount);
        totalPooled.set(0);
        
        SuperResolution.LOGGER.info("内存缓冲区池清理完成，释放: {}个缓冲区", freedCount);
    }
    
    /**
     * 获取内存使用统计
     */
    public static MemoryStats getStats() {
        long allocated = totalAllocated.get();
        long pooled = totalPooled.get();
        long inUse = allocated - pooled;
        
        return new MemoryStats(allocated, pooled, inUse);
    }
    
    public static class MemoryStats {
        public final long totalAllocated;
        public final long totalPooled;
        public final long inUse;
        
        public MemoryStats(long totalAllocated, long totalPooled, long inUse) {
            this.totalAllocated = totalAllocated;
            this.totalPooled = totalPooled;
            this.inUse = inUse;
        }
        
        @Override
        public String toString() {
            return String.format("MemoryStats{allocated=%d, pooled=%d, inUse=%d}", 
                               totalAllocated, totalPooled, inUse);
        }
    }
    
    /**
     * 自动管理的缓冲区包装器
     */
    public static class ManagedBuffer implements AutoCloseable {
        private final ByteBuffer buffer;
        
        public ManagedBuffer(int size) {
            this.buffer = acquire(size);
        }
        
        public ByteBuffer get() {
            return buffer;
        }
        
        @Override
        public void close() {
            release(buffer);
        }
    }
}