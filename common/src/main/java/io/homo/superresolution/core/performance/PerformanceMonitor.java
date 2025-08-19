package io.homo.superresolution.core.performance;

import io.homo.superresolution.common.SuperResolution;
import io.homo.superresolution.common.config.SuperResolutionConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 性能监控系统
 * 提供详细的性能指标收集和分析
 */
public class PerformanceMonitor {
    private static final ConcurrentHashMap<String, PerformanceCounter> counters = new ConcurrentHashMap<>();
    private static final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
    private static boolean enabled = false;
    
    public static void init() {
        enabled = SuperResolutionConfig.isEnablePerformanceMonitoring();
        if (enabled) {
            SuperResolution.LOGGER.info("性能监控已启用");
            
            // 注册默认计数器
            registerCounter("frame_time", "帧时间", "ms");
            registerCounter("algorithm_time", "算法执行时间", "ms");
            registerCounter("state_switches", "状态切换次数", "次");
            registerCounter("shader_compiles", "着色器编译次数", "次");
            registerCounter("memory_allocations", "内存分配次数", "次");
            registerCounter("gl_calls", "OpenGL调用次数", "次");
        }
    }
    
    public static void registerCounter(String name, String displayName, String unit) {
        if (!enabled) return;
        counters.put(name, new PerformanceCounter(name, displayName, unit));
    }
    
    public static void recordTime(String counterName, long timeNanos) {
        if (!enabled) return;
        PerformanceCounter counter = counters.get(counterName);
        if (counter != null) {
            counter.recordTime(timeNanos);
        }
    }
    
    public static void increment(String counterName) {
        increment(counterName, 1);
    }
    
    public static void increment(String counterName, long value) {
        if (!enabled) return;
        PerformanceCounter counter = counters.get(counterName);
        if (counter != null) {
            counter.increment(value);
        }
    }
    
    public static void recordValue(String counterName, double value) {
        if (!enabled) return;
        PerformanceCounter counter = counters.get(counterName);
        if (counter != null) {
            counter.recordValue(value);
        }
    }
    
    /**
     * 自动计时器
     */
    public static AutoTimer startTimer(String counterName) {
        return enabled ? new AutoTimer(counterName) : AutoTimer.NOOP;
    }
    
    /**
     * 获取性能报告
     */
    public static PerformanceReport getReport() {
        if (!enabled) return PerformanceReport.EMPTY;
        
        PerformanceReport.Builder builder = new PerformanceReport.Builder();
        builder.setUptime(System.currentTimeMillis() - startTime.get());
        
        for (PerformanceCounter counter : counters.values()) {
            builder.addCounter(counter.getName(), counter.getStats());
        }
        
        return builder.build();
    }
    
    /**
     * 重置所有计数器
     */
    public static void reset() {
        if (!enabled) return;
        counters.values().forEach(PerformanceCounter::reset);
        startTime.set(System.currentTimeMillis());
    }
    
    /**
     * 打印性能报告
     */
    public static void printReport() {
        if (!enabled) return;
        
        PerformanceReport report = getReport();
        SuperResolution.LOGGER.info("=== 性能报告 ===");
        SuperResolution.LOGGER.info("运行时间: {}ms", report.getUptimeMs());
        
        for (String counterName : report.getCounterNames()) {
            CounterStats stats = report.getCounterStats(counterName);
            PerformanceCounter counter = counters.get(counterName);
            if (counter != null) {
                SuperResolution.LOGGER.info("{}: {}", counter.getDisplayName(), formatStats(stats, counter.getUnit()));
            }
        }
        SuperResolution.LOGGER.info("===============");
    }
    
    private static String formatStats(CounterStats stats, String unit) {
        if (stats.getCount() == 0) {
            return "无数据";
        }
        
        return String.format("总计: %.2f%s, 平均: %.2f%s, 最小: %.2f%s, 最大: %.2f%s, 次数: %d",
                           stats.getTotal(), unit,
                           stats.getAverage(), unit,
                           stats.getMin(), unit,
                           stats.getMax(), unit,
                           stats.getCount());
    }
    
    public static class AutoTimer implements AutoCloseable {
        public static final AutoTimer NOOP = new AutoTimer(null);
        
        private final String counterName;
        private final long startTime;
        
