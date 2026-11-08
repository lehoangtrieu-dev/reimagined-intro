package noobtrieu.reimaginedintro.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ZipIntroPackTest {

    @Test
    public void testFullZipIntroPackAutoCountsFrames(@TempDir Path tempDir) throws IOException {
        File zipFile = tempDir.resolve("full_pack.zip").toFile();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // Manifest
            addEntry(zos, "intro.json", """
                {
                  "format_version": 1,
                  "id": "full_test_pack",
                  "name": "Full Test Pack",
                  "author": "Tester",
                  "official": false,
                  "fps": 60
                }
                """);

            // Thumbnail
            addEntry(zos, "thumbnail.png", "fake_png_data");

            // Intro frames (out of order in zip to verify alphabetical sorting)
            addEntry(zos, "intro/frame_0003.jpg", "frame3");
            addEntry(zos, "intro/frame_0001.jpg", "frame1");
            addEntry(zos, "intro/frame_0002.jpg", "frame2");

            // Audio
            addEntry(zos, "intro/audio.ogg", "audio_bytes");

            // Loop frames
            addEntry(zos, "loop/frame_0001.png", "loop1");
            addEntry(zos, "loop/frame_0002.png", "loop2");

            // Panorama
            for (int i = 0; i < 6; i++) {
                addEntry(zos, "panorama/panorama_" + i + ".png", "pano" + i);
            }
            addEntry(zos, "panorama/panorama_overlay.png", "overlay");
        }

        try (ZipIntroPack pack = new ZipIntroPack(zipFile)) {
            assertEquals("full_test_pack", pack.getId());
            assertEquals("Full Test Pack", pack.getName());
            assertEquals(60, pack.getFps());
            assertFalse(pack.isOfficial());

            // Auto-counted frames
            assertTrue(pack.hasIntro());
            assertEquals(3, pack.getIntroFrameCount());

            assertTrue(pack.hasLoop());
            assertEquals(2, pack.getLoopFrameCount());

            assertTrue(pack.hasAudio());
            assertTrue(pack.hasPanorama());
            assertTrue(pack.hasPanoramaOverlay());

            // Test frame ordering: frame 1 should return "frame1"
            try (InputStream is = pack.openIntroFrame(1)) {
                assertNotNull(is);
                assertEquals("frame1", new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (InputStream is = pack.openIntroFrame(2)) {
                assertNotNull(is);
                assertEquals("frame2", new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }

            // Test thumbnail & audio
            try (InputStream is = pack.openThumbnail()) {
                assertNotNull(is);
                assertEquals("fake_png_data", new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (InputStream is = pack.openAudio()) {
                assertNotNull(is);
                assertEquals("audio_bytes", new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }

            // Test panorama faces
            for (int i = 0; i < 6; i++) {
                try (InputStream is = pack.openPanoramaFace(i)) {
                    assertNotNull(is);
                    assertEquals("pano" + i, new String(is.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
    }

    @Test
    public void testNestedFolderZipDetection(@TempDir Path tempDir) throws IOException {
        File zipFile = tempDir.resolve("nested_pack.zip").toFile();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addEntry(zos, "my_custom_pack/intro.json", """
                {
                  "id": "nested_pack",
                  "name": "Nested Pack"
                }
                """);
            addEntry(zos, "my_custom_pack/thumbnail.png", "thumb");
            addEntry(zos, "my_custom_pack/intro/frame_0001.jpg", "f1");
        }

        try (ZipIntroPack pack = new ZipIntroPack(zipFile)) {
            assertEquals("nested_pack", pack.getId());
            assertEquals("Nested Pack", pack.getName());
            assertTrue(pack.hasIntro());
            assertEquals(1, pack.getIntroFrameCount());
            assertFalse(pack.hasLoop());
            assertFalse(pack.hasAudio());
            assertFalse(pack.hasPanorama());
        }
    }

    @Test
    public void testOptionalSections(@TempDir Path tempDir) throws IOException {
        File zipFile = tempDir.resolve("loop_only.zip").toFile();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addEntry(zos, "intro.json", """
                {
                  "id": "loop_only_pack"
                }
                """);
            addEntry(zos, "loop/frame_0001.jpg", "loop_f1");
        }

        try (ZipIntroPack pack = new ZipIntroPack(zipFile)) {
            assertFalse(pack.hasIntro());
            assertEquals(0, pack.getIntroFrameCount());
            assertTrue(pack.hasLoop());
            assertEquals(1, pack.getLoopFrameCount());
            assertFalse(pack.hasAudio());
            assertFalse(pack.hasPanorama());
            assertFalse(pack.hasPanoramaOverlay());
        }
    }

    @Test
    public void testWindowsBackslashPathsAndIntroPackJson(@TempDir Path tempDir) throws IOException {
        File zipFile = tempDir.resolve("windows_pack.zip").toFile();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addEntry(zos, "intro_pack.json", """
                {
                  "format_version": 1,
                  "id": "windows_pack",
                  "name": "Windows Backslash Pack",
                  "official": true,
                  "fps": 25
                }
                """);
            addEntry(zos, "intro\\frame_0001.jpg", "f1");
            addEntry(zos, "intro\\frame_0002.jpg", "f2");
            addEntry(zos, "loop\\frame_0001.jpg", "l1");
            addEntry(zos, "panorama\\panorama_overlay.png", "overlay");
            addEntry(zos, "audio.ogg", "audio");
        }

        try (ZipIntroPack pack = new ZipIntroPack(zipFile)) {
            assertEquals("windows_pack", pack.getId());
            assertEquals("Windows Backslash Pack", pack.getName());
            assertTrue(pack.isOfficial());
            assertEquals(25, pack.getFps());
            assertTrue(pack.hasIntro());
            assertEquals(2, pack.getIntroFrameCount());
            assertTrue(pack.hasLoop());
            assertEquals(1, pack.getLoopFrameCount());
            assertTrue(pack.hasPanoramaOverlay());
            assertTrue(pack.hasAudio());
        }
    }

    private void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}

