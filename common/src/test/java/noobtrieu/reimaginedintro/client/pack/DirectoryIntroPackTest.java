package noobtrieu.reimaginedintro.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DirectoryIntroPackTest {

    @Test
    public void testDirectoryIntroPack(@TempDir Path tempDir) throws IOException {
        Path packDir = tempDir.resolve("my_folder_pack");
        Files.createDirectories(packDir);

        // Manifest
        Files.writeString(packDir.resolve("intro.json"), """
            {
              "id": "folder_pack",
              "name": "Folder Pack",
              "author": "CommunityDev",
              "fps": 24
            }
            """);

        // Thumbnail
        Files.writeString(packDir.resolve("thumbnail.png"), "thumb_data");

        // Intro
        Path introDir = packDir.resolve("intro");
        Files.createDirectories(introDir);
        Files.writeString(introDir.resolve("frame_0002.png"), "d2");
        Files.writeString(introDir.resolve("frame_0001.png"), "d1");
        Files.writeString(introDir.resolve("audio.ogg"), "audio_dir");

        // Panorama
        Path panoDir = packDir.resolve("panorama");
        Files.createDirectories(panoDir);
        Files.writeString(panoDir.resolve("panorama_overlay.png"), "pano_overlay");

        try (DirectoryIntroPack pack = new DirectoryIntroPack(packDir.toFile())) {
            assertEquals("folder_pack", pack.getId());
            assertEquals("Folder Pack", pack.getName());
            assertEquals(24, pack.getFps());
            assertTrue(pack.hasIntro());
            assertEquals(2, pack.getIntroFrameCount());
            assertFalse(pack.hasLoop());
            assertTrue(pack.hasAudio());
            assertTrue(pack.hasPanoramaOverlay());
            assertFalse(pack.hasPanorama()); // Not all 6 faces exist

            try (InputStream is = pack.openIntroFrame(1)) {
                assertNotNull(is);
                assertEquals("d1", new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (InputStream is = pack.openIntroFrame(2)) {
                assertNotNull(is);
                assertEquals("d2", new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (InputStream is = pack.openThumbnail()) {
                assertNotNull(is);
                assertEquals("thumb_data", new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}

