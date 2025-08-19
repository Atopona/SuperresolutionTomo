package io.homo.superresolution.common.upscale.nis;

import io.homo.superresolution.common.SuperResolution;
import io.homo.superresolution.common.minecraft.MinecraftRenderHandle;
import io.homo.superresolution.common.upscale.nis.enums.NISGPUArchitecture;
import io.homo.superresolution.core.performance.PerformanceMonitor;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL43.*;

/**
 * NIS性能基准测试工具
 * 用于测试不同配置下的NIS性能表现
 */
public class NISBenchmark {
    
    public static class BenchmarkResult {
        public final NISGPUArchitecture architecture;
        public final int workGroupX;
        public final int workGroupY;
        public final boolean useOptimizedPath;
        public final double avgFrameTimeMs;
        public final double minFrameTimeMs;
        public final double maxFrameTimeMs;
        public final int sampleCount;
        public final double throughputMPixelsPerSec;
        
        public BenchmarkResult(NISGPUArchitecture architecture, int workGroupX, int workGroupY, 
                             boolean useOptimizedPath, double avgFrameTimeMs, double minFrameTimeMs, 
                             double maxFrameTimeMs, int sampleCount, double throughputMPixelsPerSec) {
            this.architecture = architecture;
            this.workGroupX = workGroupX;
            this.workGroupY = workGroupY;
            this.useOptimizedPath = useOptimizedPath;
            this.avgFrameTimeMs = avgFrameTimeMs;
            this.minFrameTimeMs = minFrameTimeMs;
            this.maxFrameTimeMs = maxFrameTimeMs;
            this.sampleCount = sampleCount;
            this.throughputMPixelsPerSec = throughputMPixelsPerSec;
        }
        
        @Override
        public String toString() {
            return String.format(
                "NIS Benchmark Result:\n" +
                "  Architecture: %s\n" +
                "  Work Group: %dx%d\n" +
                "  Optimized Path: %s\n" +
                "  Avg Frame Time: %.2f ms\n" +
                "  Min Frame Time: %.2f ms\n" +
                "  Max Frame Time: %.2f ms\n" +
                "  Sample Count: %d\n" +
                "  Throughput: %.2f MPixels/sec",
                architecture.getDisplayName(), workGroupX, workGroupY, useOptimizedPath,
                avgFrameTimeMs, minFrameTimeMs, maxFrameTimeMs, sampleCount, throughputMPixelsPerSec
            );
        }
    }
    
    /**
     * 运行完整的NIS基准测试
     */
    public static List<BenchmarkResult> runFullBenchmark() {
        SuperResolution.LOGGER.info("开始NIS性能基准测试...");
        
        List<BenchmarkResult> results = new ArrayList<>();
        
        // 检测当前GPU架构
        String renderer = glGetString(GL_RENDERER);
        NISGPUArchitecture detectedArch = NISGPUArchitecture.detectFromRenderer(renderer);
        
        SuperResolution.LOGGER.info("检测到GPU: {}, 架构: {}", renderer, detectedArch.getDisplayName());
        
        // 测试不同的工作组大小配置
        int[][] workGroupConfigs = {
            {16, 16},   // 小工作组
            {32, 24},   // NVIDIA推荐
            {64, 16},   // AMD推荐
            {32, 32},   // 正方形工作组
            {64, 8},    // 宽工作组
            {8, 64}     // 高工作组
        };
        
        for (int[] config : workGroupConfigs) {
            int workGroupX = config[0];
            int workGroupY = config[1];
            
            // 测试优化路径和非优化路径
            for (boolean useOptimized : new boolean[]{true, false}) {
                try {
                    BenchmarkResult result = benchmarkConfiguration(
                        detectedArch, workGroupX, workGroupY, useOptimized
                    );
                    results.add(result);
                    
                    SuperResolution.LOGGER.info("完成配置测试: {}x{}, 优化: {}, 平均帧时间: {:.2f}ms",
                                               workGroupX, workGroupY, useOptimized, result.avgFrameTimeMs);
                    
                } catch (Exception e) {
                    SuperResolution.LOGGER.warn("配置测试失败: {}x{}, 优化: {}", 
                                               workGroupX, workGroupY, useOptimized, e);
                }
            }
        }
        
        // 找出最佳配置
        BenchmarkResult bestResult = results.stream()
            .min((a, b) -> Double.compare(a.avgFrameTimeMs, b.avgFrameTimeMs))
            .orElse(null);
        
        if (bestResult != null) {
            SuperResolution.LOGGER.info("最佳NIS配置:\n{}", bestResult);
        }
        
        SuperResolution.LOGGER.info("NIS基准测试完成，共测试{}个配置", results.size());
        
        return results;
    }
    
