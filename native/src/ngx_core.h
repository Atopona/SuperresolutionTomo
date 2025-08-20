#pragma once
#include <cstdint>
#include <stdbool.h>

// Core interface for NGX integration. Implemented by either stub or NGX backend.

bool ngx_core_init();
void* ngx_core_create_feature(int renderW, int renderH, int outputW, int outputH,
                              int qualityPreset, bool autoExposure, float sharpness);
bool ngx_core_evaluate(void* feature,
                       uint64_t colorTex, uint64_t depthTex, uint64_t motionVecTex,
                       uint64_t exposureTex, uint64_t outputTex);
bool ngx_core_release_feature(void* feature);
bool ngx_core_shutdown();

