package noobtrieu.reimaginedintro.client.pack;

import net.minecraft.client.Minecraft;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.FrameStreamer;
import noobtrieu.reimaginedintro.client.PanoramaOverlayManager;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import noobtrieu.reimaginedintro.platform.Services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class IntroPackManager {
    private static final Map<String, IntroPack> availablePacks = new LinkedHashMap<>();
    private static IntroPack activePack;
    private static final DefaultIntroPack DEFAULT_PACK = new DefaultIntroPack();
    private static boolean initialized = false;

    public static Path getIntrosDir() {
        return Services.PLATFORM.getGameDir().resolve("intros");
    }

    public static synchronized void init() {
        if (initialized) return;
        reloadPacks();
        initialized = true;
    }

    public static synchronized void reloadPacks() {
        // Close previously opened ZIP packs
        for (IntroPack pack : availablePacks.values()) {
            if (pack != DEFAULT_PACK) {
                try {
                    pack.close();
                } catch (Exception ignored) {}
            }
        }
        availablePacks.clear();

        // Always register default bundled intro
        availablePacks.put(DEFAULT_PACK.getId(), DEFAULT_PACK);

        Path dir = getIntrosDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ReimaginedIntro.LOGGER.error("Failed to create intros directory at " + dir, e);
        }

        File[] files = dir.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    IntroPack pack = null;
                    if (file.isDirectory()) {
                        pack = new DirectoryIntroPack(file);
                    } else if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        pack = new ZipIntroPack(file);
                    }

                    if (pack != null) {
                        String id = pack.getId();
                        if (availablePacks.containsKey(id)) {
                            ReimaginedIntro.LOGGER.warn("Duplicate intro pack id: " + id + " from " + file.getName());
                            // Disambiguate id
                            id = id + "_" + System.currentTimeMillis();
                        }
                        availablePacks.put(id, pack);
                        ReimaginedIntro.LOGGER.info("Discovered intro pack: " + pack.getName() + " (" + id + ")");
                    }
                } catch (Exception e) {
                    ReimaginedIntro.LOGGER.error("Failed to load intro pack from " + file.getName(), e);
                }
            }
        }

        // Resolve active pack
        String desiredId = ModConfig.get().selectedPack;
        if (desiredId != null && availablePacks.containsKey(desiredId)) {
            activePack = availablePacks.get(desiredId);
        } else {
            activePack = DEFAULT_PACK;
            ModConfig.get().selectedPack = DEFAULT_PACK.getId();
            ModConfig.save();
        }

        ReimaginedIntro.LOGGER.info("Active intro pack set to: " + activePack.getName() + " (" + activePack.getId() + ")");
    }

    public static synchronized IntroPack getActivePack() {
        if (activePack == null) {
            init();
        }
        return activePack;
    }

    public static synchronized void setActivePack(IntroPack pack) {
        if (pack == null) {
            pack = DEFAULT_PACK;
        }
        if (activePack != pack) {
            FrameStreamer.cleanup();
            PanoramaOverlayManager.cleanup();
            activePack = pack;
            ModConfig.get().selectedPack = pack.getId();
            ModConfig.save();
            ReimaginedIntro.LOGGER.info("Selected active intro pack: " + pack.getName() + " (" + pack.getId() + ")");

            if (pack.hasPanorama()) {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.getResourceManager() != null) {
                        net.minecraft.resources.Identifier panoId = net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/title/background/panorama");
                        net.minecraft.client.renderer.texture.AbstractTexture tex = mc.getTextureManager().getTexture(panoId);
                        if (tex instanceof net.minecraft.client.renderer.texture.CubeMapTexture cubeTex) {
                            net.minecraft.client.renderer.texture.TextureContents contents = cubeTex.loadContents(mc.getResourceManager());
                            cubeTex.apply(contents);
                        }
                    }
                } catch (Throwable t) {
                    ReimaginedIntro.LOGGER.error("Failed to reload cubemap texture for pack: " + pack.getName(), t);
                }
            }
        }
    }

    public static synchronized List<IntroPack> getAvailablePacks() {
        if (!initialized) {
            init();
        }
        return new ArrayList<>(availablePacks.values());
    }

    public static synchronized IntroPack getPack(String id) {
        if (!initialized) {
            init();
        }
        return availablePacks.get(id);
    }
}
