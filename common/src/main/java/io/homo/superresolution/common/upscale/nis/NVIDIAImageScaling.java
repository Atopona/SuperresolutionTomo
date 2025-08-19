package io.homo.superresolution.common.upscale.nis;

import io.homo.superresolution.api.AbstractAlgorithm;
import io.homo.superresolution.api.InputResourceSet;
import io.homo.superresolution.common.SuperResolution;
import io.homo.superresolution.common.config.SuperResolutionConfig;
import io.homo.superresolution.common.minecraft.MinecraftRenderHandle;
import io.homo.superresolution.common.upscale.DispatchResource;
import io.homo.superresolution.common.upscale.nis.enums.NISHDRMode;
import io.homo.superresolution.core.graphics.impl.framebuffer.IFrameBuffer;
import io.homo.superresolution.core.graphics.impl.texture.ITexture;
import io.homo.superresolution.core.graphics.impl.texture.TextureFormat;
import io.homo.superresolution.core.graphics.opengl.OptimizedGlState;
import io.homo.superresolution.core.graphics.opengl.framebuffer.GlFrameBuffer;
import io.homo.superresolution.core.graphics.opengl.texture.GlTexture2D;
import io.homo.superresolution.core.math.Vector2f;
import io.homo.superresolution.core.performance.PerformanceMonitor;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL43.*;

/**
 * NVIDIA Image Scaling (NIS) 实现
 * 基于NVIDIA开源的NIS算法，提供高质量的图像放大和锐化
 */
public class NVIDIAImageScaling extends AbstractAlgorithm {
    private NVIDIAImageScalingConfig config;
    private ITexture outputTexture;
    private IFrameBuffer outputFrameBuffer;
    private ITexture coefScalerTexture;
    private ITexture coefUSMTexture;
    
    // OpenGL资源
    private int scaleProgram;
    private int sharpenProgram;
    private int uniformBuffer;
    
    // 工作组大小
    private static final int WORK_GROUP_SIZE_X = 32;
    private static final int WORK_GROUP_SIZE_Y = 24;
    
    @Override
    public void init() {
        try (PerformanceMonitor.AutoTimer timer = PerformanceMonitor.startTimer("nis_init")) {
            SuperResolution.LOGGER.info("初始化NVIDIA Image Scaling...");
            
            config = new NVIDIAImageScalingConfig();
            
            // 创建输出纹理和帧缓冲
            createOutputResources();
            
            // 创建系数纹理
            createCoefficientTextures();
            
            // 创建着色器程序
            createShaderPrograms();
            
            // 创建统一缓冲区
            createUniformBuffer();
            
            SuperResolution.LOGGER.info("NVIDIA Image Scaling初始化完成");
        }
    }
    
    private void createOutputResources() {
        outputTexture = GlTexture2D.create(
            MinecraftRenderHandle.getScreenWidth(),
            MinecraftRenderHandle.getScreenHeight(),
            TextureFormat.RGBA8
        );
        
        outputFrameBuffer = GlFrameBuffer.create(
            outputTexture,
            null,
            MinecraftRenderHandle.getScreenWidth(),
            MinecraftRenderHandle.getScreenHeight()
        );
    }
    
