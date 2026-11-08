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

public class IntroPackList extends ContainerObjectSelectionList<IntroPackList.GridRowEntry> {
    private final IntroPackSelectionScreen screen;

    public IntroPackList(IntroPackSelectionScreen screen, Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.screen = screen;
        this.centerListVertically = false;
    }

    public int getColumnCount() {
        return this.width < 450 ? 2 : 3;
    }

    @Override
    public int getRowWidth() {
        return Math.min(640, this.width - 32);
    }

    @Override
    protected int scrollBarX() {
        return this.getRowRight() + 6;
    }

    public void setEntries(Collection<IntroPack> packs) {
        this.clearEntries();
        List<IntroPack> list = new ArrayList<>(packs);
        int cols = getColumnCount();
        for (int i = 0; i < list.size(); i += cols) {
            int end = Math.min(i + cols, list.size());
            List<IntroPack> rowPacks = list.subList(i, end);
            this.addEntry(new GridRowEntry(this.screen, this, rowPacks));
        }
    }

    public static class GridRowEntry extends ContainerObjectSelectionList.Entry<GridRowEntry> {
        private final IntroPackSelectionScreen screen;
        private final IntroPackList parentList;
        private final List<PackCard> cards = new ArrayList<>();
        private final List<GuiEventListener> children = new ArrayList<>();
        private final List<NarratableEntry> narratables = new ArrayList<>();

