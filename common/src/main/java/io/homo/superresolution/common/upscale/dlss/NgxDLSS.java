package io.homo.superresolution.common.upscale.dlss;

import io.homo.superresolution.api.AbstractAlgorithm;
import io.homo.superresolution.common.config.SuperResolutionConfig;
import io.homo.superresolution.common.minecraft.MinecraftRenderHandle;
import io.homo.superresolution.common.upscale.DispatchResource;
import io.homo.superresolution.core.RenderSystems;
import io.homo.superresolution.core.graphics.impl.framebuffer.FrameBufferAttachmentType;
import io.homo.superresolution.core.graphics.impl.framebuffer.IFrameBuffer;
import io.homo.superresolution.core.graphics.impl.texture.*;
import io.homo.superresolution.core.graphics.opengl.framebuffer.GlFrameBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NVIDIA DLSS (NGX) integration scaffolding.
 * Now wires the JNI stubs to allow runtime availability gating.
 */
public class NgxDLSS extends AbstractAlgorithm {
    private static final Logger LOGGER = LoggerFactory.getLogger("SuperResolution-DLSS");

    private long featureHandle = 0L;
    private GlFrameBuffer outputFbo;
    private ITexture outputTex;

    private void recreateOutputResources(int outW, int outH) {
        if (outputFbo != null) outputFbo.destroy();
        if (outputTex != null) outputTex.destroy();
        outputTex = RenderSystems.current().device().createTexture(
                TextureDescription.create()
                        .type(TextureType.Texture2D)
                        .width(outW)
                        .height(outH)
                        .format(TextureFormat.RGBA8)
                        .usages(TextureUsages.create().sampler().storage().attachmentColor())
                        .label("DLSS_Output")
                        .build()
        );
        outputFbo = GlFrameBuffer.create(outputTex, null, outW, outH);
        outputFbo.label("DLSS_OutputFbo");
    }

    @Override
    public void init() {
        if (!NgxRuntime.isAvailable()) {
            throw new IllegalStateException("NGX runtime not available on this system");
        }
        if (!NgxNative.initRuntime()) {
            throw new IllegalStateException("Failed to init NGX native runtime");
        }
        int renderW = MinecraftRenderHandle.getRenderWidth();
        int renderH = MinecraftRenderHandle.getRenderHeight();
        int outW = MinecraftRenderHandle.getScreenWidth();
        int outH = MinecraftRenderHandle.getScreenHeight();

        recreateOutputResources(outW, outH);

        int preset = SuperResolutionConfig.DLSS_QUALITY_MODE.get().ordinal();
        boolean autoExp = SuperResolutionConfig.DLSS_AUTO_EXPOSURE.get();
        float sharp = SuperResolutionConfig.DLSS_SHARPNESS.get();

        featureHandle = NgxNative.createFeature(renderW, renderH, outW, outH, preset, autoExp, sharp);
        if (featureHandle == 0L) {
            throw new IllegalStateException("NGX createFeature returned 0");
        }
        LOGGER.info("DLSS (NGX) feature created: handle={}", featureHandle);
    }

    @Override
    public boolean dispatch(DispatchResource dispatchResource) {
        super.dispatch(dispatchResource);
        if (featureHandle == 0L) return false;
        var res = dispatchResource.resources();
        ITexture color = res.colorTexture();
        ITexture depth = res.depthTexture();
        ITexture motion = res.motionVectorsTexture();
        long exposure = 0L; // optional
        long output = outputTex == null ? 0L : outputTex.handle();
        boolean ok = NgxNative.evaluate(featureHandle,
                color == null ? 0L : color.handle(),
                depth == null ? 0L : depth.handle(),
                motion == null ? 0L : motion.handle(),
                exposure,
                output);
        return ok;
    }

    @Override
    public void destroy() {
        if (featureHandle != 0L) {
            NgxNative.releaseFeature(featureHandle);
            featureHandle = 0L;
        }
        NgxNative.shutdown();
        if (outputTex != null) outputTex.destroy();
        if (outputFbo != null) outputFbo.destroy();
        outputTex = null;
        outputFbo = null;
    }

    @Override
    public void resize(int width, int height) {
        // Recreate output resources and (later) the NGX feature with new size
        recreateOutputResources(width, height);
    }

    @Override
    public IFrameBuffer getOutputFrameBuffer() {
        return outputFbo;
    }

    @Override
    public int getOutputTextureId() {
        return outputFbo == null ? 0 : outputFbo.getTextureId(FrameBufferAttachmentType.Color);
    }
}

