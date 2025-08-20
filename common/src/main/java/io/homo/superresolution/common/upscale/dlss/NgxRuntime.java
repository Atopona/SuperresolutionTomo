package io.homo.superresolution.common.upscale.dlss;

import io.homo.superresolution.common.platform.Arch;
import io.homo.superresolution.common.platform.OS;
import io.homo.superresolution.common.platform.OSType;
import io.homo.superresolution.core.RenderSystems;
import io.homo.superresolution.core.graphics.GraphicsCapabilities;
import io.homo.superresolution.core.graphics.GpuVendor;

/**
 * Minimal NGX runtime availability gate. This does NOT initialize NGX.
 * Later we will replace it with actual NGX runtime loading and feature creation.
 */
public final class NgxRuntime {
    private NgxRuntime() {}

    public static boolean isAvailable() {
        // Basic checks: Windows + x86_64 + Vulkan + NVIDIA GPU
        OS os = new OS();
        boolean osOk = os.arch == Arch.X86_64 && os.type == OSType.WINDOWS;
        boolean vkOk = RenderSystems.isSupportVulkan();
        boolean vendorOk = GraphicsCapabilities.detectGpuVendor() == GpuVendor.NVIDIA;
        // also require native bridge present (so UI strictly reflects runtime ready)
        boolean nativeBridge = NgxNative.isRuntimePresent();
        return osOk && vkOk && vendorOk && nativeBridge;
    }
}

