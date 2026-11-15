package noobtrieu.reimaginedintro.client.pack;

import noobtrieu.reimaginedintro.client.config.VideoFps;
import noobtrieu.reimaginedintro.client.config.VideoResolution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FrameCacheManagerTest {

    @AfterEach
    void cleanup() {
        FrameCacheManager.resetCacheRootForTest();
    }

    @Test
    void videoResolutionParsingAndDimensions() {
        assertEquals(VideoResolution.ORIGINAL, VideoResolution.fromId(null));
        assertEquals(VideoResolution.ORIGINAL, VideoResolution.fromId("unknown"));
        assertEquals(VideoResolution.P1080, VideoResolution.fromId("1080p"));
        assertEquals(VideoResolution.P720, VideoResolution.fromId("720P"));
        assertEquals(VideoResolution.P480, VideoResolution.fromId("480p"));

        assertArrayEquals(new int[]{2560, 1440}, VideoResolution.ORIGINAL.computeTargetDimensions(2560, 1440));
        assertArrayEquals(new int[]{1920, 1080}, VideoResolution.P1080.computeTargetDimensions(2560, 1440));
        assertArrayEquals(new int[]{1280, 720}, VideoResolution.P720.computeTargetDimensions(2560, 1440));
        assertArrayEquals(new int[]{853, 480}, VideoResolution.P480.computeTargetDimensions(2560, 1440));

        // Never upscale smaller sources
        assertArrayEquals(new int[]{1280, 720}, VideoResolution.P1080.computeTargetDimensions(1280, 720));
        assertFalse(VideoResolution.P1080.needsDownscale(720));
        assertTrue(VideoResolution.P720.needsDownscale(1080));

        // Filtering hides options >= source height and clamps invalid presets to ORIGINAL
        assertEquals(
            List.of(VideoResolution.ORIGINAL, VideoResolution.P1080, VideoResolution.P720, VideoResolution.P480),
            VideoResolution.getAvailableResolutions(1440)
        );
        assertEquals(
            List.of(VideoResolution.ORIGINAL, VideoResolution.P720, VideoResolution.P480),
            VideoResolution.getAvailableResolutions(1080)
        );
        assertEquals(
            List.of(VideoResolution.ORIGINAL, VideoResolution.P480),
            VideoResolution.getAvailableResolutions(720)
        );
        assertEquals(List.of(VideoResolution.ORIGINAL), VideoResolution.getAvailableResolutions(480));
        assertEquals(List.of(VideoResolution.ORIGINAL), VideoResolution.getAvailableResolutions(0));

        assertEquals(VideoResolution.ORIGINAL, VideoResolution.P1080.clampToSourceHeight(720));
        assertEquals(VideoResolution.ORIGINAL, VideoResolution.P720.clampToSourceHeight(720));
        assertEquals(VideoResolution.P480, VideoResolution.P480.clampToSourceHeight(720));
        assertEquals(VideoResolution.P720, VideoResolution.P720.clampToSourceHeight(1080));

        IntroPack emptyPack = createStubPack("empty_pack", "1.0.0", 0, 0, new byte[0]);
        assertEquals(List.of(VideoResolution.ORIGINAL), FrameCacheManager.getAvailableResolutions(emptyPack));
        assertEquals(VideoResolution.ORIGINAL, FrameCacheManager.clampResolutionForPack(emptyPack, VideoResolution.P720));
    }

    @Test
    void videoFpsFilteringClampingAndSampling() {
        assertEquals(VideoFps.ORIGINAL, VideoFps.fromId(null));
        assertEquals(VideoFps.ORIGINAL, VideoFps.fromId("unknown"));
        assertEquals(VideoFps.FPS_60, VideoFps.fromId("60"));
        assertEquals(VideoFps.FPS_30, VideoFps.fromId("30"));
        assertEquals(VideoFps.FPS_15, VideoFps.fromId("15"));

        assertEquals(
            List.of(VideoFps.ORIGINAL, VideoFps.FPS_60, VideoFps.FPS_30, VideoFps.FPS_15),
            VideoFps.getAvailableFps(120, true)
        );
        assertEquals(
            List.of(VideoFps.ORIGINAL, VideoFps.FPS_30, VideoFps.FPS_15),
            VideoFps.getAvailableFps(60, true)
        );
        assertEquals(
            List.of(VideoFps.ORIGINAL, VideoFps.FPS_15),
            VideoFps.getAvailableFps(30, true)
        );
        assertEquals(List.of(VideoFps.ORIGINAL), VideoFps.getAvailableFps(15, true));
        assertEquals(List.of(VideoFps.ORIGINAL), VideoFps.getAvailableFps(30, false));

        assertEquals(VideoFps.ORIGINAL, VideoFps.FPS_30.clampToSourceFps(30));
        assertEquals(VideoFps.ORIGINAL, VideoFps.FPS_60.clampToSourceFps(30));
        assertEquals(VideoFps.FPS_15, VideoFps.FPS_15.clampToSourceFps(30));

        // 30 FPS -> 15 FPS keeps playback duration intact and samples frames 1, 3, 5...
        assertEquals(1, VideoFps.FPS_15.mapStepToSourceFrame(VideoFps.FPS_15.computeSampleStep(0, 30, 150, false), 30, 150, false));
        assertEquals(3, VideoFps.FPS_15.mapStepToSourceFrame(VideoFps.FPS_15.computeSampleStep(67, 30, 150, false), 30, 150, false));
        assertEquals(31, VideoFps.FPS_15.mapStepToSourceFrame(VideoFps.FPS_15.computeSampleStep(1000, 30, 150, false), 30, 150, false));
        assertEquals(150, VideoFps.FPS_15.mapStepToSourceFrame(VideoFps.FPS_15.computeSampleStep(6000, 30, 150, false), 30, 150, false));

        // Loop wraps cleanly back to frame 1 after exact loop period (150 frames @ 30fps = 5000ms)
        long loopWrapStep = VideoFps.FPS_15.computeSampleStep(5000, 30, 150, true);
        assertEquals(0L, loopWrapStep);
        assertEquals(1, VideoFps.FPS_15.mapStepToSourceFrame(loopWrapStep, 30, 150, true));

        // 60 FPS -> 15 FPS samples every 4th frame (1, 5, 9...)
        assertEquals(5, VideoFps.FPS_15.mapStepToSourceFrame(1, 60, 120, false));
        assertEquals(9, VideoFps.FPS_15.mapStepToSourceFrame(2, 60, 120, false));
    }

    @Test
    void fingerprintSanitizationAndDirectoryCleanupWorkWithoutNatives(@TempDir Path tempCacheRoot) throws Exception {
        FrameCacheManager.setCacheRootForTest(tempCacheRoot);

        assertEquals("my_pack_v2", FrameCacheManager.sanitizeSegment("my/pack:v2"));
        assertEquals("unknown", FrameCacheManager.sanitizeSegment("   "));

        IntroPack v1 = createStubPack("sample/pack", "1.0.0", 10, 5, new byte[0]);
        IntroPack v2 = createStubPack("sample/pack", "1.0.1", 10, 5, new byte[0]);
        assertNotEquals(
            FrameCacheManager.buildFingerprint(v1, VideoResolution.P720),
            FrameCacheManager.buildFingerprint(v2, VideoResolution.P720)
        );
        assertNotEquals(
            FrameCacheManager.buildFingerprint(v1, VideoResolution.P720),
            FrameCacheManager.buildFingerprint(v1, VideoResolution.P480)
        );

        Path packDir = FrameCacheManager.getResolutionCacheDir("sample_pack", VideoResolution.P720);
        Files.createDirectories(packDir);
        Files.writeString(packDir.resolve("dummy.jpg"), "12345");
        assertEquals(5L, FrameCacheManager.getTotalCacheBytes());

        FrameCacheManager.invalidatePackCache("sample_pack");
        assertFalse(Files.exists(FrameCacheManager.getPackCacheDir("sample_pack")));
        assertEquals(0L, FrameCacheManager.getTotalCacheBytes());
    }

    @Test
    void cacheGeneratesDownscaledJpegAndSupportsPassthroughAndInvalidation(@TempDir Path tempCacheRoot) throws Exception {
        byte[] hdJpeg = createTestJpeg(1920, 1080);
        Assumptions.assumeTrue(isStbTranscodeAvailable(hdJpeg), "LWJGL STB natives not available on test runtime");
        FrameCacheManager.setCacheRootForTest(tempCacheRoot);

        byte[] sdJpeg = createTestJpeg(640, 360);

        IntroPack hdPack = createStubPack("hd_test_pack", "1.0.0", 2, 1, hdJpeg);
        assertEquals(
            List.of(VideoResolution.ORIGINAL, VideoResolution.P720, VideoResolution.P480),
            FrameCacheManager.getAvailableResolutions(hdPack)
        );
        assertEquals(VideoResolution.ORIGINAL, FrameCacheManager.clampResolutionForPack(hdPack, VideoResolution.P1080));
        assertEquals(VideoResolution.P720, FrameCacheManager.clampResolutionForPack(hdPack, VideoResolution.P720));

        byte[] p720Jpeg = createTestJpeg(1280, 720);
        IntroPack p720Pack = createStubPack("p720_test_pack", "1.0.0", 1, 0, p720Jpeg);
        assertEquals(
            List.of(VideoResolution.ORIGINAL, VideoResolution.P480),
            FrameCacheManager.getAvailableResolutions(p720Pack)
        );
        assertEquals(VideoResolution.ORIGINAL, FrameCacheManager.clampResolutionForPack(p720Pack, VideoResolution.P720));
        assertEquals(VideoResolution.ORIGINAL, FrameCacheManager.clampResolutionForPack(p720Pack, VideoResolution.P1080));
        assertEquals(VideoResolution.P480, FrameCacheManager.clampResolutionForPack(p720Pack, VideoResolution.P480));

        FrameCacheManager.prepareCacheAsync(hdPack, VideoResolution.P480, null).get(15, TimeUnit.SECONDS);

        assertTrue(FrameCacheManager.isCacheReady(hdPack, VideoResolution.P480));
        assertTrue(FrameCacheManager.getTotalCacheBytes() > 0L);

        try (InputStream cachedIntro1 = FrameCacheManager.openCachedFrame(hdPack, VideoResolution.P480, false, 1);
             InputStream cachedLoop1 = FrameCacheManager.openCachedFrame(hdPack, VideoResolution.P480, true, 1)) {
            assertNotNull(cachedIntro1);
            assertNotNull(cachedLoop1);
            BufferedImage decoded = ImageIO.read(cachedIntro1);
            assertNotNull(decoded);
            assertEquals(853, decoded.getWidth());
            assertEquals(480, decoded.getHeight());
        }

        // Passthrough pack: 360p fits inside 720p without duplicating JPEGs
        IntroPack sdPack = createStubPack("sd_test_pack", "1.0.0", 1, 0, sdJpeg);
        FrameCacheManager.prepareCacheAsync(sdPack, VideoResolution.P720, null).get(15, TimeUnit.SECONDS);

        assertTrue(FrameCacheManager.isCacheReady(sdPack, VideoResolution.P720));
        assertNull(FrameCacheManager.openCachedFrame(sdPack, VideoResolution.P720, false, 1));
        Path sdMeta = FrameCacheManager.getResolutionCacheDir("sd_test_pack", VideoResolution.P720).resolve(".cache_meta");
        assertTrue(Files.isRegularFile(sdMeta));
        assertTrue(Files.readString(sdMeta).contains("passthrough=true"));

        // Invalidation removes cached pack folder cleanly
        FrameCacheManager.invalidatePackCache("hd_test_pack");
        assertFalse(Files.exists(FrameCacheManager.getPackCacheDir("hd_test_pack")));
        assertNull(FrameCacheManager.openCachedFrame(hdPack, VideoResolution.P480, false, 1));

        // Clear all cache removes everything
        FrameCacheManager.clearAllCache();
        assertEquals(0L, FrameCacheManager.getTotalCacheBytes());
    }

    private static boolean isStbTranscodeAvailable(byte[] sampleJpeg) {
        try {
            return FrameCacheManager.transcodeJpegToResolution(sampleJpeg, VideoResolution.P480) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    private static byte[] createTestJpeg(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 120, 200));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, "jpg", baos));
        return baos.toByteArray();
    }

    private static IntroPack createStubPack(String id, String version, int introCount, int loopCount, byte[] frameBytes) {
        IntroPackManifest manifest = new IntroPackManifest();
        manifest.setId(id);
        manifest.setName(id);
        manifest.setVersion(version);
        manifest.setFps(30);

        return new IntroPack() {
            @Override public IntroPackManifest getManifest() { return manifest; }
            @Override public boolean hasIntro() { return introCount > 0; }
            @Override public boolean hasLoop() { return loopCount > 0; }
            @Override public boolean hasAudio() { return false; }
            @Override public boolean hasPanorama() { return false; }
            @Override public boolean hasPanoramaOverlay() { return false; }
            @Override public int getIntroFrameCount() { return introCount; }
            @Override public int getLoopFrameCount() { return loopCount; }
            @Override public InputStream openIntroFrame(int frameIndex) {
                return (frameIndex >= 1 && frameIndex <= introCount) ? new ByteArrayInputStream(frameBytes) : null;
            }
            @Override public InputStream openLoopFrame(int frameIndex) {
                return (frameIndex >= 1 && frameIndex <= loopCount) ? new ByteArrayInputStream(frameBytes) : null;
            }
            @Override public InputStream openAudio() { return null; }
            @Override public InputStream openThumbnail() { return null; }
            @Override public InputStream openPanoramaOverlay() { return null; }
            @Override public InputStream openPanoramaFace(int index) { return null; }
        };
    }
}
