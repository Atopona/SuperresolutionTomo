package io.homo.superresolution.common.upscale.nis.enums;

/**
 * GPU架构枚举，用于NIS性能优化
 */
public enum NISGPUArchitecture {
    NVIDIA_Generic(0, "NVIDIA", 32, 24, true),
    AMD_Generic(1, "AMD", 64, 16, true),
    Intel_Generic(2, "Intel", 16, 16, false),
    NVIDIA_Generic_fp16(3, "NVIDIA FP16", 32, 24, true),
    NVIDIA(0, "NVIDIA", 32, 24, true),
    AMD(1, "AMD", 64, 16, true),
    INTEL(2, "Intel", 16, 16, false),
    UNKNOWN(4, "Unknown", 32, 24, false);
    
    private final int value;
    private final String displayName;
    private final int preferredWorkGroupX;
    private final int preferredWorkGroupY;
    private final boolean supportsOptimizedPath;
    
    NISGPUArchitecture(int value, String displayName, int preferredWorkGroupX, int preferredWorkGroupY, boolean supportsOptimizedPath) {
        this.value = value;
        this.displayName = displayName;
        this.preferredWorkGroupX = preferredWorkGroupX;
        this.preferredWorkGroupY = preferredWorkGroupY;
        this.supportsOptimizedPath = supportsOptimizedPath;
    }
    
    public int getValue() {
        return value;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getPreferredWorkGroupX() {
        return preferredWorkGroupX;
    }
    
    public int getPreferredWorkGroupY() {
        return preferredWorkGroupY;
    }
    
    public boolean supportsOptimizedPath() {
        return supportsOptimizedPath;
    }
    
    /**
     * 根据GPU渲染器字符串检测架构
     */
    public static NISGPUArchitecture detectFromRenderer(String renderer) {
        String lowerRenderer = renderer.toLowerCase();
        
        if (lowerRenderer.contains("nvidia") || lowerRenderer.contains("geforce") || lowerRenderer.contains("quadro")) {
            return NVIDIA;
        } else if (lowerRenderer.contains("amd") || lowerRenderer.contains("radeon")) {
            return AMD;
        } else if (lowerRenderer.contains("intel")) {
            return INTEL;
        } else {
            return UNKNOWN;
        }
    }
}
