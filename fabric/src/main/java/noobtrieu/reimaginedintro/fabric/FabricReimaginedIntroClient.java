package noobtrieu.reimaginedintro.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import noobtrieu.reimaginedintro.ReimaginedIntro;

public class FabricReimaginedIntroClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("fabric-key-mapping-api-v1")) {
            FabricKeybindsCompat.register();
        } else {
            ReimaginedIntro.LOGGER.warn("[ReimaginedIntro] fabric-key-mapping-api-v1 is not installed; Options > Key Binds entries are disabled, defaults (F6/F7/F8) remain active.");
        }
        ReimaginedIntro.init();
    }
}
