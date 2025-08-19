package io.homo.superresolution.core.graphics.opengl;

import static org.lwjgl.opengl.GL45.*;

/**
 * 优化的OpenGL状态管理器
 * 只保存和恢复必要的状态，减少性能开销
 */
public class OptimizedGlState implements AutoCloseable {
    // 预定义的状态组合，避免每次都保存所有状态
    public static final int MINIMAL_STATE = 0x01;           // 最小状态集
    public static final int TEXTURE_STATE = 0x02;           // 纹理相关状态
    public static final int FRAMEBUFFER_STATE = 0x04;       // 帧缓冲状态
    public static final int SHADER_STATE = 0x08;            // 着色器状态
    public static final int RENDER_STATE = 0x10;            // 渲染状态
    
    private final int stateFlags;
    private StateSnapshot snapshot;
    
    public OptimizedGlState(int stateFlags) {
        this.stateFlags = stateFlags;
        this.snapshot = new StateSnapshot();
        saveState();
    }
    
    public OptimizedGlState() {
        this(MINIMAL_STATE);
    }
    
    private void saveState() {
        if ((stateFlags & SHADER_STATE) != 0) {
            snapshot.program = glGetInteger(GL_CURRENT_PROGRAM);
        }
        
        if ((stateFlags & FRAMEBUFFER_STATE) != 0) {
            snapshot.drawFBO = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            snapshot.readFBO = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        }
        
        if ((stateFlags & TEXTURE_STATE) != 0) {
            snapshot.activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
            snapshot.texture2D = glGetInteger(GL_TEXTURE_BINDING_2D);
        }
        
        if ((stateFlags & RENDER_STATE) != 0) {
            snapshot.vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            snapshot.blendEnabled = glIsEnabled(GL_BLEND);
            snapshot.depthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
        }
    }
    
    private void restoreState() {
        if ((stateFlags & FRAMEBUFFER_STATE) != 0) {
            GlStateCache.bindFramebuffer(GL_DRAW_FRAMEBUFFER, snapshot.drawFBO);
            GlStateCache.bindFramebuffer(GL_READ_FRAMEBUFFER, snapshot.readFBO);
        }
        
        if ((stateFlags & TEXTURE_STATE) != 0) {
            GlStateCache.activeTexture(snapshot.activeTexture);
            GlStateCache.bindTexture2D(snapshot.texture2D);
        }
        
        if ((stateFlags & RENDER_STATE) != 0) {
            GlStateCache.bindVertexArray(snapshot.vao);
            GlStateCache.setCapability(GL_BLEND, snapshot.blendEnabled);
            GlStateCache.setCapability(GL_DEPTH_TEST, snapshot.depthTestEnabled);
        }
        
        if ((stateFlags & SHADER_STATE) != 0) {
            GlStateCache.useProgram(snapshot.program);
        }
    }
    
    @Override
    public void close() {
        restoreState();
    }
    
    private static class StateSnapshot {
        int program;
        int drawFBO;
        int readFBO;
        int activeTexture;
        int texture2D;
        int vao;
        boolean blendEnabled;
        boolean depthTestEnabled;
    }
    
    /**
     * 创建用于超分辨率渲染的状态管理器
     */
    public static OptimizedGlState forSuperResolution() {
        return new OptimizedGlState(SHADER_STATE | FRAMEBUFFER_STATE | TEXTURE_STATE);
    }
    
    /**
     * 创建用于后处理的状态管理器
     */
    public static OptimizedGlState forPostProcessing() {
        return new OptimizedGlState(FRAMEBUFFER_STATE | TEXTURE_STATE | RENDER_STATE);
    }
}