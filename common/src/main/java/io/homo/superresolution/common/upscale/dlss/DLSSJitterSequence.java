package io.homo.superresolution.common.upscale.dlss;

import io.homo.superresolution.common.upscale.dlss.enums.DLSSQuality;
import io.homo.superresolution.core.math.Vector2f;

/**
 * DLSS抖动序列生成器
 * 基于NVIDIA推荐的Halton序列实现
 */
public class DLSSJitterSequence {
    
    // Halton序列的基数
    private static final int HALTON_BASE_X = 2;
    private static final int HALTON_BASE_Y = 3;
    
    // 序列长度（DLSS推荐使用8或16帧的循环）
    private static final int SEQUENCE_LENGTH = 16;
    
    // 预计算的Halton序列
    private final Vector2f[] haltonSequence;
    
    public DLSSJitterSequence() {
        haltonSequence = new Vector2f[SEQUENCE_LENGTH];
        generateHaltonSequence();
    }
    
    /**
     * 生成Halton序列
     */
    private void generateHaltonSequence() {
        for (int i = 0; i < SEQUENCE_LENGTH; i++) {
            float x = halton(i + 1, HALTON_BASE_X);
            float y = halton(i + 1, HALTON_BASE_Y);
            haltonSequence[i] = new Vector2f(x, y);
        }
    }
    
    /**
     * 计算Halton序列值
     */
    private float halton(int index, int base) {
        float result = 0.0f;
        float fraction = 1.0f / base;
        
        while (index > 0) {
            result += (index % base) * fraction;
            index /= base;
            fraction /= base;
        }
        
        return result;
    }
    
    /**
     * 获取指定帧的抖动偏移
     * 
     * @param frameIndex 帧索引
     * @param renderSize 渲染分辨率
     * @param displaySize 显示分辨率
     * @param quality DLSS质量设置
     * @return 抖动偏移（像素单位）
     */
    public Vector2f getJitterOffset(int frameIndex, Vector2f renderSize, Vector2f displaySize, DLSSQuality quality) {
        // 获取循环中的位置
        int sequenceIndex = frameIndex % SEQUENCE_LENGTH;
        Vector2f haltonSample = haltonSequence[sequenceIndex];
        
        // 将[0,1]范围映射到[-0.5, 0.5]
        float jitterX = haltonSample.x - 0.5f;
        float jitterY = haltonSample.y - 0.5f;
        
        // 根据质量调整抖动强度
        float jitterScale = getJitterScale(quality);
        jitterX *= jitterScale;
        jitterY *= jitterScale;
        
        // 转换为像素偏移（相对于渲染分辨率）
        float pixelJitterX = jitterX * 2.0f; // DLSS推荐的像素偏移范围
        float pixelJitterY = jitterY * 2.0f;
        
        return new Vector2f(pixelJitterX, pixelJitterY);
    }
    
    /**
     * 根据质量设置获取抖动缩放因子
     */
    private float getJitterScale(DLSSQuality quality) {
        return switch (quality) {
            case ULTRA_PERFORMANCE -> 1.0f;  // 最大抖动，补偿低分辨率
            case PERFORMANCE -> 0.9f;
            case BALANCED -> 0.8f;
            case QUALITY -> 0.7f;
            case ULTRA_QUALITY -> 0.6f;      // 最小抖动，高分辨率下不需要太多抖动
        };
    }
    
    /**
     * 获取投影矩阵的抖动偏移
     * 用于调整投影矩阵以实现子像素采样
     * 
     * @param frameIndex 帧索引
     * @param renderSize 渲染分辨率
     * @param quality DLSS质量设置
     * @return 投影矩阵偏移
     */
    public Vector2f getProjectionJitter(int frameIndex, Vector2f renderSize, DLSSQuality quality) {
        Vector2f jitter = getJitterOffset(frameIndex, renderSize, renderSize, quality);
        
        // 转换为NDC空间的偏移 [-1, 1]
        float ndcJitterX = (jitter.x / renderSize.x) * 2.0f;
        float ndcJitterY = (jitter.y / renderSize.y) * 2.0f;
        
        return new Vector2f(ndcJitterX, ndcJitterY);
    }
    
    /**
     * 重置序列（用于场景切换等情况）
     */
    public void reset() {
        // Halton序列是确定性的，不需要重置状态
        // 但可以在这里添加其他重置逻辑
    }
    
    /**
     * 获取序列长度
     */
    public int getSequenceLength() {
        return SEQUENCE_LENGTH;
    }
    
    /**
     * 检查是否为序列的开始
     */
    public boolean isSequenceStart(int frameIndex) {
        return (frameIndex % SEQUENCE_LENGTH) == 0;
    }
    
    /**
     * 获取当前序列位置的进度 [0.0, 1.0]
     */
    public float getSequenceProgress(int frameIndex) {
        int sequenceIndex = frameIndex % SEQUENCE_LENGTH;
        return (float) sequenceIndex / SEQUENCE_LENGTH;
    }
}