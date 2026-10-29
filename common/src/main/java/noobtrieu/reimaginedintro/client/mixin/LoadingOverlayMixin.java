package noobtrieu.reimaginedintro.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.ResourceLocation;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @WrapWithCondition(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"
        )
    )
    private boolean shouldRenderMojangLogo(
        GuiGraphics instance,
        ResourceLocation texture,
        int x1,
        int x2,
        int y1,
        int y2,
        float u0,
        float u1,
        int uWidth,
        int vHeight,
        int textureWidth,
        int textureHeight
    ) {
        // Suppress Mojang logo on splash screen when custom intro is enabled
        return !ModConfig.get().enableIntro;
    }
}
