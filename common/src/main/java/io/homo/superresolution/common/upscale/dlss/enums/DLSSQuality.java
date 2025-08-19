package io.homo.superresolution.common.upscale.dlss.enums;

import io.homo.superresolution.core.math.Vector2f;

/**
 * DLSS质量预设枚举
 */
public enum DLSSQuality {
    /**
     * 性能模式 - 最高性能，最低质量
     * 渲染分辨率: 50% (2x放大)
     */
    PERFORMANCE("performance", "性能", new Vector2f(0.5f, 0.5f), 0),
    
    /**
     * 平衡模式 - 性能与质量平衡
     * 渲染分辨率: 58% (1.7x放大)
     */
    BALANCED("balanced", "平衡", new Vector2f(0.58f, 0.58f), 1),
    
    /**
     * 质量模式 - 更高质量，较低性能
     * 渲染分辨率: 67% (1.5x放大)
     */
    QUALITY("quality", "质量", new Vector2f(0.67f, 0.67f), 2),
    
    /**
     * 超高质量模式 - 最高质量，最低性能
     * 渲染分辨率: 77% (1.3x放大)
     */
    ULTRA_QUALITY("ultra_quality", "超高质量", new Vector2f(0.77f, 0.77f), 3),
    
    /**
     * 超级性能模式 - 极致性能优化
     * 渲染分辨率: 33% (3x放大)
     */
    ULTRA_PERFORMANCE("ultra_performance", "超级性能", new Vector2f(0.33f, 0.33f), 4);
    
    private final String configName;
    private final String displayName;
    private final Vector2f renderScale;
    private final int nativeValue;
    
    DLSSQuality(String configName, String displayName, Vector2f renderScale, int nativeValue) {
        this.configName = configName;
        this.displayName = displayName;
        this.renderScale = renderScale;
        this.nativeValue = nativeValue;
    }
    
    public String getConfigName() {
        return configName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public Vector2f getRenderScale() {
        return renderScale;
    }
    
    public int getNativeValue() {
        return nativeValue;
    }
    
    /**
     * 获取放大倍数
     */
    public float getUpscaleFactor() {
        return 1.0f / renderScale.x;
    }
    
    /**
     * 从配置字符串获取质量设置
     */
    public static DLSSQuality fromString(String configName) {
        for (DLSSQuality quality : values()) {
            if (quality.configName.equalsIgnoreCase(configName)) {
                return quality;
            }
        }
        return BALANCED; // 默认返回平衡模式
    }
    
    /**
     * 从原生值获取质量设置
     */
    public static DLSSQuality fromNativeValue(int nativeValue) {
        for (DLSSQuality quality : values()) {
            if (quality.nativeValue == nativeValue) {
                return quality;
            }
        }
        return BALANCED;
    }
    
    /**
     * 获取推荐的最小渲染分辨率
     */
    public Vector2f getMinimumRenderResolution() {
        // DLSS对最小渲染分辨率有要求
        return switch (this) {
            case ULTRA_PERFORMANCE -> new Vector2f(640, 360);
            case PERFORMANCE -> new Vector2f(720, 405);
            case BALANCED -> new Vector2f(835, 470);
            case QUALITY -> new Vector2f(960, 540);
            case ULTRA_QUALITY -> new Vector2f(1108, 623);
        };
    }
    
    /**
     * 检查给定的显示分辨率是否支持此质量模式
     */
    public boolean isSupported(int displayWidth, int displayHeight) {
        Vector2f minRes = getMinimumRenderResolution();
        int renderWidth = (int)(displayWidth * renderScale.x);
        int renderHeight = (int)(displayHeight * renderScale.y);
        
        return renderWidth >= minRes.x && renderHeight >= minRes.y;
    }
    
    @Override
    public String toString() {
        return displayName + " (" + String.format("%.0f%%", renderScale.x * 100) + ")";
    }
}