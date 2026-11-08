package noobtrieu.reimaginedintro.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import noobtrieu.reimaginedintro.client.config.ModConfig;

public class IntroScreen extends Screen {
    public IntroScreen() {
        super(Component.literal("Reimagined Intro"));
    }

    @Override
    protected void init() {
        super.init();
        // Clear any remaining red loading overlay so it doesn't flash over the intro
        Minecraft.getInstance().gui.setOverlay(null);
        IntroManager.startIntro();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Suppress default background and panorama overlay extraction
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        long elapsed = IntroManager.getElapsedMs();
        long introDuration = FrameStreamer.getIntroTotalDurationMs();
        boolean finished = elapsed >= introDuration;
        long renderElapsed = finished ? introDuration : elapsed;

        // Update active texture
        FrameStreamer.update(renderElapsed);

        // Fill background with black
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);

        // Scale 16:9 animation to FILL (cover) the entire screen with zero black bars
        float screenAspect = (float) this.width / (float) this.height;
        float targetAspect = 16.0f / 9.0f;
        int renderWidth, renderHeight, x, y;

        if (screenAspect > targetAspect) {
            // Screen is wider than 16:9 (e.g. 21:9 ultrawide) -> match width, crop top/bottom
            renderWidth = this.width;
            renderHeight = (int) (this.width / targetAspect);
            x = 0;
            y = (this.height - renderHeight) / 2;
        } else {
            // Screen is taller than 16:9 (e.g. 16:10, 4:3, windowed) -> match height, crop left/right
            renderHeight = this.height;
            renderWidth = (int) (this.height * targetAspect);
            x = (this.width - renderWidth) / 2;
            y = 0;
        }

        // Correct Minecraft 26.3 blit parameters: (id, x0, y0, x1, y1, u0, u1, v0, v1)
        guiGraphics.blit(
            FrameStreamer.getTextureId(),
            x,
            y,
            x + renderWidth,
            y + renderHeight,
            0.0f,
            1.0f,
            0.0f,
            1.0f
        );

        // Smooth fade-in from black during first 400ms
        if (elapsed < 400) {
            float fadeProgress = 1.0f - ((float) elapsed / 400.0f);
            int alpha = (int) (Math.clamp(fadeProgress, 0.0f, 1.0f) * 255.0f);
            if (alpha > 0) {
                guiGraphics.fill(0, 0, this.width, this.height, (alpha << 24));
            }
        }

        // Subtle skip prompt in bottom-right corner after 1.5s
        if (ModConfig.get().showSkipPrompt && elapsed > 1500) {
            Component skipPrompt = Component.literal("ESC to skip");
            int textWidth = this.font.width(skipPrompt);
            guiGraphics.text(this.font, skipPrompt, this.width - textWidth - 16, this.height - 20, 0x66FFFFFF);
        }

        if (finished) {
            IntroManager.onIntroFinished();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            IntroManager.skip();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
