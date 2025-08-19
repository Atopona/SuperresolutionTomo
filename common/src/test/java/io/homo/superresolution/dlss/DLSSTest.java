package io.homo.superresolution.dlss;

import io.homo.superresolution.common.upscale.dlss.DLSS;
import io.homo.superresolution.common.upscale.dlss.DLSSNative;
import io.homo.superresolution.common.upscale.dlss.enums.DLSSQuality;
import io.homo.superresolution.common.upscale.dlss.DLSSPerformanceStats;
import io.homo.superresolution.core.math.Vector2f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DLSS功能测试
 */
public class DLSSTest {
    
    private DLSS dlss;
    
    @BeforeEach
    void setUp() {
        // 只在支持DLSS的系统上运行测试
        Assumptions.assumeTrue(DLSSNative.isDLSSSupported(), "DLSS不受支持，跳过测试");
        
        dlss = new DLSS();
    }
    
    @AfterEach
    void tearDown() {
        if (dlss != null && !dlss.isDestroyed()) {
            dlss.destroy();
        }
    }
    
    @Test
    void testDLSSAvailability() {
        // 测试DLSS可用性检查
        boolean isAvailable = DLSS.isAvailable();
        assertTrue(isAvailable, "DLSS应该可用");
        
        // 测试版本信息
        String version = DLSS.getVersion();
        assertNotNull(version, "DLSS版本不应为null");
        assertFalse(version.isEmpty(), "DLSS版本不应为空");
        
        System.out.println("DLSS版本: " + version);
    }
    
    @Test
    void testDLSSQualityModes() {
        // 测试所有质量模式
        for (DLSSQuality quality : DLSSQuality.values()) {
            System.out.println("测试质量模式: " + quality);
            
            // 检查渲染缩放
            Vector2f renderScale = quality.getRenderScale();
            assertTrue(renderScale.x > 0 && renderScale.x <= 1.0f, 
                "渲染缩放X应在(0,1]范围内");
            assertTrue(renderScale.y > 0 && renderScale.y <= 1.0f, 
                "渲染缩放Y应在(0,1]范围内");
            
            // 检查放大倍数
            float upscaleFactor = quality.getUpscaleFactor();
            assertTrue(upscaleFactor >= 1.0f && upscaleFactor <= 4.0f, 
                "放大倍数应在[1,4]范围内");
            
            // 检查分辨率支持
            boolean supports1080p = quality.isSupported(1920, 1080);
            boolean supports4K = quality.isSupported(3840, 2160);
            
            System.out.printf("  %s: 缩放=%.2f, 放大=%.1fx, 支持1080p=%s, 支持4K=%s%n",
                quality.getDisplayName(), renderScale.x, upscaleFactor, supports1080p, supports4K);
        }
    }
    
    @Test
    void testDLSSInitialization() {
        // 测试DLSS初始化
        assertDoesNotThrow(() -> {
            dlss.init();
        }, "DLSS初始化不应抛出异常");
        
        assertFalse(dlss.isDestroyed(), "初始化后DLSS不应被标记为已销毁");
    }
    
    @Test
    void testDLSSQualityChange() {
        dlss.init();
        
        // 测试质量模式切换
        DLSSQuality originalQuality = dlss.getCurrentQuality();
        assertNotNull(originalQuality, "当前质量不应为null");
        
        // 切换到不同的质量模式
        DLSSQuality newQuality = (originalQuality == DLSSQuality.BALANCED) 
            ? DLSSQuality.QUALITY : DLSSQuality.BALANCED;
        
        dlss.setQuality(newQuality);
        assertEquals(newQuality, dlss.getCurrentQuality(), 
            "质量模式应该已更改");
        
        System.out.println("质量模式从 " + originalQuality + " 切换到 " + newQuality);
    }
    