    /**
     * 测试特定配置的性能
     */
    public static BenchmarkResult benchmarkConfiguration(NISGPUArchitecture architecture, 
                                                        int workGroupX, int workGroupY, 
                                                        boolean useOptimizedPath) {
        final int WARMUP_FRAMES = 30;
        final int BENCHMARK_FRAMES = 100;
        
        SuperResolution.LOGGER.debug("开始测试配置: {}x{}, 优化: {}", workGroupX, workGroupY, useOptimizedPath);
        
        // 创建测试用的NIS实例（简化版本）
        TestNISInstance testInstance = new TestNISInstance(architecture, workGroupX, workGroupY, useOptimizedPath);
        
        try {
            testInstance.init();
            
            // 预热阶段
            for (int i = 0; i < WARMUP_FRAMES; i++) {
                testInstance.dispatch();
                glFinish(); // 确保GPU完成工作
            }
            
            // 基准测试阶段
            List<Double> frameTimes = new ArrayList<>();
            
            for (int i = 0; i < BENCHMARK_FRAMES; i++) {
                long startTime = System.nanoTime();
                testInstance.dispatch();
                glFinish(); // 确保GPU完成工作
                long endTime = System.nanoTime();
                
                double frameTimeMs = (endTime - startTime) / 1_000_000.0;
                frameTimes.add(frameTimeMs);
            }
            
            // 计算统计数据
            double avgFrameTime = frameTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double minFrameTime = frameTimes.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double maxFrameTime = frameTimes.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            
            // 计算吞吐量 (MPixels/sec)
            int width = MinecraftRenderHandle.getScreenWidth();
            int height = MinecraftRenderHandle.getScreenHeight();
            double pixelsPerFrame = width * height / 1_000_000.0; // MPixels
            double throughput = pixelsPerFrame / (avgFrameTime / 1000.0); // MPixels/sec
            
            return new BenchmarkResult(
                architecture, workGroupX, workGroupY, useOptimizedPath,
                avgFrameTime, minFrameTime, maxFrameTime, BENCHMARK_FRAMES, throughput
            );
            
        } finally {
            testInstance.destroy();
        }
    }
    
    /**
     * 快速性能测试，用于运行时优化
     */
    public static BenchmarkResult quickPerformanceTest(NISGPUArchitecture architecture, 
                                                      int workGroupX, int workGroupY, 
                                                      boolean useOptimizedPath) {
        final int QUICK_TEST_FRAMES = 10;
        
        TestNISInstance testInstance = new TestNISInstance(architecture, workGroupX, workGroupY, useOptimizedPath);
        
        try {
            testInstance.init();
            
            List<Double> frameTimes = new ArrayList<>();
            
            for (int i = 0; i < QUICK_TEST_FRAMES; i++) {
                long startTime = System.nanoTime();
                testInstance.dispatch();
                glFinish();
                long endTime = System.nanoTime();
                
                double frameTimeMs = (endTime - startTime) / 1_000_000.0;
                frameTimes.add(frameTimeMs);
            }
            
            double avgFrameTime = frameTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double minFrameTime = frameTimes.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            double maxFrameTime = frameTimes.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            
            int width = MinecraftRenderHandle.getScreenWidth();
            int height = MinecraftRenderHandle.getScreenHeight();
            double pixelsPerFrame = width * height / 1_000_000.0;
            double throughput = pixelsPerFrame / (avgFrameTime / 1000.0);
            
            return new BenchmarkResult(
                architecture, workGroupX, workGroupY, useOptimizedPath,
                avgFrameTime, minFrameTime, maxFrameTime, QUICK_TEST_FRAMES, throughput
            );
            
        } finally {
            testInstance.destroy();
        }
    }
    
