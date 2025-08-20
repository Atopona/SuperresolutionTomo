#include <jni.h>
#include <cstdint>
#include "ngx_core.h"

extern "C" {

JNIEXPORT jboolean JNICALL Java_io_homo_superresolution_common_upscale_dlss_NgxNative_nInitRuntime
  (JNIEnv*, jclass) {
    return ngx_core_init() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_io_homo_superresolution_common_upscale_dlss_NgxNative_nShutdown
  (JNIEnv*, jclass) {
    return ngx_core_shutdown() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_io_homo_superresolution_common_upscale_dlss_NgxNative_nCreateFeature
  (JNIEnv*, jclass, jint renderW, jint renderH, jint outputW, jint outputH,
   jint qualityPreset, jboolean autoExposure, jfloat sharpness) {
    void* f = ngx_core_create_feature(renderW, renderH, outputW, outputH, qualityPreset, autoExposure, sharpness);
    return (jlong)(uintptr_t)f;
}

JNIEXPORT jboolean JNICALL Java_io_homo_superresolution_common_upscale_dlss_NgxNative_nReleaseFeature
  (JNIEnv*, jclass, jlong featureHandle) {
    return ngx_core_release_feature((void*)(uintptr_t)featureHandle) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_io_homo_superresolution_common_upscale_dlss_NgxNative_nEvaluate
  (JNIEnv*, jclass, jlong featureHandle,
   jlong colorTex, jlong depthTex, jlong motionVecTex,
   jlong exposureTex, jlong outputTex) {
    return ngx_core_evaluate((void*)(uintptr_t)featureHandle,
                             (uint64_t)colorTex, (uint64_t)depthTex, (uint64_t)motionVecTex,
                             (uint64_t)exposureTex, (uint64_t)outputTex) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

