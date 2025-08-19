package io.homo.superresolution.core.graphics.opengl;

import static org.lwjgl.opengl.GL45.*;

/**
 * 高性能OpenGL状态缓存管理器
 * 通过缓存状态减少不必要的OpenGL调用
 */
public class GlStateCache {
    private static final int MAX_TEXTURE_UNITS = 32;
    
    // 缓存的状态
    private static int cachedProgram = -1;
    private static int cachedVAO = -1;
    private static int cachedVBO = -1;
    private static int cachedEBO = -1;
    private static int cachedDrawFBO = -1;
    private static int cachedReadFBO = -1;
    private static int cachedActiveTexture = -1;
    private static final int[] cachedTextures2D = new int[MAX_TEXTURE_UNITS];
    private static boolean[] cachedCapabilities = new boolean[16]; // 预分配常用能力
    private static boolean initialized = false;
    
    // 能力索引
    private static final int CAP_BLEND = 0;
    private static final int CAP_DEPTH_TEST = 1;
    private static final int CAP_CULL_FACE = 2;
    private static final int CAP_SCISSOR_TEST = 3;
    
    static {
        // 初始化缓存数组
        for (int i = 0; i < MAX_TEXTURE_UNITS; i++) {
            cachedTextures2D[i] = -1;
        }
        for (int i = 0; i < cachedCapabilities.length; i++) {
            cachedCapabilities[i] = false;
        }
    }
    
    public static void init() {
        if (initialized) return;
        
        // 初始化时获取当前状态
        cachedProgram = glGetInteger(GL_CURRENT_PROGRAM);
        cachedVAO = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        cachedVBO = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        cachedEBO = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        cachedDrawFBO = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        cachedReadFBO = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        cachedActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        
        // 缓存纹理绑定状态
        int originalActiveTexture = cachedActiveTexture;
        for (int i = 0; i < MAX_TEXTURE_UNITS; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            cachedTextures2D[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
        }
        glActiveTexture(originalActiveTexture);
        
        // 缓存能力状态
        cachedCapabilities[CAP_BLEND] = glIsEnabled(GL_BLEND);
        cachedCapabilities[CAP_DEPTH_TEST] = glIsEnabled(GL_DEPTH_TEST);
        cachedCapabilities[CAP_CULL_FACE] = glIsEnabled(GL_CULL_FACE);
        cachedCapabilities[CAP_SCISSOR_TEST] = glIsEnabled(GL_SCISSOR_TEST);
        
        initialized = true;
    }
    
    public static void useProgram(int program) {
        if (cachedProgram != program) {
            glUseProgram(program);
            cachedProgram = program;
        }
    }
    
    public static void bindVertexArray(int vao) {
        if (cachedVAO != vao) {
            glBindVertexArray(vao);
            cachedVAO = vao;
        }
    }
    
    public static void bindBuffer(int target, int buffer) {
        switch (target) {
            case GL_ARRAY_BUFFER:
                if (cachedVBO != buffer) {
                    glBindBuffer(target, buffer);
                    cachedVBO = buffer;
                }
                break;
            case GL_ELEMENT_ARRAY_BUFFER:
                if (cachedEBO != buffer) {
                    glBindBuffer(target, buffer);
                    cachedEBO = buffer;
                }
                break;
            default:
                glBindBuffer(target, buffer); // 不缓存其他类型
                break;
        }
    }
    
    public static void bindFramebuffer(int target, int framebuffer) {
        switch (target) {
            case GL_DRAW_FRAMEBUFFER:
                if (cachedDrawFBO != framebuffer) {
                    glBindFramebuffer(target, framebuffer);
                    cachedDrawFBO = framebuffer;
                }
                break;
            case GL_READ_FRAMEBUFFER:
                if (cachedReadFBO != framebuffer) {
                    glBindFramebuffer(target, framebuffer);
                    cachedReadFBO = framebuffer;
                }
                break;
            case GL_FRAMEBUFFER:
                if (cachedDrawFBO != framebuffer || cachedReadFBO != framebuffer) {
                    glBindFramebuffer(target, framebuffer);
                    cachedDrawFBO = framebuffer;
                    cachedReadFBO = framebuffer;
                }
                break;
        }
    }
    
    public static void activeTexture(int texture) {
        if (cachedActiveTexture != texture) {
            glActiveTexture(texture);
            cachedActiveTexture = texture;
        }
    }
    
    public static void bindTexture2D(int texture) {
        int unit = cachedActiveTexture - GL_TEXTURE0;
        if (unit >= 0 && unit < MAX_TEXTURE_UNITS && cachedTextures2D[unit] != texture) {
            glBindTexture(GL_TEXTURE_2D, texture);
            cachedTextures2D[unit] = texture;
        }
    }
    
    public static void setCapability(int cap, boolean enabled) {
        int index = getCapabilityIndex(cap);
        if (index >= 0 && cachedCapabilities[index] != enabled) {
            if (enabled) {
                glEnable(cap);
            } else {
                glDisable(cap);
            }
            cachedCapabilities[index] = enabled;
        }
    }
    
    private static int getCapabilityIndex(int cap) {
        switch (cap) {
            case GL_BLEND: return CAP_BLEND;
            case GL_DEPTH_TEST: return CAP_DEPTH_TEST;
            case GL_CULL_FACE: return CAP_CULL_FACE;
            case GL_SCISSOR_TEST: return CAP_SCISSOR_TEST;
            default: return -1; // 不缓存的能力
        }
    }
    
    /**
     * 强制刷新所有缓存状态（在上下文切换时使用）
     */
    public static void invalidateCache() {
        cachedProgram = -1;
        cachedVAO = -1;
        cachedVBO = -1;
        cachedEBO = -1;
        cachedDrawFBO = -1;
        cachedReadFBO = -1;
        cachedActiveTexture = -1;
        
        for (int i = 0; i < MAX_TEXTURE_UNITS; i++) {
            cachedTextures2D[i] = -1;
        }
        
        for (int i = 0; i < cachedCapabilities.length; i++) {
            cachedCapabilities[i] = false;
        }
    }
    
    /**
     * 批量设置纹理绑定（减少状态切换）
     */
    public static void bindTextures(int startUnit, int[] textures) {
        for (int i = 0; i < textures.length; i++) {
            int unit = startUnit + i;
            if (unit < MAX_TEXTURE_UNITS) {
                activeTexture(GL_TEXTURE0 + unit);
                bindTexture2D(textures[i]);
            }
        }
    }
}