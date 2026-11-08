package noobtrieu.reimaginedintro.client.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import noobtrieu.reimaginedintro.client.PanoramaOverlayManager;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Panorama.class)
public class PanoramaMixin {
    @Redirect(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIII)V"
        )
    )
    private void fillPanoramaOverlay(
        GuiGraphicsExtractor guiGraphics,
        RenderPipeline pipeline,
        Identifier texture,
        int x,
        int y,
        float uOffset,
        float vOffset,
        int width,
        int height,
        int uWidth,
        int vHeight,
        int textureWidth,
        int textureHeight
    ) {
        IntroPack pack = IntroPackManager.getActivePack();
        if (pack == null || !pack.hasPanoramaOverlay()) {
            guiGraphics.blit(pipeline, texture, x, y, uOffset, vOffset, width, height, uWidth, vHeight, textureWidth, textureHeight);
            return;
        }

        Identifier customOverlay = PanoramaOverlayManager.getOverlayTextureId();
        if (customOverlay == null) {
            guiGraphics.blit(pipeline, texture, x, y, uOffset, vOffset, width, height, uWidth, vHeight, textureWidth, textureHeight);
            return;
        }

        Identifier activeTexture = customOverlay;

        float targetAspect = 16.0f / 9.0f;
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(activeTexture);
        if (tex != null && tex.getTexture() != null) {
            int tw = tex.getTexture().getWidth(0);
            int th = tex.getTexture().getHeight(0);
            if (tw == 16 && th == 128) {
                // Vanilla default 16x128 gradient: stretch as normal
                guiGraphics.blit(pipeline, activeTexture, x, y, uOffset, vOffset, width, height, uWidth, vHeight, textureWidth, textureHeight);
                return;
            }
            if (tw > 0 && th > 0) {
                targetAspect = (float) tw / (float) th;
            }
        }

        float screenAspect = (float) width / (float) height;
        int renderWidth, renderHeight, renderX, renderY;

        if (screenAspect > targetAspect) {
            // Screen is wider than texture -> match width, crop top/bottom
            renderWidth = width;
            renderHeight = (int) (width / targetAspect);
            renderX = 0;
            renderY = (height - renderHeight) / 2;
        } else {
            // Screen is taller than texture -> match height, crop left/right
            renderHeight = height;
            renderWidth = (int) (height * targetAspect);
            renderX = (width - renderWidth) / 2;
            renderY = 0;
        }

        guiGraphics.blit(
            activeTexture,
            renderX,
            renderY,
            renderX + renderWidth,
            renderY + renderHeight,
            0.0f,
            1.0f,
            0.0f,
            1.0f
        );
    }
}
