package io.homo.superresolution.common.upscale.dlss;

import io.homo.superresolution.common.upscale.dlss.enums.DLSSQuality;

/**
 * DLSS配置类
 */
public class DLSSConfig {
    // 渲染分辨率
    public int renderWidth;
    public int renderHeight;
    
    // 显示分辨率
    public int displayWidth;
    public int displayHeight;
    
    // 质量设置
    public DLSSQuality quality = DLSSQuality.BALANCED;
    
    // 特性标志
    public int featureFlags = 0;
    
    // 性能设置
    public boolean enableHDR = false;
    public boolean enableAutoExposure = false;
    public boolean invertDepth = false;
    public boolean jitteredMotionVectors = true;
    public boolean lowResMotionVectors = false;
    
    // 调试设置
    public boolean enableDebugOutput = false;
    public boolean showMetrics = false;
    
    /**
     * 验证配置的有效性
     */
    public boolean validate() {
        // 检查分辨率
        if (renderWidth <= 0 || renderHeight <= 0 || 
            displayWidth <= 0 || displayHeight <= 0) {
            return false;
        }
        
        // 检查放大比例是否合理
        float scaleX = (float) displayWidth / renderWidth;
        float scaleY = (float) displayHeight / renderHeight;
        
        if (scaleX < 1.0f || scaleY < 1.0f || scaleX > 4.0f || scaleY > 4.0f) {
            return false;
        }
        
        // 检查质量模式是否支持当前分辨率
        return quality.isSupported(displayWidth, displayHeight);
    }
    
    /**
     * 获取渲染分辨率缩放比例
     */
    public float getRenderScale() {
        return quality.getRenderScale().x;
    }
    
    /**
     * 获取放大倍数
     */
    public float getUpscaleFactor() {
        return quality.getUpscaleFactor();
    }
    
    /**
     * 重置为默认配置
     */
    public void reset() {
        quality = DLSSQuality.BALANCED;
        featureFlags = 0;
        enableHDR = false;
        enableAutoExposure = false;
        invertDepth = false;
        jitteredMotionVectors = true;
        lowResMotionVectors = false;
        enableDebugOutput = false;
        showMetrics = false;
    }
    
    @Override
    public String toString() {
        return String.format("DLSSConfig{quality=%s, render=%dx%d, display=%dx%d, scale=%.2f}", 
            quality, renderWidth, renderHeight, displayWidth, displayHeight, getRenderScale());
    }
}