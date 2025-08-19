#include "dlss_wrapper.h"
#include <iostream>
#include <chrono>
#include <vector>
#include <algorithm>
#include <thread>
#include <numeric>

// 如果有DLSS SDK，包含相关头文件
#ifdef DLSS_AVAILABLE
#include "nvsdk_ngx.h"
#include "nvsdk_ngx_helpers.h"
#endif

// DLSS包装器实现类
class DLSSWrapper::Impl {
public:
    Impl() : initialized(false), featureCreated(false) {
        stats = {};
        stats.lastUpdateTime = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();
    }
    
    ~Impl() {
        if (featureCreated) {
            ReleaseFeature();
        }
        if (initialized) {
            Shutdown();
        }
    }
    
    bool Initialize() {
        if (initialized) return true;
        
        try {
#ifdef DLSS_AVAILABLE
            // 初始化NGX
            NVSDK_NGX_Result result = NVSDK_NGX_Init(
                0, // Application ID
                L".", // Log directory
                NVSDK_NGX_Version_API
            );
            
            if (NVSDK_NGX_SUCCEED(result)) {
                initialized = true;
                std::cout << "DLSS初始化成功" << std::endl;
                return true;
            } else {
                std::cerr << "DLSS初始化失败: " << result << std::endl;
                return false;
            }
#else
            // 模拟模式 - 用于测试
            std::cout << "DLSS模拟模式初始化" << std::endl;
            initialized = true;
            return true;
#endif
        } catch (const std::exception& e) {
            std::cerr << "DLSS初始化异常: " << e.what() << std::endl;
            return false;
        }
    }
    
    bool CreateFeature(const DLSSCreateParams& params) {
        if (!initialized) return false;
        if (featureCreated) ReleaseFeature();
        
        try {
#ifdef DLSS_AVAILABLE
            // 创建DLSS特性参数
            NVSDK_NGX_DLSS_Create_Params dlssCreateParams = {};
            dlssCreateParams.Feature.InWidth = params.maxRenderWidth;
            dlssCreateParams.Feature.InHeight = params.maxRenderHeight;
            dlssCreateParams.Feature.InTargetWidth = params.maxDisplayWidth;
            dlssCreateParams.Feature.InTargetHeight = params.maxDisplayHeight;
            dlssCreateParams.Feature.InPerfQualityValue = NVSDK_NGX_PerfQuality_Value_Balanced;
            dlssCreateParams.InFeatureCreateFlags = params.featureFlags;
            
            // 创建DLSS特性
            NVSDK_NGX_Result result = NGX_DLSS_CREATE_EXT(
                &dlssCreateParams,
                &dlssFeature
            );
            
            if (NVSDK_NGX_SUCCEED(result)) {
                featureCreated = true;
                std::cout << "DLSS特性创建成功" << std::endl;
                return true;
            } else {
                std::cerr << "DLSS特性创建失败: " << result << std::endl;
                return false;
            }
#else
            // 模拟模式
            std::cout << "DLSS特性模拟创建成功" << std::endl;
            featureCreated = true;
            return true;
#endif
        } catch (const std::exception& e) {
            std::cerr << "DLSS特性创建异常: " << e.what() << std::endl;
            return false;
        }
    }
    