        public AutoTimer(String counterName) {
            this.counterName = counterName;
            this.startTime = counterName != null ? System.nanoTime() : 0;
        }
        
        @Override
        public void close() {
            if (counterName != null) {
                long elapsed = System.nanoTime() - startTime;
                recordTime(counterName, elapsed);
            }
        }
    }
    
    private static class PerformanceCounter {
        private final String name;
        private final String displayName;
        private final String unit;
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private volatile double minValue = Double.MAX_VALUE;
        private volatile double maxValue = Double.MIN_VALUE;
        
        public PerformanceCounter(String name, String displayName, String unit) {
            this.name = name;
            this.displayName = displayName;
            this.unit = unit;
        }
        
        public void recordTime(long nanos) {
            double millis = nanos / 1_000_000.0;
            recordValue(millis);
        }
        
        public void increment(long value) {
            count.add(value);
        }
        
        public void recordValue(double value) {
            count.increment();
            totalNanos.add((long) (value * 1_000_000)); // 存储为纳秒
            
            // 更新最小值和最大值
            updateMin(value);
            updateMax(value);
        }
        
        private void updateMin(double value) {
            double current = minValue;
            while (value < current && !compareAndSetMin(current, value)) {
                current = minValue;
            }
        }
        
        private void updateMax(double value) {
            double current = maxValue;
            while (value > current && !compareAndSetMax(current, value)) {
                current = maxValue;
            }
        }
        
        private boolean compareAndSetMin(double expect, double update) {
            // 简化的CAS操作，实际应该使用AtomicReference<Double>
            synchronized (this) {
                if (minValue == expect) {
                    minValue = update;
                    return true;
                }
                return false;
            }
        }
        
        private boolean compareAndSetMax(double expect, double update) {
            synchronized (this) {
                if (maxValue == expect) {
                    maxValue = update;
                    return true;
                }
                return false;
            }
        }
        
        public void reset() {
            count.reset();
            totalNanos.reset();
            minValue = Double.MAX_VALUE;
            maxValue = Double.MIN_VALUE;
        }
        
        public CounterStats getStats() {
            long countValue = count.sum();
            if (countValue == 0) {
                return new CounterStats(0, 0.0, 0.0, 0.0, 0.0);
            }
            
            double total = totalNanos.sum() / 1_000_000.0; // 转换回毫秒
            double average = total / countValue;
            double min = minValue == Double.MAX_VALUE ? 0.0 : minValue;
            double max = maxValue == Double.MIN_VALUE ? 0.0 : maxValue;
            
            return new CounterStats(countValue, total, average, min, max);
        }
        
        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public String getUnit() { return unit; }
    }
    
    public static class CounterStats {
        private final long count;
        private final double total;
        private final double average;
        private final double min;
        private final double max;
        
        public CounterStats(long count, double total, double average, double min, double max) {
            this.count = count;
            this.total = total;
            this.average = average;
            this.min = min;
            this.max = max;
        }
        
        public long getCount() { return count; }
        public double getTotal() { return total; }
        public double getAverage() { return average; }
        public double getMin() { return min; }
        public double getMax() { return max; }
    }
    
    public static class PerformanceReport {
        public static final PerformanceReport EMPTY = new PerformanceReport(0, new ConcurrentHashMap<>());
        
        private final long uptimeMs;
        private final ConcurrentHashMap<String, CounterStats> counterStats;
        
        private PerformanceReport(long uptimeMs, ConcurrentHashMap<String, CounterStats> counterStats) {
            this.uptimeMs = uptimeMs;
            this.counterStats = counterStats;
        }
        
        public long getUptimeMs() { return uptimeMs; }
        public java.util.Set<String> getCounterNames() { return counterStats.keySet(); }
        public CounterStats getCounterStats(String name) { return counterStats.get(name); }
        
        public static class Builder {
            private long uptimeMs;
            private final ConcurrentHashMap<String, CounterStats> counterStats = new ConcurrentHashMap<>();
            
            public Builder setUptime(long uptimeMs) {
                this.uptimeMs = uptimeMs;
                return this;
            }
            
            public Builder addCounter(String name, CounterStats stats) {
                counterStats.put(name, stats);
                return this;
            }
            
            public PerformanceReport build() {
                return new PerformanceReport(uptimeMs, counterStats);
            }
        }
    }
}