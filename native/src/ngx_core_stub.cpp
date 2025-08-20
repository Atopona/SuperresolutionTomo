#include "ngx_core.h"

bool ngx_core_init() { return true; }

void* ngx_core_create_feature(int renderW, int renderH, int outputW, int outputH,
                              int qualityPreset, bool autoExposure, float sharpness) {
    (void)renderW; (void)renderH; (void)outputW; (void)outputH;
    (void)qualityPreset; (void)autoExposure; (void)sharpness;
    return (void*)0x1;
}

bool ngx_core_evaluate(void* feature,
                       uint64_t colorTex, uint64_t depthTex, uint64_t motionVecTex,
                       uint64_t exposureTex, uint64_t outputTex) {
    (void)feature; (void)colorTex; (void)depthTex; (void)motionVecTex; (void)exposureTex; (void)outputTex;
    return true;
}

bool ngx_core_release_feature(void* feature) { (void)feature; return true; }

bool ngx_core_shutdown() { return true; }

