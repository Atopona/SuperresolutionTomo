package io.homo.superresolution.common.upscale.dlss;

import io.homo.superresolution.api.utils.Requirement;
import io.homo.superresolution.common.platform.Arch;
import io.homo.superresolution.common.platform.OS;
import io.homo.superresolution.common.platform.OSType;
import io.homo.superresolution.core.RenderSystems;

public final class NgxRequirement {
    private NgxRequirement() {}

    public static Requirement build() {
        return Requirement.nothing()
                .addSupportedOS(new OS(Arch.X86_64, OSType.WINDOWS))
                .requireVulkan(true)
                // Gate with consolidated runtime availability check
                .isTrue(NgxRuntime::isAvailable)
                .isTrue(RenderSystems::isSupportVulkan);
    }
}

