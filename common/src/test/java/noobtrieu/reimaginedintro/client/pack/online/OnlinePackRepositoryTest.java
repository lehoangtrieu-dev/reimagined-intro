package noobtrieu.reimaginedintro.client.pack.online;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlinePackRepositoryTest {

    @Test
    void parseRegistrySupportsObjectWrapperAndFiltersInvalidEntries() {
        String json = """
                {
                  "format_version": 1,
                  "packs": [
                    {
                      "id": "Dungeon Intro!",
                      "name": "Dungeon Intro",
                      "author": "NoobTrieu",
                      "version": "1.2.0",
                      "description": "Animated dungeon reveal",
                      "fps": 30,
                      "official": true,
                      "has_intro": true,
                      "has_loop": true,
                      "has_audio": true,
                      "has_panorama": false,
                      "has_panorama_overlay": false,
                      "thumbnail_url": "https://example.com/thumb.png",
                      "download_url": "https://github.com/user/repo/releases/download/v1/dungeon.zip",
                      "file_size": 1048576
                    },
                    {
                      "id": "missing_url",
                      "name": "Missing URL"
                    },
                    {
                      "id": "local_url",
                      "name": "Local File URL",
                      "download_url": "file:///C:/temp/pack.zip"
                    }
                  ]
                }
                """;

        List<RemoteIntroPack> packs = RemoteIntroPack.parseRegistry(new StringReader(json));
        assertEquals(1, packs.size());

        RemoteIntroPack pack = packs.get(0);
        assertEquals("dungeon_intro_", pack.getId());
        assertEquals("Dungeon Intro", pack.getName());
        assertEquals("NoobTrieu", pack.getAuthor());
        assertEquals("1.2.0", pack.getVersion());
        assertEquals(30, pack.getFps());
        assertTrue(pack.isOfficial());
        assertTrue(pack.hasIntro());
        assertTrue(pack.hasLoop());
        assertTrue(pack.hasAudio());
        assertFalse(pack.hasPanorama());
        assertEquals(1048576L, pack.getFileSizeBytes());
    }

    @Test
    void parseRegistrySupportsBareArray() {
        String json = """
                [
                  {
                    "id": "bare_pack",
                    "name": "Bare Pack",
                    "download_url": "https://example.com/bare_pack.zip"
                  }
                ]
                """;

        List<RemoteIntroPack> packs = RemoteIntroPack.parseRegistry(new StringReader(json));
        assertEquals(1, packs.size());
        assertEquals("bare_pack", packs.get(0).getId());
        assertEquals("Bare Pack", packs.get(0).getName());
    }

    @Test
    void isNewerVersionComparesSemverCorrectly() {
        assertTrue(RemoteIntroPack.isNewerVersion("1.0.0", "1.0.1"));
        assertTrue(RemoteIntroPack.isNewerVersion("v1.0.0", "1.1.0"));
        assertTrue(RemoteIntroPack.isNewerVersion("1.2", "1.2.1"));
        assertFalse(RemoteIntroPack.isNewerVersion("1.2.0", "1.2.0"));
        assertFalse(RemoteIntroPack.isNewerVersion("2.0.0", "1.9.9"));
        assertFalse(RemoteIntroPack.isNewerVersion("1.0.0", ""));
    }

    @Test
    void formatFileSizeFormatsUnitsCleanly() {
        assertEquals("", RemoteIntroPack.formatFileSize(0));
        assertEquals("512 B", RemoteIntroPack.formatFileSize(512));
        assertEquals("2 KB", RemoteIntroPack.formatFileSize(1536));
        assertEquals("2.5 MB", RemoteIntroPack.formatFileSize(2621440));
    }

    @Test
    void normalizeUserUrlConvertsGitHubBlobLinks() {
        String blob = "https://github.com/lehoangtrieu-dev/reimagined-intro/blob/master/packs/registry.json";
        String expected = "https://raw.githubusercontent.com/lehoangtrieu-dev/reimagined-intro/master/packs/registry.json";
        assertEquals(expected, OnlinePackRepository.normalizeUserUrl(blob));

        String release = "https://github.com/lehoangtrieu-dev/reimagined-intro/releases/download/v1/pack.zip";
        assertEquals(release, OnlinePackRepository.normalizeUserUrl(release));
    }

    @Test
    void validateHttpUriAcceptsHttpAndRejectsUnsafeSchemes() throws IOException {
        URI https = OnlinePackRepository.validateHttpUri("https://example.com/pack.zip");
        assertEquals("https", https.getScheme());

        URI http = OnlinePackRepository.validateHttpUri("http://example.com/pack.zip");
        assertEquals("http", http.getScheme());

        assertThrows(IOException.class, () -> OnlinePackRepository.validateHttpUri(""));
        assertThrows(IOException.class, () -> OnlinePackRepository.validateHttpUri("file:///C:/pack.zip"));
        assertThrows(IOException.class, () -> OnlinePackRepository.validateHttpUri("ftp://example.com/pack.zip"));
    }

    @Test
    void inferAndSanitizeZipFileNameAlwaysEndsInZip() {
        assertEquals("my_pack.zip", OnlinePackRepository.inferZipFileName("https://example.com/files/my_pack.zip?raw=true"));
        assertEquals("custom_archive.zip", OnlinePackRepository.inferZipFileName("https://example.com/files/custom_archive"));
        String fallbackName = OnlinePackRepository.inferZipFileName("https://example.com/");
        assertTrue(fallbackName.startsWith("downloaded_intro_") && fallbackName.endsWith(".zip"));
        assertEquals("weird_name_.zip", OnlinePackRepository.sanitizeZipFileName("weird:name?.zip"));
    }

    @Test
    void validateZipFileAcceptsValidZipAndRejectsCorruptFiles(@TempDir Path tempDir) throws IOException {
        Path validZip = tempDir.resolve("valid.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(validZip))) {
            zos.putNextEntry(new ZipEntry("pack.json"));
            zos.write("{\"name\":\"Test\"}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        OnlinePackRepository.validateZipFile(validZip);

        Path emptyFile = tempDir.resolve("empty.zip");
        Files.createFile(emptyFile);
        assertThrows(IOException.class, () -> OnlinePackRepository.validateZipFile(emptyFile));

        Path corruptFile = tempDir.resolve("corrupt.zip");
        Files.writeString(corruptFile, "not a zip archive");
        assertThrows(IOException.class, () -> OnlinePackRepository.validateZipFile(corruptFile));
    }
}
