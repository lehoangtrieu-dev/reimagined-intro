package noobtrieu.reimaginedintro.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.ModKeybinds;

public final class NeoForgeClientKeybinds {
    private NeoForgeClientKeybinds() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(RegisterKeyMappingsEvent.class, NeoForgeClientKeybinds::onRegisterKeyMappings);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(ModKeybinds.CATEGORY);
        ModKeybinds.registerAll(event::register);
        ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Registered key mappings (NeoForge).");
    }
}
