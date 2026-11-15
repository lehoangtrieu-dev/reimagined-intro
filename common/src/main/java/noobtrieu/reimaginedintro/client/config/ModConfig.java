package noobtrieu.reimaginedintro.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.platform.Services;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    public static final String DEFAULT_REGISTRY_URL = "https://raw.githubusercontent.com/lehoangtrieu-dev/reimagined-intro/master/packs/registry.json";
    private static final String LEGACY_MAIN_REGISTRY_URL = "https://raw.githubusercontent.com/lehoangtrieu-dev/reimagined-intro/main/packs/registry.json";
    private static final String LEGACY_V4_DEV_REGISTRY_URL = "https://raw.githubusercontent.com/lehoangtrieu-dev/reimagined-intro/v4-dev/packs/registry.json";

    public boolean enableIntro = true;
    public float introVolume = 1.0f;
    public boolean enableTitleScreenLoop = true;
    public boolean showSkipPrompt = true;
    public boolean showOptionsButton = true;
    public boolean ignoreLegacyWarning = false;
    public String selectedPack = "mojang_reimagined";
    public String packRegistryUrl = DEFAULT_REGISTRY_URL;
    public String videoResolution = VideoResolution.ORIGINAL.getId();
    public String videoFps = VideoFps.ORIGINAL.getId();

    public VideoResolution getVideoResolution() {
        return VideoResolution.fromId(this.videoResolution);
    }

    public void setVideoResolution(VideoResolution resolution) {
        this.videoResolution = (resolution != null ? resolution : VideoResolution.ORIGINAL).getId();
    }

    public VideoFps getVideoFps() {
        return VideoFps.fromId(this.videoFps);
    }

    public void setVideoFps(VideoFps fps) {
        this.videoFps = (fps != null ? fps : VideoFps.ORIGINAL).getId();
    }

    public String getEffectiveRegistryUrl() {
        if (packRegistryUrl == null || packRegistryUrl.trim().isEmpty()) {
            return DEFAULT_REGISTRY_URL;
        }
        String trimmed = packRegistryUrl.trim();
        if (LEGACY_MAIN_REGISTRY_URL.equals(trimmed) || LEGACY_V4_DEV_REGISTRY_URL.equals(trimmed)) {
            this.packRegistryUrl = DEFAULT_REGISTRY_URL;
            return DEFAULT_REGISTRY_URL;
        }
        return trimmed;
    }

    private static ModConfig INSTANCE;
    private static final Path CONFIG_FILE = Services.PLATFORM.getConfigDir().resolve("reimagined-intro.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ModConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                ReimaginedIntro.LOGGER.error("Failed to load config file, falling back to defaults", e);
            }
        }
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(get(), writer);
            }
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.error("Failed to save config file", e);
        }
    }
}
