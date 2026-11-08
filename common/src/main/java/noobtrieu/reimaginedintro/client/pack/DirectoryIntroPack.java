package noobtrieu.reimaginedintro.client.pack;

import noobtrieu.reimaginedintro.ReimaginedIntro;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class DirectoryIntroPack implements IntroPack {
    private final Path rootDir;
    private IntroPackManifest manifest;

    private final List<Path> introFramePaths = new ArrayList<>();
    private final List<Path> loopFramePaths = new ArrayList<>();
    private Path audioPath;
    private Path thumbnailPath;
    private Path panoramaOverlayPath;
    private final Path[] panoramaFaces = new Path[6];

    public DirectoryIntroPack(File dir) throws IOException {
        this.rootDir = dir.toPath();
        initialize();
    }

    private void initialize() throws IOException {
        Path manifestPath = rootDir.resolve("intro.json");
        if (Files.exists(manifestPath)) {
            try (var reader = Files.newBufferedReader(manifestPath)) {
                this.manifest = IntroPackManifest.parse(reader);
            } catch (Exception e) {
                ReimaginedIntro.LOGGER.error("Failed to parse intro.json in " + rootDir.getFileName(), e);
            }
        }

        if (this.manifest == null) {
            this.manifest = new IntroPackManifest();
            String name = rootDir.getFileName().toString();
            this.manifest.setId(name.toLowerCase());
            this.manifest.setName(name);
        }

        // Scan intro frames
        Path introDir = rootDir.resolve("intro");
        if (Files.isDirectory(introDir)) {
            try (Stream<Path> stream = Files.list(introDir)) {
                stream.filter(this::isImageFile)
                      .forEach(introFramePaths::add);
            }
            introFramePaths.sort(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));

            Path sound = introDir.resolve("audio.ogg");
            if (Files.exists(sound)) audioPath = sound;
            else {
                sound = introDir.resolve("sound.ogg");
                if (Files.exists(sound)) audioPath = sound;
            }
        }

        if (audioPath == null) {
            Path rootAudio = rootDir.resolve("audio.ogg");
            if (Files.exists(rootAudio)) audioPath = rootAudio;
        }

        // Scan loop frames
        Path loopDir = rootDir.resolve("loop");
        if (Files.isDirectory(loopDir)) {
            try (Stream<Path> stream = Files.list(loopDir)) {
                stream.filter(this::isImageFile)
                      .forEach(loopFramePaths::add);
            }
            loopFramePaths.sort(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        }

        // Thumbnail
        Path thumb = rootDir.resolve("thumbnail.png");
        if (Files.exists(thumb)) {
            thumbnailPath = thumb;
        } else {
            thumb = rootDir.resolve("thumbnail.jpg");
            if (Files.exists(thumb)) {
                thumbnailPath = thumb;
            } else {
                thumb = rootDir.resolve("pack.png");
                if (Files.exists(thumb)) thumbnailPath = thumb;
            }
        }

        // Panorama
        Path panoDir = rootDir.resolve("panorama");
        if (Files.isDirectory(panoDir)) {
            Path overlay = panoDir.resolve("panorama_overlay.png");
            if (Files.exists(overlay)) panoramaOverlayPath = overlay;

            for (int i = 0; i < 6; i++) {
                Path face = panoDir.resolve("panorama_" + i + ".png");
                if (Files.exists(face)) {
                    panoramaFaces[i] = face;
                }
            }
        }
    }

    private boolean isImageFile(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
    }

    @Override
    public IntroPackManifest getManifest() {
        return manifest;
    }

    @Override
    public boolean hasIntro() {
        return !introFramePaths.isEmpty();
    }

    @Override
    public boolean hasLoop() {
        return !loopFramePaths.isEmpty();
    }

    @Override
    public boolean hasAudio() {
        return audioPath != null;
    }

    @Override
    public boolean hasPanorama() {
        for (Path face : panoramaFaces) {
            if (face == null) return false;
        }
        return true;
    }

    @Override
    public boolean hasPanoramaOverlay() {
        return panoramaOverlayPath != null;
    }

    @Override
    public int getIntroFrameCount() {
        return introFramePaths.size();
    }

    @Override
    public int getLoopFrameCount() {
        return loopFramePaths.size();
    }

    @Override
    public InputStream openIntroFrame(int frameIndex) {
        int idx = frameIndex - 1;
        if (idx >= 0 && idx < introFramePaths.size()) {
            return openSafe(introFramePaths.get(idx));
        }
        return null;
    }

    @Override
    public InputStream openLoopFrame(int frameIndex) {
        int idx = frameIndex - 1;
        if (idx >= 0 && idx < loopFramePaths.size()) {
            return openSafe(loopFramePaths.get(idx));
        }
        return null;
    }

    @Override
    public InputStream openAudio() {
        return audioPath != null ? openSafe(audioPath) : null;
    }

    @Override
    public InputStream openThumbnail() {
        return thumbnailPath != null ? openSafe(thumbnailPath) : null;
    }

    @Override
    public InputStream openPanoramaOverlay() {
        return panoramaOverlayPath != null ? openSafe(panoramaOverlayPath) : null;
    }

    @Override
    public InputStream openPanoramaFace(int index) {
        if (index >= 0 && index < 6 && panoramaFaces[index] != null) {
            return openSafe(panoramaFaces[index]);
        }
        return null;
    }

    private InputStream openSafe(Path p) {
        try {
            return Files.newInputStream(p);
        } catch (IOException e) {
            ReimaginedIntro.LOGGER.error("Failed to open file: " + p, e);
            return null;
        }
    }
}
