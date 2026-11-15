package noobtrieu.reimaginedintro.fabric.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import noobtrieu.reimaginedintro.client.pack.gui.IntroPackSelectionScreen;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return IntroPackSelectionScreen::marketplace;
    }
}
