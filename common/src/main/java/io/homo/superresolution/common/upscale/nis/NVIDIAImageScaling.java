package io.homo.superresolution.common.upscale.nis;

import io.homo.superresolution.api.AbstractAlgorithm;
import io.homo.superresolution.common.config.SuperResolutionConfig;
import io.homo.superresolution.common.minecraft.MinecraftRenderHandle;
import io.homo.superresolution.common.upscale.DispatchResource;
import io.homo.superresolution.core.RenderSystems;
import io.homo.superresolution.core.graphics.impl.buffer.*;
import io.homo.superresolution.core.graphics.impl.framebuffer.IFrameBuffer;
import io.homo.superresolution.core.graphics.impl.pipeline.Pipeline;
import io.homo.superresolution.core.graphics.impl.pipeline.PipelineJobBuilders;
import io.homo.superresolution.core.graphics.impl.pipeline.PipelineJobResource;
import io.homo.superresolution.core.graphics.impl.pipeline.PipelineResourceAccess;
import io.homo.superresolution.core.graphics.impl.shader.ShaderDescription;
import io.homo.superresolution.core.graphics.impl.shader.ShaderSource;
import io.homo.superresolution.core.graphics.impl.shader.ShaderType;
import io.homo.superresolution.core.graphics.impl.shader.uniform.ShaderUniformAccess;
import io.homo.superresolution.core.graphics.impl.texture.*;
import io.homo.superresolution.core.graphics.opengl.framebuffer.GlFrameBuffer;
import io.homo.superresolution.core.math.Vector3i;
import org.lwjgl.opengl.GL43;
import org.lwjgl.stb.STBImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Optional;

import static org.lwjgl.opengl.GL43.GL_RGBA;
import static org.lwjgl.opengl.GL43.GL_UNSIGNED_BYTE;

public class NVIDIAImageScaling extends AbstractAlgorithm {
    private NVIDIAImageScalingConfig config;

    private ITexture output;
    private GlFrameBuffer outputFbo;

    private ITexture coefScaler;
    private ITexture coefUSM;

    private IBuffer ubo;

    private Pipeline pipeline;
    private ITexture srcInputColor;

    @Override
    public void init() {
        this.config = new NVIDIAImageScalingConfig();

        // Output texture & FBO
        this.output = RenderSystems.current().device().createTexture(
                TextureDescription.create()
                        .type(TextureType.Texture2D)
                        .width(MinecraftRenderHandle.getScreenWidth())
                        .height(MinecraftRenderHandle.getScreenHeight())
                        .format(TextureFormat.RGBA8)
                        .usages(TextureUsages.create().sampler().storage())
                        .label("NIS_Output")
                        .build()
        );
        this.outputFbo = GlFrameBuffer.create(output, null,
                MinecraftRenderHandle.getScreenWidth(), MinecraftRenderHandle.getScreenHeight());
        this.outputFbo.label("NIS_OutputFbo");

        // Coef textures
        this.coefScaler = createCoefTexture("/assets/super_resolution/textures/coef_scaler.png",
                NVIDIAImageScalingConst.kFilterSize / 4,
                NVIDIAImageScalingConst.kPhaseCount);
        this.coefUSM = createCoefTexture("/assets/super_resolution/textures/coef_usm.png",
                NVIDIAImageScalingConst.kFilterSize / 4,
                NVIDIAImageScalingConst.kPhaseCount);

        // UBO
        this.ubo = RenderSystems.current().device().createBuffer(
                BufferDescription.create().size(NVIDIAImageScalingConst.kPhaseCount > 0 ? 256 : 256).usage(BufferUsage.UBO).build()
        );
        this.ubo.setBufferData(new DynamicBufferData(256));

        // Shader
        var program = RenderSystems.current().device().createShaderProgram(
                ShaderDescription.compute(ShaderSource.file(ShaderType.COMPUTE, "/shader/nis/nis_scaler.comp.glsl"))
                        .name("NIS_Scaler")
                        .uniformBuffer("const_buffer", 0, 256)
                        .uniformSamplerTexture("in_texture", 2)
                        .uniformStorageTexture("out_texture", ShaderUniformAccess.Write, 3)
                        .uniformSamplerTexture("coef_scaler", 4)
                        .uniformSamplerTexture("coef_usm", 5)
                        .build()
        );
        program.compile();

        // Pipeline
        this.pipeline = new Pipeline();
        this.pipeline.job("nis_scaler",
                PipelineJobBuilders.compute(program)
                        .resource("in_texture",
                                PipelineJobResource.SamplerTexture.create(() -> Optional.ofNullable(getResources() == null ? null : getResources().colorTexture()))
                        )
                        .resource("out_texture",
                                PipelineJobResource.StorageTexture.create(output, PipelineResourceAccess.Write)
                        )
                        .resource("const_buffer",
                                PipelineJobResource.UniformBuffer.create(ubo)
                        )
                        .resource("coef_scaler",
                                PipelineJobResource.SamplerTexture.create(coefScaler)
                        )
                        .resource("coef_usm",
                                PipelineJobResource.SamplerTexture.create(coefUSM)
                        )
                        .workGroupSupplier(this::getWorkGroupSize)
                        .build()
        );
    }

