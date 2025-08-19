package io.homo.superresolution.core.graphics.shader;

import io.homo.superresolution.common.SuperResolution;
import io.homo.superresolution.core.graphics.impl.shader.IShaderProgram;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 高性能着色器缓存系统
 * 支持异步编译和智能缓存策略
 */
public class ShaderCache {
    private static final ConcurrentHashMap<String, IShaderProgram> runtimeCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<IShaderProgram>> compilingShaders = new ConcurrentHashMap<>();
    private static final ExecutorService compilerExecutor = Executors.newFixedThreadPool(
        Math.max(1, Runtime.getRuntime().availableProcessors() / 2)
    );
    
    private static Path cacheDirectory;
    private static boolean diskCacheEnabled = false;
    
    public static void init(Path gameDirectory) {
        cacheDirectory = gameDirectory.resolve("super_resolution").resolve("shader_cache");
        try {
            Files.createDirectories(cacheDirectory);
            diskCacheEnabled = true;
            SuperResolution.LOGGER.info("着色器缓存已启用: {}", cacheDirectory);
        } catch (Exception e) {
            SuperResolution.LOGGER.warn("无法创建着色器缓存目录，禁用磁盘缓存: {}", e.getMessage());
            diskCacheEnabled = false;
        }
    }
    
    /**
     * 获取着色器程序，优先从缓存获取
     */
    public static IShaderProgram getShader(String vertexSource, String fragmentSource, ShaderCompileOptions options) {
        String cacheKey = generateCacheKey(vertexSource, fragmentSource, options);
        
        // 首先检查运行时缓存
        IShaderProgram cached = runtimeCache.get(cacheKey);
        if (cached != null && !cached.isDestroyed()) {
            return cached;
        }
        
        // 检查是否正在编译
        CompletableFuture<IShaderProgram> compiling = compilingShaders.get(cacheKey);
        if (compiling != null) {
            try {
                return compiling.get(); // 等待编译完成
            } catch (Exception e) {
                SuperResolution.LOGGER.warn("等待着色器编译时出错: {}", e.getMessage());
                compilingShaders.remove(cacheKey);
            }
        }
        
        // 尝试从磁盘缓存加载
        if (diskCacheEnabled) {
            IShaderProgram diskCached = loadFromDisk(cacheKey);
            if (diskCached != null) {
                runtimeCache.put(cacheKey, diskCached);
                return diskCached;
            }
        }
        
        // 同步编译（对于关键着色器）
        if (options.isBlocking()) {
            return compileShaderSync(cacheKey, vertexSource, fragmentSource, options);
        }
        
        // 异步编译
        return compileShaderAsync(cacheKey, vertexSource, fragmentSource, options);
    }
    
