package noobtrieu.reimaginedintro.client.pack;

import noobtrieu.reimaginedintro.ReimaginedIntro;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipIntroPack implements IntroPack {
    private final File zipFileOnDisk;
    private ZipFile zipFile;
    private IntroPackManifest manifest;

    private final List<String> introFrameEntries = new ArrayList<>();
    private final List<String> loopFrameEntries = new ArrayList<>();
    private String audioEntry;
    private String thumbnailEntry;
    private String panoramaOverlayEntry;
    private final String[] panoramaFaces = new String[6];

    public ZipIntroPack(File file) throws IOException {
        this.zipFileOnDisk = file;
        this.zipFile = new ZipFile(file, StandardCharsets.UTF_8);
        try {
            initialize();
        } catch (Throwable t) {
            close();
            if (t instanceof IOException e) throw e;
            throw new IOException("Failed to initialize zip intro pack: " + file.getName(), t);
        }
    }

    private void initialize() throws IOException {
        String basePrefix = "";

        // Find intro.json to determine basePrefix if wrapped inside a subfolder
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith("intro.json")) {
                basePrefix = name.substring(0, name.length() - "intro.json".length());
                try (InputStream is = zipFile.getInputStream(entry);
                     InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    this.manifest = IntroPackManifest.parse(reader);
                } catch (Exception e) {
                    ReimaginedIntro.LOGGER.error("Failed to parse intro.json in " + zipFileOnDisk.getName(), e);
                }
                break;
            }
        }

        if (this.manifest == null) {
            this.manifest = new IntroPackManifest();
            String rawName = zipFileOnDisk.getName();
            int dot = rawName.lastIndexOf('.');
            String id = (dot != -1 ? rawName.substring(0, dot) : rawName).toLowerCase();
            this.manifest.setId(id);
            this.manifest.setName(id);
        }

        // Collect frames and assets matching basePrefix
        entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;

            String name = entry.getName();
            if (!basePrefix.isEmpty() && !name.startsWith(basePrefix)) continue;
            String rel = name.substring(basePrefix.length());

            if (rel.startsWith("intro/") && isImageFile(rel)) {
                introFrameEntries.add(name);
            } else if (rel.startsWith("loop/") && isImageFile(rel)) {
                loopFrameEntries.add(name);
            } else if (rel.equalsIgnoreCase("intro/audio.ogg") || rel.equalsIgnoreCase("intro/sound.ogg") || rel.equalsIgnoreCase("audio.ogg")) {
                audioEntry = name;
            } else if (rel.equalsIgnoreCase("thumbnail.png")) {
                thumbnailEntry = name;
            } else if (thumbnailEntry == null && (rel.equalsIgnoreCase("thumbnail.jpg") || rel.equalsIgnoreCase("pack.png"))) {
                thumbnailEntry = name;
            } else if (rel.equalsIgnoreCase("panorama/panorama_overlay.png")) {
                panoramaOverlayEntry = name;
            } else {
                for (int i = 0; i < 6; i++) {
                    if (rel.equalsIgnoreCase("panorama/panorama_" + i + ".png")) {
                        panoramaFaces[i] = name;
                        break;
                    }
                }
            }
        }

        // Sort frames in natural alphanumeric order
        introFrameEntries.sort(String.CASE_INSENSITIVE_ORDER);
        loopFrameEntries.sort(String.CASE_INSENSITIVE_ORDER);
    }

    private static boolean isImageFile(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
    }

    @Override
    public IntroPackManifest getManifest() {
        return manifest;
    }

    @Override
    public boolean hasIntro() {
        return !introFrameEntries.isEmpty();
    }

    @Override
    public boolean hasLoop() {
        return !loopFrameEntries.isEmpty();
    }

    @Override
    public boolean hasAudio() {
        return audioEntry != null;
    }

    @Override
    public boolean hasPanorama() {
        for (String face : panoramaFaces) {
            if (face == null) return false;
        }
        return true;
    }

    @Override
    public boolean hasPanoramaOverlay() {
        return panoramaOverlayEntry != null;
    }

    @Override
    public int getIntroFrameCount() {
        return introFrameEntries.size();
    }

    @Override
    public int getLoopFrameCount() {
        return loopFrameEntries.size();
    }

    @Override
    public synchronized InputStream openIntroFrame(int frameIndex) {
        int idx = frameIndex - 1;
        if (idx >= 0 && idx < introFrameEntries.size()) {
            return openEntry(introFrameEntries.get(idx));
        }
        return null;
    }

    @Override
    public synchronized InputStream openLoopFrame(int frameIndex) {
        int idx = frameIndex - 1;
        if (idx >= 0 && idx < loopFrameEntries.size()) {
            return openEntry(loopFrameEntries.get(idx));
        }
        return null;
    }

    @Override
    public synchronized InputStream openAudio() {
        return audioEntry != null ? openEntry(audioEntry) : null;
    }

    @Override
    public synchronized InputStream openThumbnail() {
        return thumbnailEntry != null ? openEntry(thumbnailEntry) : null;
    }

    @Override
    public synchronized InputStream openPanoramaOverlay() {
        return panoramaOverlayEntry != null ? openEntry(panoramaOverlayEntry) : null;
    }

    @Override
    public synchronized InputStream openPanoramaFace(int index) {
        if (index >= 0 && index < 6 && panoramaFaces[index] != null) {
            return openEntry(panoramaFaces[index]);
        }
        return null;
    }

    private InputStream openEntry(String entryName) {
        try {
            ensureZipOpen();
            ZipEntry entry = zipFile.getEntry(entryName);
            return entry != null ? zipFile.getInputStream(entry) : null;
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.error("Failed to read entry " + entryName + " from " + zipFileOnDisk.getName(), e);
            return null;
        }
    }

    private void ensureZipOpen() throws IOException {
        if (zipFile == null) {
            zipFile = new ZipFile(zipFileOnDisk, StandardCharsets.UTF_8);
        }
    }

    @Override
    public synchronized void close() {
        if (zipFile != null) {
            try {
                zipFile.close();
            } catch (IOException ignored) {}
            zipFile = null;
        }
    }
}