    bool Evaluate(const DLSSEvaluateParams& params) {
        if (!initialized || !featureCreated) return false;
        
        auto startTime = std::chrono::high_resolution_clock::now();
        
        try {
#ifdef DLSS_AVAILABLE
            // 准备DLSS评估参数
            NVSDK_NGX_DLSS_Eval_Params evalParams = {};
            evalParams.Feature.pInColor = reinterpret_cast<void*>(params.colorTextureId);
            evalParams.Feature.pInOutput = reinterpret_cast<void*>(params.outputTextureId);
            evalParams.Feature.InSharpness = 0.0f; // 可以从配置获取
            evalParams.pInDepth = reinterpret_cast<void*>(params.depthTextureId);
            evalParams.pInMotionVectors = reinterpret_cast<void*>(params.motionVectorTextureId);
            evalParams.InJitterOffsetX = params.jitterOffsetX;
            evalParams.InJitterOffsetY = params.jitterOffsetY;
            evalParams.InRenderSubrectDimensions.Width = params.renderWidth;
            evalParams.InRenderSubrectDimensions.Height = params.renderHeight;
            evalParams.InReset = params.reset;
            evalParams.InMVScaleX = params.motionVectorScaleX;
            evalParams.InMVScaleY = params.motionVectorScaleY;
            
            // 执行DLSS推理
            NVSDK_NGX_Result result = NGX_DLSS_EVALUATE_EXT(
                dlssFeature,
                &evalParams
            );
            
            auto endTime = std::chrono::high_resolution_clock::now();
            float inferenceTime = std::chrono::duration<float, std::milli>(endTime - startTime).count();
            
            UpdatePerformanceStats(inferenceTime, NVSDK_NGX_SUCCEED(result));
            
            if (NVSDK_NGX_SUCCEED(result)) {
                return true;
            } else {
                std::cerr << "DLSS推理失败: " << result << std::endl;
                return false;
            }
#else
            // 模拟模式 - 简单的纹理复制
            auto endTime = std::chrono::high_resolution_clock::now();
            float inferenceTime = std::chrono::duration<float, std::milli>(endTime - startTime).count();
            
            // 模拟一些处理时间
            std::this_thread::sleep_for(std::chrono::microseconds(500));
            
            UpdatePerformanceStats(inferenceTime + 0.5f, true);
            return true;
#endif
        } catch (const std::exception& e) {
            auto endTime = std::chrono::high_resolution_clock::now();
            float inferenceTime = std::chrono::duration<float, std::milli>(endTime - startTime).count();
            UpdatePerformanceStats(inferenceTime, false);
            
            std::cerr << "DLSS推理异常: " << e.what() << std::endl;
            return false;
        }
    }
    
    void ReleaseFeature() {
        if (!featureCreated) return;
        
        try {
#ifdef DLSS_AVAILABLE
            if (dlssFeature) {
                NVSDK_NGX_DLSS_ReleaseFeature(dlssFeature);
                dlssFeature = nullptr;
            }
#endif
            featureCreated = false;
            std::cout << "DLSS特性已释放" << std::endl;
        } catch (const std::exception& e) {
            std::cerr << "DLSS特性释放异常: " << e.what() << std::endl;
        }
    }
    
    void Shutdown() {
        if (!initialized) return;
        
        try {
            if (featureCreated) {
                ReleaseFeature();
            }
            
#ifdef DLSS_AVAILABLE
            NVSDK_NGX_Shutdown();
#endif
            initialized = false;
            std::cout << "DLSS已关闭" << std::endl;
        } catch (const std::exception& e) {
            std::cerr << "DLSS关闭异常: " << e.what() << std::endl;
        }
    }
    
    DLSSPerformanceStats GetPerformanceStats() {
        return stats;
    }
    
    static bool IsDLSSSupported() {
#ifdef DLSS_AVAILABLE
        // 检查DLSS支持
        NVSDK_NGX_FeatureCommonInfo info = {};
        NVSDK_NGX_Result result = NVSDK_NGX_GetFeatureRequirements(
            NVSDK_NGX_Feature_SuperSampling,
            &info
        );
        
        return NVSDK_NGX_SUCCEED(result) && info.FeatureSupported;
#else
        // 模拟模式 - 总是返回支持
        return true;
#endif
    }
    
    static std::string GetDLSSVersion() {
#ifdef DLSS_AVAILABLE
        return "DLSS 3.7.0"; // 实际应该从NGX SDK获取
#else
        return "DLSS 模拟版本 1.0.0";
#endif
    }
    
private:
    bool initialized;
    bool featureCreated;
    DLSSPerformanceStats stats;
    std::vector<float> recentInferenceTimes;
    
#ifdef DLSS_AVAILABLE
    NVSDK_NGX_Handle* dlssFeature = nullptr;
#endif
    
