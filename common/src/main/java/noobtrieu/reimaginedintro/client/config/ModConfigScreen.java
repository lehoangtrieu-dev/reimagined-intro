package noobtrieu.reimaginedintro.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import noobtrieu.reimaginedintro.client.IntroManager;
import noobtrieu.reimaginedintro.client.IntroScreen;

public class ModConfigScreen extends Screen {
    private final Screen parent;

    public ModConfigScreen(Screen parent) {
        super(Component.literal("Reimagined Intro Options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = Math.max(40, this.height / 2 - 70);

        // 1. Startup Intro Toggle
        this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.literal("§aYes"), Component.literal("§cNo"))
                .withInitialValue(ModConfig.get().enableIntro)
                .create(centerX - 100, startY, 200, 20, Component.literal("Enable Reimagined Intro"), (btn, val) -> {
                    ModConfig.get().enableIntro = val;
                })
        );

        // 2. Title Screen Loop Toggle
        this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.literal("§6Animated"), Component.literal("§7Static"))
                .withInitialValue(ModConfig.get().enableTitleScreenLoop)
                .create(centerX - 100, startY + 24, 200, 20, Component.literal("Title Screen"), (btn, val) -> {
                    ModConfig.get().enableTitleScreenLoop = val;
                })
        );

        // 3. Skip Prompt Toggle
        this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.literal("§aYes"), Component.literal("§cNo"))
                .withInitialValue(ModConfig.get().showSkipPrompt)
                .create(centerX - 100, startY + 48, 200, 20, Component.literal("Show \"ESC to skip\""), (btn, val) -> {
                    ModConfig.get().showSkipPrompt = val;
                })
        );

        // 4. Volume Slider
        this.addRenderableWidget(new AbstractSliderButton(centerX - 100, startY + 72, 200, 20, Component.empty(), ModConfig.get().introVolume) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                int pct = (int) Math.round(this.value * 100.0);
                setMessage(Component.literal("Intro Volume: " + pct + "%"));
            }

            @Override
            protected void applyValue() {
                ModConfig.get().introVolume = (float) this.value;
            }
        });

        // 5. Test / Preview Intro button
        this.addRenderableWidget(
            Button.builder(Component.literal("Preview Intro Sequence"), b -> {
                ModConfig.save();
                IntroManager.resetForReplay();
                Minecraft.getInstance().setScreen(new IntroScreen());
            }).bounds(centerX - 100, startY + 98, 200, 20).build()
        );

        // 6. Done Button
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(centerX - 100, this.height - 32, 200, 20)
                .build()
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render Title
        int titleWidth = this.font.width(this.title);
        guiGraphics.drawString(this.font, this.title, this.width / 2 - titleWidth / 2, 20, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        ModConfig.save();
        Minecraft.getInstance().setScreen(this.parent);
    }
}
