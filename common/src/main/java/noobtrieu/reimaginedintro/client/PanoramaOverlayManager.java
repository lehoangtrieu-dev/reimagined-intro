package noobtrieu.reimaginedintro.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;

import java.io.InputStream;
import java.util.Objects;

public class PanoramaOverlayManager {
    public static final Identifier OVERLAY_ID = ReimaginedIntro.id("active_panorama_overlay");
    private static DynamicTexture dynamicTexture;
    private static String loadedPackId = null;

    public static Identifier getOverlayTextureId() {
        IntroPack pack = IntroPackManager.getActivePack();
        if (pack == null || !pack.hasPanoramaOverlay()) {
            return null;
        }

        String packId = pack.getId();
        if (dynamicTexture == null || !Objects.equals(packId, loadedPackId)) {
            reload(pack);
        }

        return dynamicTexture != null ? OVERLAY_ID : null;
    }

    public static synchronized void reload(IntroPack pack) {
        cleanup();
        if (pack == null || !pack.hasPanoramaOverlay()) {
            return;
        }

        try (InputStream is = pack.openPanoramaOverlay()) {
            if (is != null) {
                NativeImage img = NativeImageDecoder.decode(is);
                if (img != null) {
                    dynamicTexture = new SmoothDynamicTexture("panorama_overlay", img.getWidth(), img.getHeight(), false);
                    dynamicTexture.setPixels(img);
                    dynamicTexture.upload();
                    Minecraft.getInstance().getTextureManager().register(OVERLAY_ID, dynamicTexture);
                    loadedPackId = pack.getId();
                }
            }
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.error("Failed to load custom panorama overlay from pack: " + pack.getName(), e);
            cleanup();
        }
    }

    public static synchronized void cleanup() {
        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
        loadedPackId = null;
    }
}