    /**
     * 自动选择最佳NIS配置
     */
    public static BenchmarkResult findOptimalConfiguration() {
        SuperResolution.LOGGER.info("自动寻找最佳NIS配置...");
        
        String renderer = glGetString(GL_RENDERER);
        NISGPUArchitecture architecture = NISGPUArchitecture.detectFromRenderer(renderer);
        
        // 基于架构的候选配置
        int[][] candidates;
        switch (architecture) {
            case NVIDIA:
                candidates = new int[][]{{32, 24}, {32, 32}, {64, 16}};
                break;
            case AMD:
                candidates = new int[][]{{64, 16}, {32, 32}, {32, 24}};
                break;
            case INTEL:
                candidates = new int[][]{{16, 16}, {32, 24}, {16, 32}};
                break;
            default:
                candidates = new int[][]{{32, 24}, {16, 16}, {64, 16}};
                break;
        }
        
        BenchmarkResult bestResult = null;
        
        for (int[] candidate : candidates) {
            try {
                BenchmarkResult result = quickPerformanceTest(
                    architecture, candidate[0], candidate[1], architecture.supportsOptimizedPath()
                );
                
                if (bestResult == null || result.avgFrameTimeMs < bestResult.avgFrameTimeMs) {
                    bestResult = result;
                }
                
            } catch (Exception e) {
                SuperResolution.LOGGER.warn("测试配置失败: {}x{}", candidate[0], candidate[1], e);
            }
        }
        
        if (bestResult != null) {
            SuperResolution.LOGGER.info("找到最佳NIS配置: {}x{}, 平均帧时间: {:.2f}ms",
                                       bestResult.workGroupX, bestResult.workGroupY, bestResult.avgFrameTimeMs);
        }
        
        return bestResult;
    }
    
    /**
     * 简化的NIS测试实例
     */
    private static class TestNISInstance {
        private final NISGPUArchitecture architecture;
        private final int workGroupX;
        private final int workGroupY;
        private final boolean useOptimizedPath;
        
        private int testProgram;
        private int testTexture;
        private int outputTexture;
        
        public TestNISInstance(NISGPUArchitecture architecture, int workGroupX, int workGroupY, boolean useOptimizedPath) {
            this.architecture = architecture;
            this.workGroupX = workGroupX;
            this.workGroupY = workGroupY;
            this.useOptimizedPath = useOptimizedPath;
        }
        
        public void init() {
            // 创建简单的测试着色器
            String shaderSource = String.format("""
                #version 430
                layout(local_size_x = %d, local_size_y = %d, local_size_z = 1) in;
                layout(binding = 0) uniform sampler2D inputTex;
                layout(binding = 1, rgba8) uniform writeonly image2D outputImg;
                
                void main() {
                    ivec2 pos = ivec2(gl_GlobalInvocationID.xy);
                    ivec2 size = imageSize(outputImg);
                    if (pos.x >= size.x || pos.y >= size.y) return;
                    
                    vec2 texCoord = (vec2(pos) + 0.5) / vec2(size);
                    vec4 color = texture(inputTex, texCoord);
                    
                    %s
                    
                    imageStore(outputImg, pos, color);
                }
                """, workGroupX, workGroupY, 
                useOptimizedPath ? "color = color * 1.1; // 简单的处理" : "// 基础处理");
            
            testProgram = createTestProgram(shaderSource);
            
            // 创建测试纹理
            int width = MinecraftRenderHandle.getScreenWidth();
            int height = MinecraftRenderHandle.getScreenHeight();
            
            testTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, testTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            
            outputTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, outputTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        }
        
        public void dispatch() {
            glUseProgram(testProgram);
            
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, testTexture);
            
            glBindImageTexture(1, outputTexture, 0, false, 0, GL_WRITE_ONLY, GL_RGBA8);
            
            int width = MinecraftRenderHandle.getScreenWidth();
            int height = MinecraftRenderHandle.getScreenHeight();
            int workGroupsX = (width + workGroupX - 1) / workGroupX;
            int workGroupsY = (height + workGroupY - 1) / workGroupY;
            
            glDispatchCompute(workGroupsX, workGroupsY, 1);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        }
        
        public void destroy() {
            if (testProgram != 0) {
                glDeleteProgram(testProgram);
            }
            if (testTexture != 0) {
                glDeleteTextures(testTexture);
            }
            if (outputTexture != 0) {
                glDeleteTextures(outputTexture);
            }
        }
        
        private int createTestProgram(String source) {
            int shader = glCreateShader(GL_COMPUTE_SHADER);
            glShaderSource(shader, source);
            glCompileShader(shader);
            
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
                String log = glGetShaderInfoLog(shader);
                glDeleteShader(shader);
                throw new RuntimeException("Test shader compilation failed: " + log);
            }
            
            int program = glCreateProgram();
            glAttachShader(program, shader);
            glLinkProgram(program);
            
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                String log = glGetProgramInfoLog(program);
                glDeleteProgram(program);
                glDeleteShader(shader);
                throw new RuntimeException("Test program linking failed: " + log);
            }
            
            glDeleteShader(shader);
            return program;
        }
    }
}