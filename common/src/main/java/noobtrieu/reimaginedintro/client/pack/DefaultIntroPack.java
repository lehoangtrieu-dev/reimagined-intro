package noobtrieu.reimaginedintro.client.pack;

import noobtrieu.reimaginedintro.ReimaginedIntro;

import java.io.InputStream;

public class DefaultIntroPack implements IntroPack {
    public static final String DEFAULT_ID = "mojang_reimagined";
    private final IntroPackManifest manifest;

    public DefaultIntroPack() {
        this.manifest = new IntroPackManifest();
        this.manifest.setId(DEFAULT_ID);
        this.manifest.setName("Reimagined Intro");
        this.manifest.setAuthor("NoobTrieu");
        this.manifest.setVersion("1.0.0");
        this.manifest.setDescription("The default look and feel of the Reimagined Intro you know and love.");
        this.manifest.setOfficial(true);
        this.manifest.setFps(25);
    }

    @Override
    public IntroPackManifest getManifest() {
        return manifest;
    }

    @Override
    public int getFps() {
        return 25; // 40ms per frame
    }

    @Override
    public boolean hasIntro() {
        return true;
    }

    @Override
    public boolean hasLoop() {
        return true;
    }

    @Override
    public boolean hasAudio() {
        return true;
    }

    @Override
    public boolean hasPanorama() {
        return false;
    }

    @Override
    public boolean hasPanoramaOverlay() {
        return true;
    }

    @Override
    public int getIntroFrameCount() {
        return 161;
    }

    @Override
    public int getLoopFrameCount() {
        return 238;
    }

    @Override
    public InputStream openIntroFrame(int frameIndex) {
        String jpg = String.format("/assets/reimagined-intro/textures/intro/frame_%04d.jpg", frameIndex);
        InputStream stream = ReimaginedIntro.class.getResourceAsStream(jpg);
        if (stream == null) {
            String png = String.format("/assets/reimagined-intro/textures/intro/frame_%04d.png", frameIndex);
            stream = ReimaginedIntro.class.getResourceAsStream(png);
        }
        return stream;
    }

    @Override
    public InputStream openLoopFrame(int frameIndex) {
        String jpg = String.format("/assets/reimagined-intro/textures/loop/frame_%04d.jpg", frameIndex);
        InputStream stream = ReimaginedIntro.class.getResourceAsStream(jpg);
        if (stream == null) {
            String png = String.format("/assets/reimagined-intro/textures/loop/frame_%04d.png", frameIndex);
            stream = ReimaginedIntro.class.getResourceAsStream(png);
        }
        return stream;
    }

    @Override
    public InputStream openAudio() {
        return ReimaginedIntro.class.getResourceAsStream("/assets/reimagined-intro/sounds/intro.ogg");
    }

    @Override
    public InputStream openThumbnail() {
        InputStream stream = ReimaginedIntro.class.getResourceAsStream("/assets/reimagined-intro/textures/gui/panorama_overlay.png");
        if (stream == null) {
            stream = ReimaginedIntro.class.getResourceAsStream("/assets/reimagined-intro/textures/intro/frame_0161.jpg");
        }
        if (stream == null) {
            stream = ReimaginedIntro.class.getResourceAsStream("/assets/reimagined-intro/icon.png");
        }
        return stream;
    }

    @Override
    public InputStream openPanoramaOverlay() {
        return ReimaginedIntro.class.getResourceAsStream("/assets/reimagined-intro/textures/gui/panorama_overlay.png");
    }

    @Override
    public InputStream openPanoramaFace(int index) {
        return null;
    }
}
