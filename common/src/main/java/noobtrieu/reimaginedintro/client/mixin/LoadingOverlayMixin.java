package noobtrieu.reimaginedintro.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import noobtrieu.reimaginedintro.client.IntroManager;
import noobtrieu.reimaginedintro.client.IntroScreen;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    private static final long INTRO_BAR_FADE_MS = 250L;

    @Shadow @Final private Minecraft minecraft;
    @Shadow protected long fadeOutStart;
    @Shadow private float currentProgress;
    @Unique private long reimaginedIntro$handoffStartMs = -1L;

    private static boolean shouldSuppressMojangLogo() {
        return ModConfig.get().enableIntro && IntroPackManager.getActivePack().hasIntro();
    }

    @Unique
    private boolean reimaginedIntro$isHandingOffToIntro() {
        return this.fadeOutStart > -1L
            && this.minecraft.gui.screen() instanceof IntroScreen
            && shouldSuppressMojangLogo();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void handOffToIntroWhenBarFaded(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (!this.reimaginedIntro$isHandingOffToIntro()) {
            return;
        }
        long now = Util.getMillis();
        if (this.reimaginedIntro$handoffStartMs == -1L) {
            this.reimaginedIntro$handoffStartMs = now;
        }
        if (now - this.reimaginedIntro$handoffStartMs >= INTRO_BAR_FADE_MS) {
            this.minecraft.gui.setOverlay(null);
            IntroManager.startIntro();
            Screen current = this.minecraft.gui.screen();
            if (current != null) {
                current.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, a);
            }
            ci.cancel();
        }
    }

    @WrapWithCondition(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"
        )
    )
    private boolean shouldRenderMojangLogo(
        GuiGraphicsExtractor instance,
        RenderPipeline pipeline,
        Identifier texture,
        int x0,
        int y0,
        float u0,
        float v0,
        int width,
        int height,
        int uWidth,
        int vHeight,
        int textureWidth,
        int textureHeight,
        int tintColor
    ) {
        // Suppress Mojang logo on splash screen when custom intro is enabled and pack has intro
        return !shouldSuppressMojangLogo();
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;extractProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"
        )
    )
    private void centerProgressBarWhenLogoHidden(
        LoadingOverlay instance,
        GuiGraphicsExtractor graphics,
        int x0,
        int y0,
        int x1,
        int y1,
        float fade,
        Operation<Void> original
    ) {
        if (this.reimaginedIntro$isHandingOffToIntro()) {
            long start = this.reimaginedIntro$handoffStartMs > -1L ? this.reimaginedIntro$handoffStartMs : Util.getMillis();
            float t = Mth.clamp((float) (Util.getMillis() - start) / (float) INTRO_BAR_FADE_MS, 0.0F, 1.0F);
            // Smoothly finish filling the bar and fade it out cleanly over INTRO_BAR_FADE_MS
            this.currentProgress = Math.max(this.currentProgress, Mth.clamp(t * 2.5F, 0.0F, 1.0F));
            fade = (1.0F - t) * (1.0F - t);
        }
        if (shouldSuppressMojangLogo()) {
            int barHeight = y1 - y0;
            int centerY = graphics.guiHeight() / 2;
            y0 = centerY - barHeight / 2;
            y1 = y0 + barHeight;
        }
        original.call(instance, graphics, x0, y0, x1, y1, fade);
    }
}
