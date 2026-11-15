package noobtrieu.reimaginedintro.client.pack;

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
