package io.homo.superresolution.core.graphics;

import io.homo.superresolution.core.graphics.opengl.GlStateCache;
import io.homo.superresolution.core.performance.PerformanceMonitor;
import io.homo.superresolution.common.SuperResolution;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL45.*;

/**
 * 批量渲染优化器
 * 减少渲染调用次数，提高GPU利用率
 */
public class BatchRenderer {
    private static final int MAX_BATCH_SIZE = 1000;
    private final List<RenderCommand> commandBatch = new ArrayList<>();
    private boolean batchingEnabled = true;
    
    /**
     * 渲染命令接口
     */
    public interface RenderCommand {
        void execute();
        int getPriority(); // 用于排序优化
        boolean canBatch(RenderCommand other); // 是否可以与其他命令批处理
    }
    
    /**
     * 添加渲染命令到批次
     */
    public void addCommand(RenderCommand command) {
        if (!batchingEnabled) {
            command.execute();
            return;
        }
        
        commandBatch.add(command);
        
        if (commandBatch.size() >= MAX_BATCH_SIZE) {
            flush();
        }
    }
    
    /**
     * 执行所有批次命令
     */
    public void flush() {
        if (commandBatch.isEmpty()) return;
        
        try (PerformanceMonitor.AutoTimer timer = PerformanceMonitor.startTimer("batch_render")) {
            // 按优先级排序命令
            commandBatch.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
            
            // 批量执行
            executeBatch();
            
            commandBatch.clear();
            PerformanceMonitor.increment("batch_flushes");
        }
    }
    
    private void executeBatch() {
        RenderCommand lastCommand = null;
        int batchCount = 0;
        
        for (RenderCommand command : commandBatch) {
            // 尝试与上一个命令批处理
            if (lastCommand != null && lastCommand.canBatch(command)) {
                batchCount++;
            } else {
                if (batchCount > 1) {
                    PerformanceMonitor.increment("batched_commands", batchCount);
                }
                batchCount = 1;
            }
            
            command.execute();
            lastCommand = command;
        }
        
        if (batchCount > 1) {
            PerformanceMonitor.increment("batched_commands", batchCount);
        }
    }
    
    /**
     * 纹理绑定命令
     */
    public static class TextureBindCommand implements RenderCommand {
        private final int unit;
        private final int texture;
        
        public TextureBindCommand(int unit, int texture) {
            this.unit = unit;
            this.texture = texture;
        }
        
        @Override
        public void execute() {
            GlStateCache.activeTexture(GL_TEXTURE0 + unit);
            GlStateCache.bindTexture2D(texture);
            PerformanceMonitor.increment("gl_calls");
        }
        
        @Override
        public int getPriority() {
            return 1; // 纹理绑定优先级较高
        }
        
        @Override
        public boolean canBatch(RenderCommand other) {
            return other instanceof TextureBindCommand;
        }
    }
    
    /**
     * 着色器使用命令
     */
    public static class ShaderUseCommand implements RenderCommand {
        private final int program;
        
        public ShaderUseCommand(int program) {
            this.program = program;
        }
        
        @Override
        public void execute() {
            GlStateCache.useProgram(program);
            PerformanceMonitor.increment("gl_calls");
        }
        
        @Override
        public int getPriority() {
            return 0; // 着色器切换优先级最高
        }
        
        @Override
        public boolean canBatch(RenderCommand other) {
            return false; // 着色器切换不能批处理
        }
    }
    
    /**
     * 帧缓冲绑定命令
     */
    public static class FramebufferBindCommand implements RenderCommand {
        private final int target;
        private final int framebuffer;
        
        public FramebufferBindCommand(int target, int framebuffer) {
            this.target = target;
            this.framebuffer = framebuffer;
        }
        
        @Override
        public void execute() {
            GlStateCache.bindFramebuffer(target, framebuffer);
            PerformanceMonitor.increment("gl_calls");
        }
        
        @Override
        public int getPriority() {
            return 2; // 帧缓冲绑定优先级中等
        }
        
        @Override
        public boolean canBatch(RenderCommand other) {
            return other instanceof FramebufferBindCommand && 
                   ((FramebufferBindCommand) other).target == this.target;
        }
    }
    
    /**
     * 批量纹理绑定优化
     */
    public void bindTextures(int startUnit, int[] textures) {
        if (textures.length <= 1) {
            // 单个纹理直接绑定
            if (textures.length == 1) {
                addCommand(new TextureBindCommand(startUnit, textures[0]));
            }
            return;
        }
        
        // 批量纹理绑定命令
        addCommand(new RenderCommand() {
            @Override
            public void execute() {
                GlStateCache.bindTextures(startUnit, textures);
                PerformanceMonitor.increment("gl_calls");
                PerformanceMonitor.increment("batched_texture_binds", textures.length);
            }
            
            @Override
            public int getPriority() {
                return 1;
            }
            
            @Override
            public boolean canBatch(RenderCommand other) {
                return false; // 批量绑定不再批处理
            }
        });
    }
    
    /**
     * 设置批处理模式
     */
    public void setBatchingEnabled(boolean enabled) {
        if (!enabled && !commandBatch.isEmpty()) {
            flush(); // 禁用前先清空批次
        }
        this.batchingEnabled = enabled;
    }
    
    /**
     * 获取当前批次大小
     */
    public int getBatchSize() {
        return commandBatch.size();
    }
    
    /**
     * 清空批次（不执行）
     */
    public void clear() {
        commandBatch.clear();
    }
}