package noobtrieu.reimaginedintro.fabric;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.ModKeybinds;

public final class FabricKeybindsCompat {
    private FabricKeybindsCompat() {
    }

    public static void register() {
        KeyMapping.Category.register(ModKeybinds.CATEGORY.id());
        ModKeybinds.registerAll(KeyMappingHelper::registerKeyMapping);
        ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Registered key mappings (Fabric).");
    }
}
