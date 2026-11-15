package noobtrieu.reimaginedintro.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import noobtrieu.reimaginedintro.ReimaginedIntro;

import java.util.List;
import java.util.function.Consumer;

public final class ModKeybinds {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(ReimaginedIntro.id("general"));

    public static final KeyMapping REPLAY_INTRO = new KeyMapping(
        "key.reimagined-intro.replay_intro",
        InputConstants.Type.KEYBOARD,
        InputConstants.KEY_F6,
        CATEGORY,
        1
    );

    public static final KeyMapping OPEN_OPTIONS = new KeyMapping(
        "key.reimagined-intro.open_options",
        InputConstants.Type.KEYBOARD,
        InputConstants.KEY_F7,
        CATEGORY,
        2
    );

    public static final KeyMapping OPEN_PACKS = new KeyMapping(
        "key.reimagined-intro.open_packs",
        InputConstants.Type.KEYBOARD,
        InputConstants.KEY_F8,
        CATEGORY,
        3
    );

    private static final List<KeyMapping> ALL = List.of(
        REPLAY_INTRO,
        OPEN_OPTIONS,
        OPEN_PACKS
    );

    private ModKeybinds() {
    }

    public static void registerAll(Consumer<KeyMapping> registrar) {
        ALL.forEach(registrar);
    }

    public static Component getOpenOptionsKeyDisplay() {
        return OPEN_OPTIONS.getTranslatedKeyMessage();
    }
}
