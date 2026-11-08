package noobtrieu.reimaginedintro.client.pack.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class IntroPackList extends ContainerObjectSelectionList<IntroPackList.PackEntry> {
    private final IntroPackSelectionScreen screen;

    public IntroPackList(IntroPackSelectionScreen screen, Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.screen = screen;
        this.centerListVertically = false;
    }

    @Override
    public int getRowWidth() {
        return Math.min(460, this.width - 40);
    }

    @Override
    protected int scrollBarX() {
        return this.getRowRight() + 8;
    }

    public void setEntries(Collection<IntroPack> packs) {
        this.clearEntries();
        for (IntroPack pack : packs) {
            this.addEntry(new PackEntry(this.screen, pack));
        }
    }

    public static class PackEntry extends ContainerObjectSelectionList.Entry<PackEntry> {
        private final IntroPackSelectionScreen screen;
        private final IntroPack pack;
        private final Button selectButton;
        private final Button previewButton;
        private final List<GuiEventListener> children = new ArrayList<>();
        private final List<NarratableEntry> narratables = new ArrayList<>();

        public PackEntry(IntroPackSelectionScreen screen, IntroPack pack) {
            this.screen = screen;
            this.pack = pack;

            this.selectButton = Button.builder(Component.literal("Select"), b -> screen.selectPack(pack))
                .bounds(0, 0, 52, 20)
                .build();
            this.children.add(this.selectButton);
            this.narratables.add(this.selectButton);

            if (pack.hasIntro()) {
                this.previewButton = Button.builder(Component.literal("▶"), b -> screen.previewPack(pack))
                    .bounds(0, 0, 24, 20)
                    .build();
                this.children.add(this.previewButton);
                this.narratables.add(this.previewButton);
            } else {
                this.previewButton = null;
            }
        }

        public IntroPack getPack() {
            return pack;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return narratables;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean isDown) {
            if (super.mouseClicked(event, isDown)) {
                return true;
            }
            if (event.button() == 0 && isMouseOver(event.x(), event.y())) {
                screen.selectPack(this.pack);
                return true;
            }
            return false;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();

            boolean isActive = IntroPackManager.getActivePack() != null 
                && IntroPackManager.getActivePack().getId().equals(pack.getId());

            // 1. Card Background
            guiGraphics.fill(x, y, x + width, y + height, 0xDD141414);

            // 2. Card Border
            int borderColor;
            if (isActive) {
                borderColor = 0xFF22C55E; // Radiant Emerald Green for Active Pack
            } else if (hovered) {
                borderColor = 0xFFFFFFFF; // Crisp White Highlight on Hover
            } else {
                borderColor = 0xFF2A2A2A; // Subtle Border
            }
            guiGraphics.outline(x, y, width, height, borderColor);

            // 3. Widescreen 16:9 Thumbnail Preview (88x50 px)
            int thumbW = 88;
            int thumbH = 50;
            int thumbX = x + 6;
            int thumbY = y + (height - thumbH) / 2;

            // Thumbnail Border
            guiGraphics.outline(thumbX - 1, thumbY - 1, thumbW + 2, thumbH + 2, 0xFF000000);

            Identifier thumbId = screen.getThumbnailId(pack.getId());
            if (thumbId != null) {
                guiGraphics.blit(thumbId, thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, 0.0f, 1.0f, 0.0f, 1.0f);
            } else {
                // Fallback Thumbnail placeholder
                guiGraphics.fill(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, 0xFF1F1F1F);
                guiGraphics.centeredText(font, Component.literal("§7No Preview"), thumbX + thumbW / 2, thumbY + thumbH / 2 - 4, 0xFF777777);
            }

            // 4. Buttons positioning
            int btnY = y + (height - 20) / 2;
            int selectX = x + width - 58;
            selectButton.setX(selectX);
            selectButton.setY(btnY);
            selectButton.setWidth(52);
            selectButton.setHeight(20);

            if (isActive) {
                selectButton.active = false;
                selectButton.setMessage(Component.literal("Active ✓"));
            } else {
                selectButton.active = true;
                selectButton.setMessage(Component.literal("Select"));
            }

            int buttonsLeft = selectX;
            if (previewButton != null) {
                int prevX = selectX - 28;
                previewButton.setX(prevX);
                previewButton.setY(btnY);
                previewButton.setWidth(24);
                previewButton.setHeight(20);
                buttonsLeft = prevX;
            }

            // 5. Pack Info & Metadata
            int textX = thumbX + thumbW + 8;
            int maxTextW = buttonsLeft - textX - 8;

            // Line 1: Title & Badges
            int curX = textX;
            if (pack.isOfficial()) {
                int badgeW = font.width("OFFICIAL") + 6;
                guiGraphics.fill(curX, y + 8, curX + badgeW, y + 19, 0xFF1D4ED8); // Royal Blue Badge
                guiGraphics.outline(curX, y + 8, badgeW, 11, 0xFF3B82F6);
                guiGraphics.text(font, Component.literal("OFFICIAL").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), curX + 3, y + 9, 0xFFFFFFFF);
                curX += badgeW + 5;
            }

            String packName = font.plainSubstrByWidth(pack.getName(), Math.max(10, buttonsLeft - curX - 6));
            guiGraphics.text(font, Component.literal(packName).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), curX, y + 9, 0xFFFFFFFF);

            // Line 2: Author, Version, FPS
            String meta = "by " + pack.getManifest().getAuthor() + " • v" + pack.getManifest().getVersion() + " • " + pack.getFps() + " FPS";
            String trimmedMeta = font.plainSubstrByWidth(meta, maxTextW);
            guiGraphics.text(font, Component.literal(trimmedMeta).withStyle(ChatFormatting.GRAY), textX, y + 23, 0xFFAAAAAA);

            // Line 3: Feature Indicator Tags
            StringBuilder features = new StringBuilder();
            if (pack.hasIntro()) features.append("§a[Intro] ");
            if (pack.hasLoop()) features.append("§6[Loop] ");
            if (pack.hasAudio()) features.append("§b[Audio] ");
            if (pack.hasPanorama()) features.append("§d[Panorama] ");
            if (pack.hasPanoramaOverlay()) features.append("§e[Overlay] ");
            String trimmedFeatures = font.plainSubstrByWidth(features.toString().trim(), maxTextW);
            guiGraphics.text(font, Component.literal(trimmedFeatures), textX, y + 37, 0xFFFFFFFF);

            // Line 4: Description
            String desc = pack.getManifest().getDescription();
            if (desc != null && !desc.isEmpty()) {
                String trimmedDesc = font.plainSubstrByWidth(desc, maxTextW);
                guiGraphics.text(font, Component.literal(trimmedDesc).withStyle(ChatFormatting.DARK_GRAY), textX, y + 51, 0xFF888888);
            }

            // 6. Render Buttons
            selectButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            if (previewButton != null) {
                previewButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }
}