    @Test
    void testDLSSPerformanceStats() {
        dlss.init();
        
        // 获取性能统计
        DLSSPerformanceStats stats = dlss.getPerformanceStats();
        assertNotNull(stats, "性能统计不应为null");
        
        // 检查初始状态
        assertEquals(0, stats.totalInferences, "初始推理次数应为0");
        assertEquals(0, stats.successfulInferences, "初始成功次数应为0");
        assertEquals(0, stats.failedInferences, "初始失败次数应为0");
        
        // 测试统计信息格式化
        String formattedStats = stats.formatStats();
        assertNotNull(formattedStats, "格式化统计信息不应为null");
        assertFalse(formattedStats.isEmpty(), "格式化统计信息不应为空");
        
        System.out.println("性能统计:");
        System.out.println(formattedStats);
    }
    
    @Test
    void testDLSSJitterSequence() {
        dlss.init();
        
        Vector2f renderSize = new Vector2f(1920, 1080);
        Vector2f displaySize = new Vector2f(2560, 1440);
        
        // 测试多帧的抖动偏移
        for (int frame = 0; frame < 20; frame++) {
            Vector2f jitter = dlss.getJitterOffset(frame, renderSize, displaySize);
            
            assertNotNull(jitter, "抖动偏移不应为null");
            
            // 抖动偏移应该在合理范围内
            assertTrue(Math.abs(jitter.x) <= 2.0f, 
                "抖动偏移X应在合理范围内: " + jitter.x);
            assertTrue(Math.abs(jitter.y) <= 2.0f, 
                "抖动偏移Y应在合理范围内: " + jitter.y);
            
            if (frame < 5) {
                System.out.printf("帧%d抖动偏移: (%.3f, %.3f)%n", 
                    frame, jitter.x, jitter.y);
            }
        }
    }
    
    @Test
    void testDLSSConfigValidation() {
        // 测试质量模式字符串解析
        assertEquals(DLSSQuality.PERFORMANCE, 
            DLSSQuality.fromString("performance"));
        assertEquals(DLSSQuality.BALANCED, 
            DLSSQuality.fromString("balanced"));
        assertEquals(DLSSQuality.QUALITY, 
            DLSSQuality.fromString("quality"));
        assertEquals(DLSSQuality.ULTRA_QUALITY, 
            DLSSQuality.fromString("ultra_quality"));
        assertEquals(DLSSQuality.ULTRA_PERFORMANCE, 
            DLSSQuality.fromString("ultra_performance"));
        
        // 测试无效字符串的处理
        assertEquals(DLSSQuality.BALANCED, 
            DLSSQuality.fromString("invalid_quality"));
        assertEquals(DLSSQuality.BALANCED, 
            DLSSQuality.fromString(null));
    }
    
    @Test
    void testDLSSResourceManagement() {
        // 测试资源管理
        dlss.init();
        assertFalse(dlss.isDestroyed(), "初始化后不应被销毁");
        
        // 测试重复初始化
        assertDoesNotThrow(() -> {
            dlss.init(); // 应该安全地处理重复初始化
        });
        
        // 测试销毁
        dlss.destroy();
        assertTrue(dlss.isDestroyed(), "销毁后应被标记为已销毁");
        
        // 测试重复销毁
        assertDoesNotThrow(() -> {
            dlss.destroy(); // 应该安全地处理重复销毁
        });
    }
    
    @Test
    void testDLSSErrorHandling() {
        // 测试未初始化状态下的操作
        DLSS uninitializedDLSS = new DLSS();
        
        // 这些操作应该安全失败而不是崩溃
        assertFalse(uninitializedDLSS.dispatch(null), 
            "未初始化的DLSS分发应该返回false");
        
        Vector2f jitter = uninitializedDLSS.getJitterOffset(0, 
            new Vector2f(1920, 1080), new Vector2f(2560, 1440));
        assertEquals(0.0f, jitter.x, "未初始化时抖动偏移X应为0");
        assertEquals(0.0f, jitter.y, "未初始化时抖动偏移Y应为0");
        
        uninitializedDLSS.destroy(); // 应该安全处理
    }
}