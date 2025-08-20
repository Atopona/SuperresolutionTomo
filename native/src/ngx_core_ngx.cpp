#ifdef USE_NGX_SDK

#include "ngx_core.h"
#include <vulkan/vulkan.h>

// NGX SDK headers — expected to be under NGX_SDK_DIR/include
#include <nvsdk_ngx.h>
#include <nvsdk_ngx_helpers.h>
#include <nvsdk_ngx_vk.h>

#include <cassert>
#include <cstdio>

// Minimal, WIP implementation for route A (Vulkan direct)
// NOTE:
// - This file is compiled only if USE_NGX_SDK=ON and NGX_SDK_DIR is set
// - It expects Vulkan SDK available and proper linkage in CMake
// - For integration with your engine, wire VkInstance/VkDevice/VkQueue from the engine instead of creating new ones here

static VkInstance     gInstance  = VK_NULL_HANDLE;
static VkPhysicalDevice gPhys    = VK_NULL_HANDLE;
static VkDevice       gDevice    = VK_NULL_HANDLE;
static VkQueue        gQueue     = VK_NULL_HANDLE;
static uint32_t       gQueueFamily = 0;
static NVSDK_NGX_Parameter* gParams = nullptr;

static bool createMinimalVulkan();
static void destroyMinimalVulkan();

bool ngx_core_init() {
    if (!createMinimalVulkan()) return false;

    NVSDK_NGX_Result r = NVSDK_NGX_VULKAN_Init(NVSDK_NGX_Version_API, nullptr, gInstance, gDevice);
    if (NVSDK_NGX_FAILED(r)) {
        destroyMinimalVulkan();
        return false;
    }
    r = NVSDK_NGX_VULKAN_GetParameters(gDevice, &gParams);
    if (NVSDK_NGX_FAILED(r) || !gParams) {
        NVSDK_NGX_VULKAN_Shutdown();
        destroyMinimalVulkan();
        return false;
    }
    return true;
}

void* ngx_core_create_feature(int renderW, int renderH, int outputW, int outputH,
                              int qualityPreset, bool autoExposure, float sharpness) {
    if (!gParams) return nullptr;

    // Map preset from Java enum ordinal to NGX
    NVSDK_NGX_PerfQuality_Value pq = NVSDK_NGX_PerfQuality_Value_MaxQuality;
    switch (qualityPreset) {
        case 0: pq = NVSDK_NGX_PerfQuality_Value_MaxPerf; break;
        case 1: pq = NVSDK_NGX_PerfQuality_Value_Balanced; break;
        case 2: pq = NVSDK_NGX_PerfQuality_Value_MaxQuality; break;
        case 3: pq = NVSDK_NGX_PerfQuality_Value_UltraQuality; break;
        case 4: /* DLAA */ pq = NVSDK_NGX_PerfQuality_Value_DLAA; break;
        default: break;
    }

    NVSDK_NGX_Handle* feature = nullptr;

    // Required parameters for DLSS creation
    NVSDK_NGX_Parameter* params = gParams;
    params->Set(NVSDK_NGX_Parameter_PerfQualityValue, pq);
    params->Set(NVSDK_NGX_Parameter_Reset, 1);
    params->Set(NVSDK_NGX_Parameter_Width, outputW);
    params->Set(NVSDK_NGX_Parameter_Height, outputH);
    params->Set(NVSDK_NGX_Parameter_OutWidth, outputW);
    params->Set(NVSDK_NGX_Parameter_OutHeight, outputH);
    params->Set(NVSDK_NGX_Parameter_MV_ScaleX, 1.0f);
    params->Set(NVSDK_NGX_Parameter_MV_ScaleY, 1.0f);
    params->Set(NVSDK_NGX_Parameter_Jitter_OffsetX, 0.0f);
    params->Set(NVSDK_NGX_Parameter_Jitter_OffsetY, 0.0f);
    params->Set(NVSDK_NGX_Parameter_Sharpness, sharpness);
    params->Set(NVSDK_NGX_Parameter_AutoExposure, autoExposure ? 1 : 0);

    NVSDK_NGX_Result r = NVSDK_NGX_VULKAN_CreateFeature(
        gDevice,
        NVSDK_NGX_Feature_SuperSampling,
        &feature,
        params,
        renderW,
        renderH
    );
    if (NVSDK_NGX_FAILED(r)) {
        return nullptr;
    }
    return feature;
}

bool ngx_core_evaluate(void* featurePtr,
                       uint64_t colorTex, uint64_t depthTex, uint64_t motionVecTex,
                       uint64_t exposureTex, uint64_t outputTex) {
    if (!featurePtr || !gParams) return false;

    NVSDK_NGX_Handle* feature = reinterpret_cast<NVSDK_NGX_Handle*>(featurePtr);

    // NOTE: Route A expects Vulkan images/views here. The incoming handles are engine-side IDs.
    // You must replace these with actual VkImage / VkImageView obtained from your engine.
    // For now, we return false to indicate not wired yet.
    (void)colorTex; (void)depthTex; (void)motionVecTex; (void)exposureTex; (void)outputTex;
    return false;
}

bool ngx_core_release_feature(void* featurePtr) {
    if (!featurePtr) return true;
    NVSDK_NGX_Handle* feature = reinterpret_cast<NVSDK_NGX_Handle*>(featurePtr);
    NVSDK_NGX_VULKAN_DestroyFeature(feature);
    return true;
}

bool ngx_core_shutdown() {
    if (gParams) gParams = nullptr;
    NVSDK_NGX_VULKAN_Shutdown();
    destroyMinimalVulkan();
    return true;
}

// --- minimal Vulkan bring-up (for prototyping; replace with engine integration) ---

static bool createMinimalVulkan() {
    // In production, use the engine's VkInstance/Device/Queue.
    // Here we leave it unimplemented to avoid creating dummy devices that won't see engine resources.
    return false;
}

static void destroyMinimalVulkan() {
    // No-op for now
}

#endif // USE_NGX_SDK

