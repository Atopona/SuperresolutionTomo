#include "sr/sr_api.h"
#include "sr/dlss/dlss.h"

struct SRDlssPrivateData {
    int dummy;
};

extern "C" {

SR_API SRReturnCode srDlssCreateUpscaleContext(SRUpscaleContext* context, const SRCreateUpscaleContextDesc* desc)
{
    // Skeleton: mark as not implemented but succeed creation for probing
    context->desc = *const_cast<SRCreateUpscaleContextDesc*>(desc);
    context->userContext = new SRDlssPrivateData{0};
    return (SRReturnCode)SR_RETURN_CODE_OK;
}

SR_API SRReturnCode srDlssDestroyUpscaleContext(SRUpscaleContext* context)
{
    if (!context) return (SRReturnCode)SR_RETURN_CODE_ERROR;
    delete reinterpret_cast<SRDlssPrivateData*>(context->userContext);
    context->userContext = nullptr;
    return (SRReturnCode)SR_RETURN_CODE_OK;
}

SR_API SRReturnCode srDlssQueryUpscale(SRUpscaleContextQueryResult* result, SRUpscaleContext* context, SRUpscaleContextQueryType queryType)
{
    switch (queryType)
    {
        case SR_UPSCALE_CONTEXT_QUERY_VERSION_INFO:
        {
            auto* ver = reinterpret_cast<SRUpscaleContextQueryVersionInfoResult*>(result->data);
            if (ver) {
                ver->versionId = SR_MAKE_VERSION(0, 1, 0);
                ver->versionNumber = SR_MAKE_VERSION(0, 1, 0);
                ver->versionName = const_cast<char*>("DLSS Skeleton");
            }
            break;
        }
        case SR_UPSCALE_CONTEXT_QUERY_GPU_MEMORY_INFO:
        default:
            return (SRReturnCode)SR_RETURN_CODE_ERROR;
    }
    return (SRReturnCode)SR_RETURN_CODE_OK;
}

SR_API SRReturnCode srDlssDispatchUpscale(SRUpscaleContext* context, const SRDispatchUpscaleDesc* desc)
{
    // Skeleton: no-op
    return (SRReturnCode)SR_RETURN_CODE_ERROR;
}

SR_API SRUpscaleContextCallbacks srGetDlssUpscaleCallbacks()
{
    static SRUpscaleContextCallbacks callbacks = {
        .pCreate = (SRCreateFunc)srDlssCreateUpscaleContext,
        .pDestroy = (SRDestroyFunc)srDlssDestroyUpscaleContext,
        .pQuery = (SRQueryFunc)srDlssQueryUpscale,
        .pDispatchUpscale = (SRDispatchUpscaleFunc)srDlssDispatchUpscale,
    };
    return callbacks;
}

}

