package io.homo.superresolution.common.upscale.dlss;

/**
 * DLSS性能统计信息
 */
public class DLSSPerformanceStats {
    // 推理时间统计（毫秒）
    public float averageInferenceTime = 0.0f;
    public float minInferenceTime = 0.0f;
    public float maxInferenceTime = 0.0f;
    public float lastInferenceTime = 0.0f;
    
    // 帧率统计
    public float averageFPS = 0.0f;
    public float minFPS = 0.0f;
    public float maxFPS = 0.0f;
    
    // GPU利用率
    public float gpuUtilization = 0.0f;
    
    // 内存使用（MB）
    public float memoryUsage = 0.0f;
    public float peakMemoryUsage = 0.0f;
    
    // 推理计数
    public long totalInferences = 0;
    public long successfulInferences = 0;
    public long failedInferences = 0;
    
    // 质量指标
    public float averageQualityScore = 0.0f; // DLSS内部质量评分
    
    // 时间戳
    public long lastUpdateTime = System.currentTimeMillis();
    
    public DLSSPerformanceStats() {
        // 默认构造函数
    }
    
    /**
     * 获取成功率
     */
    public float getSuccessRate() {
        if (totalInferences == 0) {
            return 0.0f;
        }
        return (float) successfulInferences / totalInferences * 100.0f;
    }
    
    /**
     * 获取失败率
     */
    public float getFailureRate() {
        return 100.0f - getSuccessRate();
    }
    
    /**
     * 重置统计信息
     */
    public void reset() {
        averageInferenceTime = 0.0f;
        minInferenceTime = 0.0f;
        maxInferenceTime = 0.0f;
        lastInferenceTime = 0.0f;
        
        averageFPS = 0.0f;
        minFPS = 0.0f;
        maxFPS = 0.0f;
        
        gpuUtilization = 0.0f;
        
        memoryUsage = 0.0f;
        peakMemoryUsage = 0.0f;
        
        totalInferences = 0;
        successfulInferences = 0;
        failedInferences = 0;
        
        averageQualityScore = 0.0f;
        
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 格式化为可读字符串
     */
    public String formatStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("DLSS性能统计:\n");
        sb.append(String.format("  推理时间: %.2fms (平均), %.2fms (最小), %.2fms (最大)\n", 
            averageInferenceTime, minInferenceTime, maxInferenceTime));
        sb.append(String.format("  帧率: %.1f FPS (平均), %.1f FPS (最小), %.1f FPS (最大)\n", 
            averageFPS, minFPS, maxFPS));
        sb.append(String.format("  GPU利用率: %.1f%%\n", gpuUtilization));
        sb.append(String.format("  内存使用: %.1f MB (当前), %.1f MB (峰值)\n", 
            memoryUsage, peakMemoryUsage));
        sb.append(String.format("  推理统计: %d 总计, %d 成功, %d 失败 (成功率: %.1f%%)\n", 
            totalInferences, successfulInferences, failedInferences, getSuccessRate()));
        sb.append(String.format("  质量评分: %.2f\n", averageQualityScore));
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("DLSSPerformanceStats{avgTime=%.2fms, avgFPS=%.1f, success=%.1f%%}", 
            averageInferenceTime, averageFPS, getSuccessRate());
    }
}