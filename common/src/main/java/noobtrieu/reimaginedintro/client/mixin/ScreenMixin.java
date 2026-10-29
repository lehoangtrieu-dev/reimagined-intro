package noobtrieu.reimaginedintro.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import noobtrieu.reimaginedintro.client.IntroManager;
import noobtrieu.reimaginedintro.client.IntroScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof TitleScreen) {
            if (keyCode == InputConstants.KEY_F6) {
                IntroManager.resetForReplay();
                Minecraft.getInstance().setScreen(new IntroScreen());
                cir.setReturnValue(true);
            } else if (keyCode == InputConstants.KEY_F7) {
                Minecraft.getInstance().setScreen(new noobtrieu.reimaginedintro.client.config.ModConfigScreen((Screen) (Object) this));
                cir.setReturnValue(true);
            } else if (keyCode == InputConstants.KEY_ESCAPE && noobtrieu.reimaginedintro.client.IntroSoundController.isPlaying()) {
                noobtrieu.reimaginedintro.client.IntroSoundController.stop();
                cir.setReturnValue(true);
            }
        }
    }
}
