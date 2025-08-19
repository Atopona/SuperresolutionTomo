#pragma once

#include <jni.h>
#include <memory>
#include <string>

// DLSS相关的结构体定义
struct DLSSCreateParams {
    int featureFlags;
    int maxRenderWidth;
    int maxRenderHeight;
    int maxDisplayWidth;
    int maxDisplayHeight;
    int applicationId;
    std::string engineType;
    std::string engineVersion;
    std::string projectId;
};

struct DLSSEvaluateParams {
    int colorTextureId;
    int depthTextureId;
    int motionVectorTextureId;
    int exposureTextureId;
    int outputTextureId;
    
    int renderWidth;
    int renderHeight;
    int displayWidth;
    int displayHeight;
    
    float frameTimeDelta;
    bool reset;
    
    float jitterOffsetX;
    float jitterOffsetY;
    
    float cameraFOV;
    float cameraNear;
    float cameraFar;
    
    int quality;
    
    float motionVectorScaleX;
    float motionVectorScaleY;
    float depthNear;
    float depthFar;
    bool premultipliedAlpha;
    float transparencyMask;
    float exposureScale;
};

struct DLSSPerformanceStats {
    float averageInferenceTime;
    float minInferenceTime;
    float maxInferenceTime;
    float lastInferenceTime;
    
    float averageFPS;
    float minFPS;
    float maxFPS;
    
    float gpuUtilization;
    float memoryUsage;
    float peakMemoryUsage;
    
    long totalInferences;
    long successfulInferences;
    long failedInferences;
    
    float averageQualityScore;
    long lastUpdateTime;
};

// DLSS包装器类
class DLSSWrapper {
public:
    DLSSWrapper();
    ~DLSSWrapper();
    
    // 静态方法
    static bool IsDLSSSupported();
    static std::string GetDLSSVersion();
    
    // 实例方法
    bool Initialize();
    bool CreateFeature(const DLSSCreateParams& params);
    bool Evaluate(const DLSSEvaluateParams& params);
    void ReleaseFeature();
    void Shutdown();
    DLSSPerformanceStats GetPerformanceStats();
    
private:
    class Impl;
    std::unique_ptr<Impl> pImpl;
};

// JNI导出函数
extern "C" {
    // 静态方法
    JNIEXPORT jboolean JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeIsDLSSSupported(JNIEnv* env, jclass clazz);
    JNIEXPORT jstring JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeGetDLSSVersion(JNIEnv* env, jclass clazz);
    
    // 实例方法
    JNIEXPORT jlong JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeInitialize(JNIEnv* env, jobject obj);
    JNIEXPORT jboolean JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeCreateFeature(JNIEnv* env, jobject obj, jlong handle, jobject params);
    JNIEXPORT jboolean JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeEvaluate(JNIEnv* env, jobject obj, jlong handle, jobject params);
    JNIEXPORT void JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeReleaseFeature(JNIEnv* env, jobject obj, jlong handle);
    JNIEXPORT void JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeShutdown(JNIEnv* env, jobject obj, jlong handle);
    JNIEXPORT jobject JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeGetPerformanceStats(JNIEnv* env, jobject obj, jlong handle);
}