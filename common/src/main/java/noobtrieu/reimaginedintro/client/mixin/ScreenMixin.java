package noobtrieu.reimaginedintro.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import noobtrieu.reimaginedintro.client.IntroManager;
import noobtrieu.reimaginedintro.client.IntroScreen;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof TitleScreen) {
            if (event.key() == InputConstants.KEY_F6) {
                if (IntroPackManager.getActivePack().hasIntro()) {
                    IntroManager.resetForReplay();
                    Minecraft.getInstance().gui.setScreen(new IntroScreen());
                    cir.setReturnValue(true);
                }
            } else if (event.key() == InputConstants.KEY_F7) {
                Minecraft.getInstance().gui.setScreen(new noobtrieu.reimaginedintro.client.config.ModConfigScreen((Screen) (Object) this));
                cir.setReturnValue(true);
            } else if (event.key() == InputConstants.KEY_ESCAPE && noobtrieu.reimaginedintro.client.IntroSoundController.isPlaying()) {
                noobtrieu.reimaginedintro.client.IntroSoundController.stop();
                cir.setReturnValue(true);
            }
        }
    }
}