    private ITexture createCoefTexture(String resourcePath, int width, int height) {
        ITexture tex = RenderSystems.current().device().createTexture(
                TextureDescription.create()
                        .type(TextureType.Texture2D)
                        .width(width)
                        .height(height)
                        .format(TextureFormat.RGBA8)
                        .usages(TextureUsages.create().sampler().storage())
                        .label("NIS_Coef_" + (resourcePath.contains("usm") ? "USM" : "Scaler"))
                        .build()
        );
        ByteBuffer pngBytes;
        try (InputStream is = NVIDIAImageScaling.class.getResourceAsStream(resourcePath)) {
            if (is == null) return tex;
            byte[] bytes = is.readAllBytes();
            pngBytes = ByteBuffer.allocateDirect(bytes.length);
            pngBytes.put(bytes).flip();
        } catch (IOException e) {
            return tex;
        }
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer ch = stack.mallocInt(1);
            STBImage.stbi_set_flip_vertically_on_load(false);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(pngBytes, w, h, ch, 4);
            if (pixels != null) {
                ((io.homo.superresolution.core.graphics.opengl.texture.GlTexture2D) tex).uploadData(0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels, 1);
                STBImage.stbi_image_free(pixels);
            }
        }
        return tex;
    }

    private Vector3i getWorkGroupSize() {
        int workRegionDimX = 32;
        int workRegionDimY = 24;
        int dispatchX = (MinecraftRenderHandle.getScreenWidth() + (workRegionDimX - 1)) / workRegionDimX;
        int dispatchY = (MinecraftRenderHandle.getScreenHeight() + (workRegionDimY - 1)) / workRegionDimY;
        return new Vector3i(dispatchX, dispatchY, 1);
    }

    @Override
    public boolean dispatch(DispatchResource dispatchResource) {
        super.dispatch(dispatchResource);

        // Prepare config
        config.NVScalerUpdateConfig(
                SuperResolutionConfig.getSharpness(),
                0, 0,
                dispatchResource.renderWidth(), dispatchResource.renderHeight(),
                dispatchResource.renderWidth(), dispatchResource.renderHeight(),
                0, 0,
                dispatchResource.screenWidth(), dispatchResource.screenHeight(),
                dispatchResource.screenWidth(), dispatchResource.screenHeight(),
                io.homo.superresolution.common.upscale.nis.enums.NISHDRMode.None
        );
        config.updateData(dispatchResource);
        ((DynamicBufferData) ubo.data()).update(config.container());
        ubo.upload();

        RenderSystems.current().device().commendEncoder().begin();
        pipeline.execute(RenderSystems.current().device().commendEncoder().getCommandBuffer());
        RenderSystems.current().device().submitCommandBuffer(
                RenderSystems.current().device().commendEncoder().end()
        );
        return true;
    }

    @Override
    public void destroy() {
        if (output != null) output.destroy();
        if (outputFbo != null) outputFbo.destroy();
        if (coefScaler != null) coefScaler.destroy();
        if (coefUSM != null) coefUSM.destroy();
        if (ubo != null) ubo.destroy();
        if (pipeline != null) pipeline.destroy();
    }

    @Override
    public void resize(int width, int height) {
        output.resize(MinecraftRenderHandle.getScreenWidth(), MinecraftRenderHandle.getScreenHeight());
        outputFbo.resizeFrameBuffer(MinecraftRenderHandle.getScreenWidth(), MinecraftRenderHandle.getScreenHeight());
    }

    @Override
    public IFrameBuffer getOutputFrameBuffer() {
        return outputFbo;
    }
}
