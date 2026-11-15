package noobtrieu.reimaginedintro.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import noobtrieu.reimaginedintro.client.pack.gui.IntroPackSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToContents(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"
        )
    )
    private void addReimaginedIntroOptionsButton(CallbackInfo ci, @Local GridLayout.RowHelper helper) {
        if (!ModConfig.get().showOptionsButton) {
            return;
        }
        Button button = Button.builder(
            Component.literal("Reimagined Intro..."),
            b -> Minecraft.getInstance().gui.setScreen(IntroPackSelectionScreen.marketplace(this))
        ).width(308).build();
        helper.addChild(button, 2);
    }
}

