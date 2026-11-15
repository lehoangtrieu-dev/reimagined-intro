package noobtrieu.reimaginedintro.client.pack.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import noobtrieu.reimaginedintro.client.pack.online.OnlinePackRepository;

/**
 * Simple prompt screen for downloading and installing a pack ZIP directly from an HTTP/HTTPS URL.
 */
public class InstallFromUrlScreen extends Screen {
    private final IntroPackSelectionScreen parent;
    private EditBox urlBox;
    private Button installButton;
    private String errorText = null;

    public InstallFromUrlScreen(IntroPackSelectionScreen parent) {
        super(Component.literal("Install Intro Pack from URL"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int boxWidth = Math.min(340, this.width - 40);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.urlBox = new EditBox(this.font, centerX - boxWidth / 2, centerY - 12, boxWidth, 20, Component.literal("Pack ZIP URL"));
        this.urlBox.setMaxLength(1024);
        this.urlBox.setHint(Component.literal("https://github.com/.../releases/download/.../pack.zip").withStyle(ChatFormatting.GRAY));
        this.urlBox.setResponder(text -> {
            this.errorText = null;
            if (this.installButton != null) {
                this.installButton.active = text != null && !text.trim().isEmpty();
            }
        });
        this.addRenderableWidget(this.urlBox);
        this.setInitialFocus(this.urlBox);

        int btnW = (boxWidth - 8) / 2;
        this.installButton = Button.builder(Component.literal("Download & Install"), b -> tryStartDownload())
            .bounds(centerX - boxWidth / 2, centerY + 18, btnW, 20)
            .build();
        this.installButton.active = false;
        this.addRenderableWidget(this.installButton);

        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
                .bounds(centerX - boxWidth / 2 + btnW + 8, centerY + 18, btnW, 20)
                .build()
        );
    }

    private void tryStartDownload() {
        String raw = urlBox.getValue();
        String normalized = OnlinePackRepository.normalizeUserUrl(raw);
        try {
            OnlinePackRepository.validateHttpUri(normalized);
        } catch (Exception e) {
            this.errorText = e.getMessage() != null ? e.getMessage() : "Invalid URL";
            return;
        }

        parent.startDirectUrlDownload(normalized);
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        guiGraphics.centeredText(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), centerX, centerY - 48, 0xFFFFFFFF);
        guiGraphics.centeredText(
            this.font,
            Component.literal("§7Paste a direct .zip URL (GitHub Releases, raw link, etc.)"),
            centerX,
            centerY - 30,
            0xFFAAAAAA
        );

        if (errorText != null) {
            guiGraphics.centeredText(this.font, Component.literal("§c" + errorText), centerX, centerY + 46, 0xFFFF5555);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }
}

