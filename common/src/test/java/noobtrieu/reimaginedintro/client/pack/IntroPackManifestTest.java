package noobtrieu.reimaginedintro.client.pack;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

public class IntroPackManifestTest {

    @Test
    public void testParseFullManifest() {
        String json = """
            {
              "format_version": 1,
              "id": "retro_gaming",
              "name": "Retro Gaming Intro",
              "author": "PixelArtist",
              "version": "2.1.0",
              "official": true,
              "description": "An 8-bit style intro pack",
              "fps": 60
            }
            """;

        IntroPackManifest manifest = IntroPackManifest.parse(new StringReader(json));
        assertNotNull(manifest);
        assertEquals(1, manifest.getFormatVersion());
        assertEquals("retro_gaming", manifest.getId());
        assertEquals("Retro Gaming Intro", manifest.getName());
        assertEquals("PixelArtist", manifest.getAuthor());
        assertEquals("2.1.0", manifest.getVersion());
        assertTrue(manifest.isOfficial());
        assertEquals("An 8-bit style intro pack", manifest.getDescription());
        assertEquals(60, manifest.getFps());
    }

    @Test
    public void testParseMinimalManifestWithDefaults() {
        String json = """
            {
              "id": "Minimal Intro Pack!"
            }
            """;

        IntroPackManifest manifest = IntroPackManifest.parse(new StringReader(json));
        assertNotNull(manifest);
        assertEquals(1, manifest.getFormatVersion());
        assertEquals("minimal_intro_pack_", manifest.getId()); // Sanitized
        assertEquals("minimal_intro_pack_", manifest.getName());
        assertEquals("Unknown", manifest.getAuthor());
        assertEquals("1.0.0", manifest.getVersion());
        assertFalse(manifest.isOfficial());
        assertEquals(30, manifest.getFps()); // Default 30 FPS
    }

    @Test
    public void testInvalidFpsClamped() {
        String json = """
            {
              "id": "fast_intro",
              "fps": 300
            }
            """;

        IntroPackManifest manifest = IntroPackManifest.parse(new StringReader(json));
        assertNotNull(manifest);
        assertEquals(30, manifest.getFps()); // Clamped from >120 to default 30
    }
}