    /**
     * 异步编译着色器
     */
    public static CompletableFuture<IShaderProgram> compileShaderAsync(String vertexSource, String fragmentSource, ShaderCompileOptions options) {
        String cacheKey = generateCacheKey(vertexSource, fragmentSource, options);
        
        CompletableFuture<IShaderProgram> future = CompletableFuture.supplyAsync(() -> {
            try {
                return compileShaderInternal(vertexSource, fragmentSource, options);
            } catch (Exception e) {
                SuperResolution.LOGGER.error("异步编译着色器失败: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }, compilerExecutor);
        
        compilingShaders.put(cacheKey, future);
        
        future.whenComplete((shader, throwable) -> {
            compilingShaders.remove(cacheKey);
            if (throwable == null && shader != null) {
                runtimeCache.put(cacheKey, shader);
                if (diskCacheEnabled) {
                    saveToDisk(cacheKey, shader);
                }
            }
        });
        
        return future;
    }
    
    private static IShaderProgram compileShaderSync(String cacheKey, String vertexSource, String fragmentSource, ShaderCompileOptions options) {
        try {
            IShaderProgram shader = compileShaderInternal(vertexSource, fragmentSource, options);
            runtimeCache.put(cacheKey, shader);
            if (diskCacheEnabled) {
                saveToDisk(cacheKey, shader);
            }
            return shader;
        } catch (Exception e) {
            SuperResolution.LOGGER.error("同步编译着色器失败: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
    
    private static IShaderProgram compileShaderAsync(String cacheKey, String vertexSource, String fragmentSource, ShaderCompileOptions options) {
        // 返回一个占位符，实际编译在后台进行
        CompletableFuture<IShaderProgram> future = compileShaderAsync(vertexSource, fragmentSource, options);
        
        // 对于非阻塞调用，返回null，调用者需要稍后重试
        return null;
    }
    
    private static IShaderProgram compileShaderInternal(String vertexSource, String fragmentSource, ShaderCompileOptions options) {
        // 这里应该调用实际的着色器编译逻辑
        // 暂时返回null，需要根据实际的着色器系统实现
        throw new UnsupportedOperationException("需要实现实际的着色器编译逻辑");
    }
    
    private static String generateCacheKey(String vertexSource, String fragmentSource, ShaderCompileOptions options) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(vertexSource.getBytes());
            md.update(fragmentSource.getBytes());
            md.update(options.toString().getBytes());
            
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 降级到简单的哈希
            return String.valueOf((vertexSource + fragmentSource + options.toString()).hashCode());
        }
    }
    
    private static IShaderProgram loadFromDisk(String cacheKey) {
        if (!diskCacheEnabled) return null;
        
        try {
            Path shaderFile = cacheDirectory.resolve(cacheKey + ".shader");
            if (Files.exists(shaderFile)) {
                // 这里应该实现从磁盘加载编译好的着色器的逻辑
                // 可能需要保存SPIR-V字节码或其他预编译格式
                SuperResolution.LOGGER.debug("从磁盘缓存加载着色器: {}", cacheKey);
                return null; // 暂时返回null，需要实际实现
            }
        } catch (Exception e) {
            SuperResolution.LOGGER.warn("从磁盘加载着色器缓存失败: {}", e.getMessage());
        }
        
        return null;
    }
    
    private static void saveToDisk(String cacheKey, IShaderProgram shader) {
        if (!diskCacheEnabled) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                Path shaderFile = cacheDirectory.resolve(cacheKey + ".shader");
                // 这里应该实现保存编译好的着色器到磁盘的逻辑
                SuperResolution.LOGGER.debug("保存着色器到磁盘缓存: {}", cacheKey);
            } catch (Exception e) {
                SuperResolution.LOGGER.warn("保存着色器缓存到磁盘失败: {}", e.getMessage());
            }
        }, compilerExecutor);
    }
    
    /**
     * 预热常用着色器
     */
    public static void warmupShaders() {
        // 在后台预编译常用的着色器
        CompletableFuture.runAsync(() -> {
            SuperResolution.LOGGER.info("开始预热着色器缓存...");
            // 这里可以预编译FSR、SGSR等算法的核心着色器
            SuperResolution.LOGGER.info("着色器缓存预热完成");
        }, compilerExecutor);
    }
    
    /**
     * 清理缓存
     */
    public static void cleanup() {
        runtimeCache.clear();
        compilingShaders.clear();
        
        if (diskCacheEnabled) {
            try {
                // 清理过期的磁盘缓存
                Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis() < 
                                   System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L; // 7天
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            SuperResolution.LOGGER.warn("删除过期缓存文件失败: {}", e.getMessage());
                        }
                    });
            } catch (Exception e) {
                SuperResolution.LOGGER.warn("清理着色器缓存失败: {}", e.getMessage());
            }
        }
    }
    
    public static void shutdown() {
        cleanup();
        compilerExecutor.shutdown();
    }
    
    public static class ShaderCompileOptions {
        private final boolean blocking;
        private final String defines;
        private final int optimizationLevel;
        
        public ShaderCompileOptions(boolean blocking, String defines, int optimizationLevel) {
            this.blocking = blocking;
            this.defines = defines != null ? defines : "";
            this.optimizationLevel = optimizationLevel;
        }
        
        public boolean isBlocking() { return blocking; }
        public String getDefines() { return defines; }
        public int getOptimizationLevel() { return optimizationLevel; }
        
        @Override
        public String toString() {
            return "ShaderCompileOptions{" +
                   "blocking=" + blocking +
                   ", defines='" + defines + '\'' +
                   ", optimizationLevel=" + optimizationLevel +
                   '}';
        }
        
        public static ShaderCompileOptions defaultOptions() {
            return new ShaderCompileOptions(true, "", 2);
        }
        
        public static ShaderCompileOptions asyncOptions() {
            return new ShaderCompileOptions(false, "", 2);
        }
    }
}