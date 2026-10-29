package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PanoramaRenderer.class)
public class PanoramaMixin {
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"
        )
    )
    private void fillPanoramaOverlay(
        GuiGraphics guiGraphics,
        ResourceLocation texture,
        int x,
        int y,
        int width,
        int height,
        float uOffset,
        float vOffset,
        int uWidth,
        int vHeight,
        int textureWidth,
        int textureHeight
    ) {
        float targetAspect = 16.0f / 9.0f;
        int tw = 16;
        int th = 128;
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (tex != null) {
            tex.bind();
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w == 16 && h == 128) {
                // Vanilla default 16x128 gradient: stretch as normal
                guiGraphics.blit(texture, x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
                return;
            }
            if (w > 0 && h > 0) {
                tw = w;
                th = h;
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
            texture,
            renderX,
            renderY,
            renderWidth,
            renderHeight,
            0.0f,
            0.0f,
            tw,
            th,
            tw,
            th
        );
    }
}
