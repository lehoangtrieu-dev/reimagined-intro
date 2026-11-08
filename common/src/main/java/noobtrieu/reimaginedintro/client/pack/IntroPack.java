package noobtrieu.reimaginedintro.client.pack;

import java.io.Closeable;
import java.io.InputStream;

public interface IntroPack extends Closeable {
    IntroPackManifest getManifest();

    default String getId() {
        return getManifest() != null ? getManifest().getId() : "unknown";
    }

    default String getName() {
        return getManifest() != null ? getManifest().getName() : "Unknown Intro";
    }

    default boolean isOfficial() {
        return getManifest() != null && getManifest().isOfficial();
    }

    default int getFps() {
        return getManifest() != null ? getManifest().getFps() : 30;
    }

    default long getFrameDurationMs() {
        return Math.max(1L, 1000L / Math.max(1, getFps()));
    }

    default long getIntroDurationMs() {
        return getIntroFrameCount() * getFrameDurationMs();
    }

    boolean hasIntro();

    boolean hasLoop();

    boolean hasAudio();

    boolean hasPanorama();

    boolean hasPanoramaOverlay();

    int getIntroFrameCount();

    int getLoopFrameCount();

    InputStream openIntroFrame(int frameIndex);

    InputStream openLoopFrame(int frameIndex);

    InputStream openAudio();

    InputStream openThumbnail();

    InputStream openPanoramaOverlay();

    InputStream openPanoramaFace(int index);

    @Override
    default void close() {}
}

