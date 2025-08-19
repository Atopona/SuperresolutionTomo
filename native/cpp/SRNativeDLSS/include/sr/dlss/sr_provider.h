#pragma once
#include "sr/sr_api.h"
#include "sr/sr_modules.h"
#include "sr/dlss/dlss.h"

static SRUpscaleProvider g_dlssProviders[1];
static bool g_dlssInitialized = false;

static void ensureDlssInitialized()
{
    if (!g_dlssInitialized)
    {
        g_dlssProviders[0].providerId = SR_MODULES_DLSS_ID;
        g_dlssProviders[0].callbacks = srGetDlssUpscaleCallbacks();
        g_dlssInitialized = true;
    }
}

#ifdef __cplusplus
extern "C" {
#endif
    SR_API SRReturnCode srGetDlssUpscaleProviders(SRUpscaleProvider* outProvider)
    {
        ensureDlssInitialized();
        outProvider[0] = g_dlssProviders[0];
        return (SRReturnCode)SR_RETURN_CODE_OK;
    }

    SR_API SRReturnCode srGetDlssUpscaleProvidersCount(uint32_t* outCount)
    {
        ensureDlssInitialized();
        *outCount = 1;
        return (SRReturnCode)SR_RETURN_CODE_OK;
    }
#ifdef __cplusplus
}
#endif

