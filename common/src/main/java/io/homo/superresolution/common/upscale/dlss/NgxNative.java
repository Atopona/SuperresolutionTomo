package io.homo.superresolution.common.upscale.dlss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * JNI facade for NGX runtime. Safe wrappers are provided so that
 * absence of the native library will not crash Java code paths.
 */
public final class NgxNative {
    private static final Logger LOGGER = LoggerFactory.getLogger("SuperResolution-NGXNative");
    private static final String LIB_NAME = "ngx_jni"; // native lib base name
    private static final boolean LOADED;

    private static Path getExtractionDir() {
        // Use current working directory (game run dir) + /superresolution_natives
        String userDir = System.getProperty("user.dir", ".");
        return Path.of(userDir, "superresolution_natives");
    }

    private static String getResourcePath() {
        // Currently only Windows x64 is supported by our CI build
        return "/natives/win64/" + LIB_NAME + ".dll";
    }

    private static boolean tryLoadFromResource() {
        Path dir = getExtractionDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.warn("Failed to create natives dir {}: {}", dir, e.toString());
            return false;
        }
        Path out = dir.resolve(LIB_NAME + ".dll");
        if (!Files.exists(out)) {
            String resPath = getResourcePath();
            try (InputStream in = NgxNative.class.getResourceAsStream(resPath)) {
                if (in == null) {
                    LOGGER.info("Embedded NGX native not found at {}", resPath);
                    return false;
                }
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Extracted embedded native to {}", out);
            } catch (IOException e) {
                LOGGER.warn("Failed to extract embedded native: {}", e.toString());
                return false;
            }
        }
        try {
            System.load(out.toAbsolutePath().toString());
            LOGGER.info("Loaded native library from {}", out.toAbsolutePath());
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Failed to load extracted native {}: {}", out, t.toString());
            return false;
        }
    }

    static {
        boolean ok = false;
        try {
            System.loadLibrary(LIB_NAME);
            ok = true;
            LOGGER.info("Loaded native library: {} (system)", LIB_NAME);
        } catch (Throwable t) {
            LOGGER.info("Native NGX bridge not present via System.loadLibrary ({}): {}", LIB_NAME, t.toString());
            // Try extract from mod resources and load
            ok = tryLoadFromResource();
        }
        LOADED = ok;
    }

    private NgxNative() {}

    public static boolean isRuntimePresent() {
        return LOADED;
    }

    // === Native method declarations ===
    private static native boolean nInitRuntime();
    private static native boolean nShutdown();
    private static native long nCreateFeature(int renderW, int renderH, int outputW, int outputH,
                                              int qualityPreset /*0..4*/, boolean autoExposure, float sharpness);
    private static native boolean nReleaseFeature(long featureHandle);
    private static native boolean nEvaluate(long featureHandle,
                                            long colorTex, long depthTex, long motionVecTex,
                                            long exposureTex, long outputTex);

    // === Safe wrappers (no-op when native not present) ===
    public static boolean initRuntime() { return LOADED && nInitRuntime(); }
    public static boolean shutdown() { return LOADED && nShutdown(); }
    public static long createFeature(int renderW, int renderH, int outputW, int outputH,
                                     int qualityPreset, boolean autoExposure, float sharpness) {
        return LOADED ? nCreateFeature(renderW, renderH, outputW, outputH, qualityPreset, autoExposure, sharpness) : 0L;
    }
    public static boolean releaseFeature(long featureHandle) { return LOADED && nReleaseFeature(featureHandle); }
    public static boolean evaluate(long featureHandle,
                                   long colorTex, long depthTex, long motionVecTex,
                                   long exposureTex, long outputTex) {
        return LOADED && nEvaluate(featureHandle, colorTex, depthTex, motionVecTex, exposureTex, outputTex);
    }
}

