package noobtrieu.reimaginedintro.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import org.lwjgl.glfw.GLFW;

public class IntroScreen extends Screen {
    public IntroScreen() {
        super(Component.literal("Reimagined Intro"));
    }

    @Override
    protected void init() {
        super.init();
        // Clear any remaining loading overlay so it doesn't flash over the intro
        Minecraft.getInstance().setOverlay(null);
        IntroManager.startIntro();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Suppress default background and panorama overlay
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        long elapsed = IntroManager.getElapsedMs();
        boolean finished = elapsed >= FrameStreamer.INTRO_TOTAL_DURATION_MS;
        long renderElapsed = finished ? FrameStreamer.INTRO_TOTAL_DURATION_MS : elapsed;

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

        // Blit full frame onto the scaled quad
        guiGraphics.blit(
            FrameStreamer.getTextureId(),
            x,
            y,
            0.0F,
            0.0F,
            renderWidth,
            renderHeight,
            renderWidth,
            renderHeight
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
            guiGraphics.drawString(this.font, skipPrompt, this.width - textWidth - 16, this.height - 20, 0x66FFFFFF);
        }

        if (finished) {
            IntroManager.onIntroFinished();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            IntroManager.skip();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
