package io.homo.superresolution.common.upscale.dlss.enums;

/**
 * DLSS特性标志枚举
 */
public enum DLSSFeatureFlags {
    /**
     * 无特殊标志
     */
    DLSS_FEATURE_FLAGS_NONE(0x0),
    
    /**
     * 启用HDR支持
     */
    DLSS_FEATURE_FLAGS_HDR(0x1),
    
    /**
     * 启用运动向量低分辨率
     */
    DLSS_FEATURE_FLAGS_MV_LOW_RES(0x2),
    
    /**
     * 启用运动向量抖动取消
     */
    DLSS_FEATURE_FLAGS_MV_JITTERED(0x4),
    
    /**
     * 启用深度反转
     */
    DLSS_FEATURE_FLAGS_DEPTH_INVERTED(0x8),
    
    /**
     * 启用自动曝光
     */
    DLSS_FEATURE_FLAGS_AUTO_EXPOSURE(0x10),
    
    /**
     * 启用运动向量预乘
     */
    DLSS_FEATURE_FLAGS_MV_PREMULTIPLIED(0x20);
    
    private final int value;
    
    DLSSFeatureFlags(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    /**
     * 组合多个标志
     */
    public static int combine(DLSSFeatureFlags... flags) {
        int result = 0;
        for (DLSSFeatureFlags flag : flags) {
            result |= flag.value;
        }
        return result;
    }
    
    /**
     * 检查是否包含指定标志
     */
    public static boolean hasFlag(int flags, DLSSFeatureFlags flag) {
        return (flags & flag.value) != 0;
    }
}