    void UpdatePerformanceStats(float inferenceTime, bool success) {
        stats.lastInferenceTime = inferenceTime;
        stats.totalInferences++;
        
        if (success) {
            stats.successfulInferences++;
        } else {
            stats.failedInferences++;
        }
        
        // 更新推理时间统计
        recentInferenceTimes.push_back(inferenceTime);
        if (recentInferenceTimes.size() > 100) {
            recentInferenceTimes.erase(recentInferenceTimes.begin());
        }
        
        if (!recentInferenceTimes.empty()) {
            stats.averageInferenceTime = std::accumulate(recentInferenceTimes.begin(), recentInferenceTimes.end(), 0.0f) / recentInferenceTimes.size();
            stats.minInferenceTime = *std::min_element(recentInferenceTimes.begin(), recentInferenceTimes.end());
            stats.maxInferenceTime = *std::max_element(recentInferenceTimes.begin(), recentInferenceTimes.end());
        }
        
        // 计算FPS
        if (inferenceTime > 0) {
            float currentFPS = 1000.0f / inferenceTime;
            stats.averageFPS = (stats.averageFPS * 0.9f) + (currentFPS * 0.1f);
            
            if (stats.minFPS == 0 || currentFPS < stats.minFPS) {
                stats.minFPS = currentFPS;
            }
            if (currentFPS > stats.maxFPS) {
                stats.maxFPS = currentFPS;
            }
        }
        
        stats.lastUpdateTime = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();
    }
};

// DLSSWrapper实现
DLSSWrapper::DLSSWrapper() : pImpl(std::make_unique<Impl>()) {}
DLSSWrapper::~DLSSWrapper() = default;

bool DLSSWrapper::IsDLSSSupported() {
    return Impl::IsDLSSSupported();
}

std::string DLSSWrapper::GetDLSSVersion() {
    return Impl::GetDLSSVersion();
}

bool DLSSWrapper::Initialize() {
    return pImpl->Initialize();
}

bool DLSSWrapper::CreateFeature(const DLSSCreateParams& params) {
    return pImpl->CreateFeature(params);
}

bool DLSSWrapper::Evaluate(const DLSSEvaluateParams& params) {
    return pImpl->Evaluate(params);
}

void DLSSWrapper::ReleaseFeature() {
    pImpl->ReleaseFeature();
}

void DLSSWrapper::Shutdown() {
    pImpl->Shutdown();
}

DLSSPerformanceStats DLSSWrapper::GetPerformanceStats() {
    return pImpl->GetPerformanceStats();
}