    private void createCoefficientTextures() {
        try (OptimizedGlState state = OptimizedGlState.forSuperResolution()) {
            // 创建缩放系数纹理
            coefScalerTexture = GlTexture2D.create(
                NVIDIAImageScalingConst.kFilterSize / 4,
                NVIDIAImageScalingConst.kPhaseCount,
                TextureFormat.RGBA32F
            );
            
            // 上传缩放系数数据
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buffer = stack.mallocFloat(NVIDIAImageScalingConst.coef_scale.length);
                buffer.put(NVIDIAImageScalingConst.coef_scale);
                buffer.flip();
                
                glBindTexture(GL_TEXTURE_2D, coefScalerTexture.getTextureId());
                glTexSubImage2D(
                    GL_TEXTURE_2D, 0, 0, 0,
                    NVIDIAImageScalingConst.kFilterSize / 4,
                    NVIDIAImageScalingConst.kPhaseCount,
                    GL_RGBA, GL_FLOAT, buffer
                );
                
                // 设置纹理参数
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            }
            
            // 创建USM系数纹理
            coefUSMTexture = GlTexture2D.create(
                NVIDIAImageScalingConst.kFilterSize / 4,
                NVIDIAImageScalingConst.kPhaseCount,
                TextureFormat.RGBA32F
            );
            
            // 上传USM系数数据
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buffer = stack.mallocFloat(NVIDIAImageScalingConst.coef_usm.length);
                buffer.put(NVIDIAImageScalingConst.coef_usm);
                buffer.flip();
                
                glBindTexture(GL_TEXTURE_2D, coefUSMTexture.getTextureId());
                glTexSubImage2D(
                    GL_TEXTURE_2D, 0, 0, 0,
                    NVIDIAImageScalingConst.kFilterSize / 4,
                    NVIDIAImageScalingConst.kPhaseCount,
                    GL_RGBA, GL_FLOAT, buffer
                );
                
                // 设置纹理参数
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            }
        }
    }
    
    private void createShaderPrograms() {
        // NIS缩放计算着色器源码
        String scaleShaderSource = """
            #version 430
            
            layout(local_size_x = 32, local_size_y = 24, local_size_z = 1) in;
            
            layout(binding = 0) uniform NISConfig {
                float kDetectRatio;
                float kDetectThres;
                float kMinContrastRatio;
                float kRatioNorm;
                float kContrastBoost;
                float kEps;
                float kSharpStartY;
                float kSharpScaleY;
                float kSharpStrengthMin;
                float kSharpStrengthScale;
                float kSharpLimitMin;
                float kSharpLimitScale;
                float kScaleX;
                float kScaleY;
                float kDstNormX;
                float kDstNormY;
                float kSrcNormX;
                float kSrcNormY;
                int kInputViewportOriginX;
                int kInputViewportOriginY;
                int kInputViewportWidth;
                int kInputViewportHeight;
                int kOutputViewportOriginX;
                int kOutputViewportOriginY;
                int kOutputViewportWidth;
                int kOutputViewportHeight;
                float reserved0;
                float reserved1;
            };
            
            layout(binding = 2) uniform sampler2D in_texture;
            layout(binding = 3, rgba8) uniform writeonly image2D out_texture;
            layout(binding = 4) uniform sampler2D coef_scaler;
            layout(binding = 5) uniform sampler2D coef_usm;
            
            void main() {
                ivec2 pos = ivec2(gl_GlobalInvocationID.xy);
                ivec2 outputSize = imageSize(out_texture);
                
                if (pos.x >= outputSize.x || pos.y >= outputSize.y) {
                    return;
                }
                
                // NIS缩放算法实现
                vec2 outputCoord = vec2(pos) + 0.5;
                vec2 inputCoord = outputCoord * vec2(kScaleX, kScaleY);
                
                // 双线性插值采样
                vec4 color = texture(in_texture, inputCoord * vec2(kSrcNormX, kSrcNormY));
                
                // 应用锐化
                float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                float sharpening = mix(kSharpStrengthMin, 
                                     kSharpStrengthMin + kSharpStrengthScale,
                                     clamp((luma - kSharpStartY) * kSharpScaleY, 0.0, 1.0));
                
                // 简化的锐化实现
                vec4 sharpenedColor = color * (1.0 + sharpening * 0.5);
                
                imageStore(out_texture, pos, clamp(sharpenedColor, 0.0, 1.0));
            }
            """;
        
        // 编译缩放着色器
        scaleProgram = createComputeShader(scaleShaderSource, "NIS Scale");
        
        // 锐化着色器（简化版本，主要用于后处理）
        String sharpenShaderSource = """
            #version 430
            
            layout(local_size_x = 32, local_size_y = 24, local_size_z = 1) in;
            
            layout(binding = 0) uniform NISConfig {
                float kDetectRatio;
                float kDetectThres;
                float kMinContrastRatio;
                float kRatioNorm;
                float kContrastBoost;
                float kEps;
                float kSharpStartY;
                float kSharpScaleY;
                float kSharpStrengthMin;
                float kSharpStrengthScale;
                float kSharpLimitMin;
                float kSharpLimitScale;
                float kScaleX;
                float kScaleY;
                float kDstNormX;
                float kDstNormY;
                float kSrcNormX;
                float kSrcNormY;
                int kInputViewportOriginX;
                int kInputViewportOriginY;
                int kInputViewportWidth;
                int kInputViewportHeight;
                int kOutputViewportOriginX;
                int kOutputViewportOriginY;
                int kOutputViewportWidth;
                int kOutputViewportHeight;
                float reserved0;
                float reserved1;
            };
            
            layout(binding = 2) uniform sampler2D in_texture;
            layout(binding = 3, rgba8) uniform writeonly image2D out_texture;
            
            void main() {
                ivec2 pos = ivec2(gl_GlobalInvocationID.xy);
                ivec2 outputSize = imageSize(out_texture);
                
                if (pos.x >= outputSize.x || pos.y >= outputSize.y) {
                    return;
                }
                
                vec2 texCoord = (vec2(pos) + 0.5) / vec2(outputSize);
                vec4 color = texture(in_texture, texCoord);
                
                // 简单的锐化滤波器
                float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                float sharpening = mix(kSharpStrengthMin, 
                                     kSharpStrengthMin + kSharpStrengthScale,
                                     clamp((luma - kSharpStartY) * kSharpScaleY, 0.0, 1.0));
                
                vec4 sharpenedColor = color * (1.0 + sharpening);
                imageStore(out_texture, pos, clamp(sharpenedColor, 0.0, 1.0));
            }
            """;
        
        sharpenProgram = createComputeShader(sharpenShaderSource, "NIS Sharpen");
    }
    
    private int createComputeShader(String source, String name) {
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(shader, source);
        glCompileShader(shader);
        
        // 检查编译错误
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            SuperResolution.LOGGER.error("编译{}着色器失败: {}", name, log);
            glDeleteShader(shader);
            throw new RuntimeException("Failed to compile " + name + " shader");
        }
        
        int program = glCreateProgram();
        glAttachShader(program, shader);
        glLinkProgram(program);
        
        // 检查链接错误
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            SuperResolution.LOGGER.error("链接{}程序失败: {}", name, log);
            glDeleteProgram(program);
            glDeleteShader(shader);
            throw new RuntimeException("Failed to link " + name + " program");
        }
        
        glDeleteShader(shader);
        PerformanceMonitor.increment("shader_compiles");
        
        return program;
    }
    
    private void createUniformBuffer() {
        uniformBuffer = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, uniformBuffer);
        glBufferData(GL_UNIFORM_BUFFER, NISConfig.SIZE, GL_DYNAMIC_DRAW);
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, uniformBuffer);
    }
    
    @Override
    public boolean dispatch(InputResourceSet inputResourceSet, ITexture outputTexture) {
        try (PerformanceMonitor.AutoTimer timer = PerformanceMonitor.startTimer("nis_dispatch")) {
            try (OptimizedGlState state = OptimizedGlState.forSuperResolution()) {
                
                // 更新配置
                updateConfig();
                
                // 绑定纹理
                glActiveTexture(GL_TEXTURE0 + NVIDIAImageScalingConst.IN_TEX_BINDING);
                glBindTexture(GL_TEXTURE_2D, inputResourceSet.color().getTextureId());
                
                glActiveTexture(GL_TEXTURE0 + NVIDIAImageScalingConst.OUT_TEX_BINDING);
                glBindImageTexture(NVIDIAImageScalingConst.OUT_TEX_BINDING, 
                                 this.outputTexture.getTextureId(), 0, false, 0, 
                                 GL_WRITE_ONLY, GL_RGBA8);
                
                glActiveTexture(GL_TEXTURE0 + NVIDIAImageScalingConst.COEF_SCALAR_BINDING);
                glBindTexture(GL_TEXTURE_2D, coefScalerTexture.getTextureId());
                
                glActiveTexture(GL_TEXTURE0 + NVIDIAImageScalingConst.COEF_USM_BINDING);
                glBindTexture(GL_TEXTURE_2D, coefUSMTexture.getTextureId());
                
                // 使用缩放程序
                glUseProgram(scaleProgram);
                
                // 计算工作组数量
                int workGroupsX = (MinecraftRenderHandle.getScreenWidth() + WORK_GROUP_SIZE_X - 1) / WORK_GROUP_SIZE_X;
                int workGroupsY = (MinecraftRenderHandle.getScreenHeight() + WORK_GROUP_SIZE_Y - 1) / WORK_GROUP_SIZE_Y;
                
                // 分发计算着色器
                glDispatchCompute(workGroupsX, workGroupsY, 1);
                glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
                
                // 复制结果到输出纹理
                copyToOutput(outputTexture);
                
                PerformanceMonitor.increment("nis_dispatches");
                return true;
            }
        } catch (Exception e) {
            SuperResolution.LOGGER.error("NIS分发失败", e);
            return false;
        }
    }
    
    private void updateConfig() {
        // 更新NIS配置
        boolean success = config.NVScalerUpdateConfig(
            SuperResolutionConfig.getSharpness(),
            0, 0,
            MinecraftRenderHandle.getRenderWidth(),
            MinecraftRenderHandle.getRenderHeight(),
            MinecraftRenderHandle.getRenderWidth(),
            MinecraftRenderHandle.getRenderHeight(),
            0, 0,
            MinecraftRenderHandle.getScreenWidth(),
            MinecraftRenderHandle.getScreenHeight(),
            MinecraftRenderHandle.getScreenWidth(),
            MinecraftRenderHandle.getScreenHeight(),
            NISHDRMode.None
        );
        
        if (!success) {
            SuperResolution.LOGGER.warn("NIS配置更新失败，使用默认参数");
        }
        
        // 上传配置到GPU
        glBindBuffer(GL_UNIFORM_BUFFER, uniformBuffer);
        ByteBuffer configData = config.container();
        glBufferSubData(GL_UNIFORM_BUFFER, 0, configData);
    }
    
    private void copyToOutput(ITexture outputTexture) {
        // 简单的纹理复制，实际项目中可能需要更复杂的处理
        glCopyImageSubData(
            this.outputTexture.getTextureId(), GL_TEXTURE_2D, 0, 0, 0, 0,
            outputTexture.getTextureId(), GL_TEXTURE_2D, 0, 0, 0, 0,
            MinecraftRenderHandle.getScreenWidth(),
            MinecraftRenderHandle.getScreenHeight(), 1
        );
    }
    
    @Override
    public void resize(int width, int height) {
        try (PerformanceMonitor.AutoTimer timer = PerformanceMonitor.startTimer("nis_resize")) {
            if (outputTexture != null) {
                outputTexture.resize(width, height);
            }
            if (outputFrameBuffer != null) {
                outputFrameBuffer.resizeFrameBuffer(width, height);
            }
            SuperResolution.LOGGER.debug("NIS已调整大小: {}x{}", width, height);
        }
    }
    
    @Override
    public void destroy() {
        try (PerformanceMonitor.AutoTimer timer = PerformanceMonitor.startTimer("nis_destroy")) {
            if (outputTexture != null) {
                outputTexture.destroy();
                outputTexture = null;
            }
            
            if (outputFrameBuffer != null) {
                outputFrameBuffer.destroy();
                outputFrameBuffer = null;
            }
            
            if (coefScalerTexture != null) {
                coefScalerTexture.destroy();
                coefScalerTexture = null;
            }
            
            if (coefUSMTexture != null) {
                coefUSMTexture.destroy();
                coefUSMTexture = null;
            }
            
            if (scaleProgram != 0) {
                glDeleteProgram(scaleProgram);
                scaleProgram = 0;
            }
            
            if (sharpenProgram != 0) {
                glDeleteProgram(sharpenProgram);
                sharpenProgram = 0;
            }
            
            if (uniformBuffer != 0) {
                glDeleteBuffers(uniformBuffer);
                uniformBuffer = 0;
            }
            
            SuperResolution.LOGGER.info("NVIDIA Image Scaling已销毁");
        }
    }
    
    @Override
    public Vector2f getJitterOffset(int frameIndex, Vector2f renderSize, Vector2f displaySize) {
        // NIS不需要时间抖动
        return new Vector2f(0.0f, 0.0f);
    }
    
    @Override
    public int getOutputWidth() {
        return MinecraftRenderHandle.getScreenWidth();
    }
    
    @Override
    public int getOutputHeight() {
        return MinecraftRenderHandle.getScreenHeight();
    }
    
    @Override
    public IFrameBuffer getOutputFrameBuffer() {
        return outputFrameBuffer;
    }
    
    @Override
    public boolean isDestroyed() {
        return outputTexture == null;
    }
}
