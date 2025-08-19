package io.homo.superresolution.common.upscale.dlss;

import io.homo.superresolution.common.SuperResolution;

/**
 * DLSS原生库接口
 * 通过JNI调用NVIDIA NGX SDK
 */
public class DLSSNative {
    
    // 原生库加载状态
    private static boolean nativeLibraryLoaded = false;
    private static boolean dlssSupported = false;
    
    // DLSS句柄
    private long dlssHandle = 0;
    
    static {
        loadNativeLibrary();
    }
    
    /**
     * 加载原生库
     */
    private static void loadNativeLibrary() {
        try {
            // 尝试加载DLSS原生库
            System.loadLibrary("superresolution_dlss");
            nativeLibraryLoaded = true;
            
            // 检查DLSS支持
            dlssSupported = nativeIsDLSSSupported();
            
            if (dlssSupported) {
                SuperResolution.LOGGER.info("DLSS原生库加载成功，DLSS支持已启用");
            } else {
                SuperResolution.LOGGER.warn("DLSS原生库加载成功，但当前系统不支持DLSS");
            }
        } catch (UnsatisfiedLinkError e) {
            SuperResolution.LOGGER.warn("无法加载DLSS原生库: {}", e.getMessage());
            nativeLibraryLoaded = false;
            dlssSupported = false;
        }
    }
    
    /**
     * 检查DLSS是否受支持
     */
    public static boolean isDLSSSupported() {
        return nativeLibraryLoaded && dlssSupported;
    }
    
    /**
     * 获取DLSS版本
     */
    public static String getDLSSVersion() {
        if (!isDLSSSupported()) {
            return "不支持";
        }
        return nativeGetDLSSVersion();
    }
    
    /**
     * 初始化DLSS
     */
    public boolean initialize() {
        if (!isDLSSSupported()) {
            return false;
        }
        
        try {
            dlssHandle = nativeInitialize();
            return dlssHandle != 0;
        } catch (Exception e) {
            SuperResolution.LOGGER.error("DLSS初始化失败", e);
            return false;
        }
    }
    
    /**
     * 创建DLSS特性
     */
    public boolean createFeature(DLSSCreateParams params) {
        if (dlssHandle == 0) {
            return false;
        }
        
        try {
            return nativeCreateFeature(dlssHandle, params);
        } catch (Exception e) {
            SuperResolution.LOGGER.error("DLSS特性创建失败", e);
            return false;
        }
    }
    
    /**
     * 执行DLSS推理
     */
    public boolean evaluate(DLSSEvaluateParams params) {
        if (dlssHandle == 0) {
            return false;
        }
        
        try {
            return nativeEvaluate(dlssHandle, params);
        } catch (Exception e) {
            SuperResolution.LOGGER.error("DLSS推理执行失败", e);
            return false;
        }
    }
    
    /**
     * 释放DLSS特性
     */
    public void releaseFeature() {
        if (dlssHandle != 0) {
            try {
                nativeReleaseFeature(dlssHandle);
            } catch (Exception e) {
                SuperResolution.LOGGER.error("DLSS特性释放失败", e);
            }
        }
    }
    
    /**
     * 关闭DLSS
     */
    public void shutdown() {
        if (dlssHandle != 0) {
            try {
                nativeShutdown(dlssHandle);
                dlssHandle = 0;
            } catch (Exception e) {
                SuperResolution.LOGGER.error("DLSS关闭失败", e);
            }
        }
    }
    
    /**
     * 获取性能统计
     */
    public DLSSPerformanceStats getPerformanceStats() {
        if (dlssHandle == 0) {
            return new DLSSPerformanceStats();
        }
        
        try {
            return nativeGetPerformanceStats(dlssHandle);
        } catch (Exception e) {
            SuperResolution.LOGGER.error("获取DLSS性能统计失败", e);
            return new DLSSPerformanceStats();
        }
    }
    
    // 原生方法声明
    
    /**
     * 检查系统是否支持DLSS
     */
    private static native boolean nativeIsDLSSSupported();
    
    /**
     * 获取DLSS版本信息
     */
    private static native String nativeGetDLSSVersion();
    
    /**
     * 初始化DLSS
     */
    private native long nativeInitialize();
    
    /**
     * 创建DLSS特性
     */
    private native boolean nativeCreateFeature(long handle, DLSSCreateParams params);
    
    /**
     * 执行DLSS推理
     */
    private native boolean nativeEvaluate(long handle, DLSSEvaluateParams params);
    
    /**
     * 释放DLSS特性
     */
    private native void nativeReleaseFeature(long handle);
    
    /**
     * 关闭DLSS
     */
    private native void nativeShutdown(long handle);
    
    /**
     * 获取性能统计
     */
    private native DLSSPerformanceStats nativeGetPerformanceStats(long handle);
}