package io.homo.superresolution.common.upscale.dlss;

/**
 * DLSS推理参数
 */
public class DLSSEvaluateParams {
    // 输入纹理ID
    public int colorTextureId;
    public int depthTextureId;
    public int motionVectorTextureId;
    public int exposureTextureId = 0; // 可选
    
    // 输出纹理ID
    public int outputTextureId;
    
    // 分辨率
    public int renderWidth;
    public int renderHeight;
    public int displayWidth;
    public int displayHeight;
    
    // 时间参数
    public float frameTimeDelta; // 帧时间间隔（秒）
    public boolean reset; // 是否重置累积
    
    // 抖动偏移（像素单位）
    public float jitterOffsetX;
    public float jitterOffsetY;
    
    // 相机参数
    public float cameraFOV; // 视野角度（弧度）
    public float cameraNear; // 近平面距离
    public float cameraFar; // 远平面距离
    
    // 质量设置
    public int quality; // DLSS质量模式
    
    // 运动向量缩放（如果运动向量不是全分辨率）
    public float motionVectorScaleX = 1.0f;
    public float motionVectorScaleY = 1.0f;
    
    // 深度范围
    public float depthNear = 0.0f;
    public float depthFar = 1.0f;
    
    // 预乘alpha（如果颜色纹理使用预乘alpha）
    public boolean premultipliedAlpha = false;
    
    // 透明度阈值
    public float transparencyMask = 1.0f;
    
    // 曝光缩放
    public float exposureScale = 1.0f;
    
    public DLSSEvaluateParams() {
        // 默认构造函数
    }
    
    /**
     * 验证参数有效性
     */
    public boolean validate() {
        // 检查必需的纹理ID
        if (colorTextureId <= 0 || depthTextureId <= 0 || 
            motionVectorTextureId <= 0 || outputTextureId <= 0) {
            return false;
        }
        
        // 检查分辨率
        if (renderWidth <= 0 || renderHeight <= 0 || 
            displayWidth <= 0 || displayHeight <= 0) {
            return false;
        }
        
        // 检查时间参数
        if (frameTimeDelta < 0) {
            return false;
        }
        
        // 检查相机参数
        if (cameraFOV <= 0 || cameraNear <= 0 || cameraFar <= cameraNear) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public String toString() {
        return String.format("DLSSEvaluateParams{render=%dx%d, display=%dx%d, quality=%d, jitter=(%.3f,%.3f)}", 
            renderWidth, renderHeight, displayWidth, displayHeight, quality, jitterOffsetX, jitterOffsetY);
    }
}