        public GridRowEntry(IntroPackSelectionScreen screen, IntroPackList parentList, List<IntroPack> packs) {
            this.screen = screen;
            this.parentList = parentList;
            for (IntroPack pack : packs) {
                PackCard card = new PackCard(screen, pack);
                this.cards.add(card);
                this.children.addAll(card.children());
                this.narratables.addAll(card.narratables());
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return narratables;
        }

        private void updateCardBounds() {
            int rowX = this.getX();
            int rowY = this.getY();
            int totalWidth = this.parentList.getRowWidth();
            int cols = this.parentList.getColumnCount();
            int gap = 8;
            int cardWidth = (totalWidth - (cols - 1) * gap) / cols;
            int cardHeight = 134;

            for (int i = 0; i < cards.size(); i++) {
                PackCard card = cards.get(i);
                int cardX = rowX + i * (cardWidth + gap);
                card.updateBounds(cardX, rowY, cardWidth, cardHeight);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean isDown) {
            updateCardBounds();
            if (super.mouseClicked(event, isDown)) {
                return true;
            }
            if (event.button() == 0) {
                for (PackCard card : cards) {
                    if (card.isMouseOver(event.x(), event.y())) {
                        screen.selectPack(card.getPack());
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            updateCardBounds();
            for (PackCard card : cards) {
                card.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }

    public static class PackCard {
        private final IntroPackSelectionScreen screen;
        private final IntroPack pack;
        private final Button selectButton;
        private final Button previewButton;
        private final List<GuiEventListener> children = new ArrayList<>();
        private final List<NarratableEntry> narratables = new ArrayList<>();

        private int x;
        private int y;
        private int width;
        private int height;

        public PackCard(IntroPackSelectionScreen screen, IntroPack pack) {
            this.screen = screen;
            this.pack = pack;

            this.selectButton = Button.builder(Component.literal("Select"), b -> screen.selectPack(pack))
                .bounds(0, 0, 50, 20)
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

        public List<GuiEventListener> children() {
            return children;
        }

        public List<NarratableEntry> narratables() {
            return narratables;
        }

        public void updateBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;

            int overlayHeight = 66;
            int overlayY = y + height - overlayHeight;
            int btnY = overlayY + 42;
            int contentX = x + 6;

            if (previewButton != null) {
                int prevW = 26;
                int prevX = x + width - 6 - prevW;
                previewButton.setX(prevX);
                previewButton.setY(btnY);
                previewButton.setWidth(prevW);
                previewButton.setHeight(20);

                int selectW = prevX - contentX - 4;
                selectButton.setX(contentX);
                selectButton.setY(btnY);
                selectButton.setWidth(selectW);
                selectButton.setHeight(20);
            } else {
                selectButton.setX(contentX);
                selectButton.setY(btnY);
                selectButton.setWidth(width - 12);
                selectButton.setHeight(20);
            }
        }

        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            boolean isActive = IntroPackManager.getActivePack() != null 
                && IntroPackManager.getActivePack().getId().equals(pack.getId());
            boolean isCardHovered = isMouseOver(mouseX, mouseY);

            // 1. Full Thumbnail Background (Matching HTML Mockup)
            guiGraphics.fill(x, y, x + width, y + height, 0xFF141414);

            Identifier thumbId = screen.getThumbnailId(pack.getId());
            if (thumbId != null) {
                guiGraphics.blit(thumbId, x, y, x + width, y + height, 0.0f, 1.0f, 0.0f, 1.0f);
            } else {
                guiGraphics.fill(x, y, x + width, y + height, 0xFF1C1C1C);
                guiGraphics.centeredText(font, Component.literal("§7[No Preview]"), x + width / 2, y + (height - 66) / 2 - 4, 0xFF666666);
            }

            // 2. Top-Right Floating Feature Badges (Option B)
            int curBadgeX = x + width - 6;
            int badgeY = y + 5;

            List<String> tags = new ArrayList<>();
            if (pack.hasLoop()) tags.add("§6Loop");
            if (pack.hasAudio()) tags.add("§bAudio");
            if (pack.hasPanorama()) tags.add("§dPano");
            if (pack.hasPanoramaOverlay()) tags.add("§eOver");
            if (pack.hasIntro() && tags.size() < 3) tags.add("§aIntro");

            for (String tag : tags) {
                int tagW = font.width(tag) + 6;
                curBadgeX -= tagW;
                guiGraphics.fill(curBadgeX, badgeY, curBadgeX + tagW, badgeY + 11, 0xD0101010);
                guiGraphics.outline(curBadgeX, badgeY, tagW, 11, 0x55FFFFFF);
                guiGraphics.text(font, Component.literal(tag), curBadgeX + 3, badgeY + 1, 0xFFFFFFFF);
                curBadgeX -= 3;
            }

            // 3. Dark Overlay Docked at Bottom (Matching HTML)
            int overlayHeight = 66;
            int overlayY = y + height - overlayHeight;
            guiGraphics.fill(x, overlayY, x + width, y + height, 0xEE121212);
            guiGraphics.fill(x, overlayY, x + width, overlayY + 1, 0x40FFFFFF);

            int contentX = x + 6;
            int maxTextW = width - 12;

            // Line 1: Title & [OFFICIAL] badge
            int line1Y = overlayY + 5;
            int curTitleX = contentX;
            if (pack.isOfficial()) {
                int badgeW = font.width("OFFICIAL") + 6;
                guiGraphics.fill(curTitleX, line1Y, curTitleX + badgeW, line1Y + 10, 0xFF1D4ED8);
                guiGraphics.outline(curTitleX, line1Y, badgeW, 10, 0xFF3B82F6);
                guiGraphics.text(font, Component.literal("OFFICIAL").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), curTitleX + 3, line1Y + 1, 0xFFFFFFFF);
                curTitleX += badgeW + 4;
            }

            int availTitleW = Math.max(10, x + width - curTitleX - 6);
            String titleStr = font.plainSubstrByWidth(pack.getName(), availTitleW);
            guiGraphics.text(font, Component.literal(titleStr).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), curTitleX, line1Y + 1, 0xFFFFFFFF);

            // Line 2: Author & FPS
            int line2Y = line1Y + 12;
            String meta = "by " + pack.getManifest().getAuthor() + " • " + pack.getFps() + " FPS";
            String trimmedMeta = font.plainSubstrByWidth(meta, maxTextW);
            guiGraphics.text(font, Component.literal(trimmedMeta).withStyle(ChatFormatting.GRAY), contentX, line2Y, 0xFFAAAAAA);

            // Line 3: 1-Line Description Summary
            int line3Y = line2Y + 11;
            String desc = pack.getManifest().getDescription();
            if (desc != null && !desc.isEmpty()) {
                String trimmedDesc = font.plainSubstrByWidth(desc, maxTextW);
                guiGraphics.text(font, Component.literal(trimmedDesc).withStyle(ChatFormatting.DARK_GRAY), contentX, line3Y, 0xFF888888);
            }

            // 4. Buttons State & Render
            if (isActive) {
                selectButton.active = false;
                selectButton.setMessage(Component.literal("§aActive ✓"));
            } else {
                selectButton.active = true;
                selectButton.setMessage(Component.literal("Select"));
            }

            selectButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            if (previewButton != null) {
                previewButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            }

            // 5. Card Outline Borders
            if (isActive) {
                // Radiant Emerald Green Outline
                guiGraphics.outline(x, y, width, height, 0xFF22C55E);
                guiGraphics.outline(x + 1, y + 1, width - 2, height - 2, 0xFF16A34A);
            } else if (isCardHovered) {
                // Crisp White Outline
                guiGraphics.outline(x, y, width, height, 0xFFFFFFFF);
            } else {
                // Dark Outline
                guiGraphics.outline(x, y, width, height, 0xFF2A2A2A);
            }

            // 6. Rich Hover Tooltip (Full description & feature breakdown)
            boolean hoveringButton = selectButton.isMouseOver(mouseX, mouseY) 
                || (previewButton != null && previewButton.isMouseOver(mouseX, mouseY));
            if (isCardHovered && !hoveringButton) {
                StringBuilder sb = new StringBuilder();
                sb.append("§f§l").append(pack.getName()).append("\n");
                sb.append("§7by ").append(pack.getManifest().getAuthor())
                  .append(" • v").append(pack.getManifest().getVersion())
                  .append(" • ").append(pack.getFps()).append(" FPS\n");
                
                sb.append("\n§6Features:\n");
                if (pack.hasIntro()) sb.append(" §a✓ Startup Intro Animation\n");
                if (pack.hasLoop()) sb.append(" §6✓ Title Screen Video Loop\n");
                if (pack.hasAudio()) sb.append(" §b✓ Custom Soundtrack & Audio\n");
                if (pack.hasPanorama()) sb.append(" §d✓ Custom Panorama Background\n");
                if (pack.hasPanoramaOverlay()) sb.append(" §e✓ Panorama Vignette Overlay\n");

                if (desc != null && !desc.trim().isEmpty()) {
                    sb.append("\n§f").append(desc.trim());
                }

                guiGraphics.setTooltipForNextFrame(Component.literal(sb.toString().trim()), mouseX, mouseY);
            }
        }
    }
}