// JNI实现
extern "C" {

JNIEXPORT jboolean JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeIsDLSSSupported(JNIEnv* env, jclass clazz) {
    return DLSSWrapper::IsDLSSSupported();
}

JNIEXPORT jstring JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeGetDLSSVersion(JNIEnv* env, jclass clazz) {
    std::string version = DLSSWrapper::GetDLSSVersion();
    return env->NewStringUTF(version.c_str());
}

JNIEXPORT jlong JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeInitialize(JNIEnv* env, jobject obj) {
    try {
        auto wrapper = new DLSSWrapper();
        if (wrapper->Initialize()) {
            return reinterpret_cast<jlong>(wrapper);
        } else {
            delete wrapper;
            return 0;
        }
    } catch (const std::exception& e) {
        std::cerr << "DLSS初始化JNI异常: " << e.what() << std::endl;
        return 0;
    }
}

// 辅助函数：从Java对象提取DLSSCreateParams
DLSSCreateParams extractCreateParams(JNIEnv* env, jobject params) {
    DLSSCreateParams result = {};
    
    jclass clazz = env->GetObjectClass(params);
    
    jfieldID featureFlagsField = env->GetFieldID(clazz, "featureFlags", "I");
    jfieldID maxRenderWidthField = env->GetFieldID(clazz, "maxRenderWidth", "I");
    jfieldID maxRenderHeightField = env->GetFieldID(clazz, "maxRenderHeight", "I");
    jfieldID maxDisplayWidthField = env->GetFieldID(clazz, "maxDisplayWidth", "I");
    jfieldID maxDisplayHeightField = env->GetFieldID(clazz, "maxDisplayHeight", "I");
    jfieldID applicationIdField = env->GetFieldID(clazz, "applicationId", "I");
    
    result.featureFlags = env->GetIntField(params, featureFlagsField);
    result.maxRenderWidth = env->GetIntField(params, maxRenderWidthField);
    result.maxRenderHeight = env->GetIntField(params, maxRenderHeightField);
    result.maxDisplayWidth = env->GetIntField(params, maxDisplayWidthField);
    result.maxDisplayHeight = env->GetIntField(params, maxDisplayHeightField);
    result.applicationId = env->GetIntField(params, applicationIdField);
    
    return result;
}

// 辅助函数：从Java对象提取DLSSEvaluateParams
DLSSEvaluateParams extractEvaluateParams(JNIEnv* env, jobject params) {
    DLSSEvaluateParams result = {};
    
    jclass clazz = env->GetObjectClass(params);
    
    // 获取所有字段ID
    jfieldID colorTextureIdField = env->GetFieldID(clazz, "colorTextureId", "I");
    jfieldID depthTextureIdField = env->GetFieldID(clazz, "depthTextureId", "I");
    jfieldID motionVectorTextureIdField = env->GetFieldID(clazz, "motionVectorTextureId", "I");
    jfieldID outputTextureIdField = env->GetFieldID(clazz, "outputTextureId", "I");
    jfieldID renderWidthField = env->GetFieldID(clazz, "renderWidth", "I");
    jfieldID renderHeightField = env->GetFieldID(clazz, "renderHeight", "I");
    jfieldID displayWidthField = env->GetFieldID(clazz, "displayWidth", "I");
    jfieldID displayHeightField = env->GetFieldID(clazz, "displayHeight", "I");
    jfieldID frameTimeDeltaField = env->GetFieldID(clazz, "frameTimeDelta", "F");
    jfieldID resetField = env->GetFieldID(clazz, "reset", "Z");
    jfieldID jitterOffsetXField = env->GetFieldID(clazz, "jitterOffsetX", "F");
    jfieldID jitterOffsetYField = env->GetFieldID(clazz, "jitterOffsetY", "F");
    jfieldID qualityField = env->GetFieldID(clazz, "quality", "I");
    
    // 提取值
    result.colorTextureId = env->GetIntField(params, colorTextureIdField);
    result.depthTextureId = env->GetIntField(params, depthTextureIdField);
    result.motionVectorTextureId = env->GetIntField(params, motionVectorTextureIdField);
    result.outputTextureId = env->GetIntField(params, outputTextureIdField);
    result.renderWidth = env->GetIntField(params, renderWidthField);
    result.renderHeight = env->GetIntField(params, renderHeightField);
    result.displayWidth = env->GetIntField(params, displayWidthField);
    result.displayHeight = env->GetIntField(params, displayHeightField);
    result.frameTimeDelta = env->GetFloatField(params, frameTimeDeltaField);
    result.reset = env->GetBooleanField(params, resetField);
    result.jitterOffsetX = env->GetFloatField(params, jitterOffsetXField);
    result.jitterOffsetY = env->GetFloatField(params, jitterOffsetYField);
    result.quality = env->GetIntField(params, qualityField);
    
    return result;
}

JNIEXPORT jboolean JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeCreateFeature(JNIEnv* env, jobject obj, jlong handle, jobject params) {
    try {
        auto wrapper = reinterpret_cast<DLSSWrapper*>(handle);
        if (!wrapper) return false;
        
        DLSSCreateParams createParams = extractCreateParams(env, params);
        return wrapper->CreateFeature(createParams);
    } catch (const std::exception& e) {
        std::cerr << "DLSS创建特性JNI异常: " << e.what() << std::endl;
        return false;
    }
}

JNIEXPORT jboolean JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeEvaluate(JNIEnv* env, jobject obj, jlong handle, jobject params) {
    try {
        auto wrapper = reinterpret_cast<DLSSWrapper*>(handle);
        if (!wrapper) return false;
        
        DLSSEvaluateParams evalParams = extractEvaluateParams(env, params);
        return wrapper->Evaluate(evalParams);
    } catch (const std::exception& e) {
        std::cerr << "DLSS推理JNI异常: " << e.what() << std::endl;
        return false;
    }
}

JNIEXPORT void JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeReleaseFeature(JNIEnv* env, jobject obj, jlong handle) {
    try {
        auto wrapper = reinterpret_cast<DLSSWrapper*>(handle);
        if (wrapper) {
            wrapper->ReleaseFeature();
        }
    } catch (const std::exception& e) {
        std::cerr << "DLSS释放特性JNI异常: " << e.what() << std::endl;
    }
}

JNIEXPORT void JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeShutdown(JNIEnv* env, jobject obj, jlong handle) {
    try {
        auto wrapper = reinterpret_cast<DLSSWrapper*>(handle);
        if (wrapper) {
            wrapper->Shutdown();
            delete wrapper;
        }
    } catch (const std::exception& e) {
        std::cerr << "DLSS关闭JNI异常: " << e.what() << std::endl;
    }
}

JNIEXPORT jobject JNICALL Java_io_homo_superresolution_common_upscale_dlss_DLSSNative_nativeGetPerformanceStats(JNIEnv* env, jobject obj, jlong handle) {
    try {
        auto wrapper = reinterpret_cast<DLSSWrapper*>(handle);
        if (!wrapper) return nullptr;
        
        DLSSPerformanceStats stats = wrapper->GetPerformanceStats();
        
        // 创建Java对象
        jclass statsClass = env->FindClass("io/homo/superresolution/common/upscale/dlss/DLSSPerformanceStats");
        jmethodID constructor = env->GetMethodID(statsClass, "<init>", "()V");
        jobject statsObj = env->NewObject(statsClass, constructor);
        
        // 设置字段值
        jfieldID averageInferenceTimeField = env->GetFieldID(statsClass, "averageInferenceTime", "F");
        jfieldID minInferenceTimeField = env->GetFieldID(statsClass, "minInferenceTime", "F");
        jfieldID maxInferenceTimeField = env->GetFieldID(statsClass, "maxInferenceTime", "F");
        jfieldID lastInferenceTimeField = env->GetFieldID(statsClass, "lastInferenceTime", "F");
        jfieldID averageFPSField = env->GetFieldID(statsClass, "averageFPS", "F");
        jfieldID totalInferencesField = env->GetFieldID(statsClass, "totalInferences", "J");
        jfieldID successfulInferencesField = env->GetFieldID(statsClass, "successfulInferences", "J");
        jfieldID failedInferencesField = env->GetFieldID(statsClass, "failedInferences", "J");
        
        env->SetFloatField(statsObj, averageInferenceTimeField, stats.averageInferenceTime);
        env->SetFloatField(statsObj, minInferenceTimeField, stats.minInferenceTime);
        env->SetFloatField(statsObj, maxInferenceTimeField, stats.maxInferenceTime);
        env->SetFloatField(statsObj, lastInferenceTimeField, stats.lastInferenceTime);
        env->SetFloatField(statsObj, averageFPSField, stats.averageFPS);
        env->SetLongField(statsObj, totalInferencesField, stats.totalInferences);
        env->SetLongField(statsObj, successfulInferencesField, stats.successfulInferences);
        env->SetLongField(statsObj, failedInferencesField, stats.failedInferences);
        
        return statsObj;
    } catch (const std::exception& e) {
        std::cerr << "DLSS获取性能统计JNI异常: " << e.what() << std::endl;
        return nullptr;
    }
}

} // extern "C"