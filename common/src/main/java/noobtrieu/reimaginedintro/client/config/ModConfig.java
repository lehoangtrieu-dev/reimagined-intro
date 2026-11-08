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
    public boolean enableIntro = true;
    public float introVolume = 1.0f;
    public boolean enableTitleScreenLoop = true;
    public boolean showSkipPrompt = true;
    public boolean ignoreLegacyWarning = false;
    public String selectedPack = "mojang_reimagined";

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
