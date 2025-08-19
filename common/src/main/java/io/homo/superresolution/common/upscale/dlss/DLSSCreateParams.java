package io.homo.superresolution.common.upscale.dlss;

/**
 * DLSS创建参数
 */
public class DLSSCreateParams {
    // 特性标志
    public int featureFlags;
    
    // 最大渲染分辨率
    public int maxRenderWidth;
    public int maxRenderHeight;
    
    // 最大显示分辨率
    public int maxDisplayWidth;
    public int maxDisplayHeight;
    
    // 应用程序ID（用于DLSS优化）
    public int applicationId = 0;
    
    // 引擎类型（可选）
    public String engineType = "Custom";
    public String engineVersion = "1.0";
    
    // 项目ID（用于NVIDIA分析）
    public String projectId = "SuperResolution";
    
    public DLSSCreateParams() {
        // 默认构造函数
    }
    
    /**
     * 验证参数有效性
     */
    public boolean validate() {
        return maxRenderWidth > 0 && maxRenderHeight > 0 &&
               maxDisplayWidth > 0 && maxDisplayHeight > 0 &&
               maxDisplayWidth >= maxRenderWidth && 
               maxDisplayHeight >= maxRenderHeight;
    }
    
    @Override
    public String toString() {
        return String.format("DLSSCreateParams{flags=0x%x, maxRender=%dx%d, maxDisplay=%dx%d}", 
            featureFlags, maxRenderWidth, maxRenderHeight, maxDisplayWidth, maxDisplayHeight);
    }
}