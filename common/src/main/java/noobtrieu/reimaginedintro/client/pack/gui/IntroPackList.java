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
import net.minecraft.util.FormattedCharSequence;
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

    public static String truncateWithEllipsis(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        int dotsW = font.width("...");
        if (maxWidth <= dotsW) return "...";
        return font.plainSubstrByWidth(text, maxWidth - dotsW) + "...";
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
                this.previewButton = Button.builder(Component.literal("Preview"), b -> screen.previewPack(pack))
                    .bounds(0, 0, 50, 20)
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
                int btnGap = 4;
                int availBtnW = width - 12 - btnGap;
                int btnW = availBtnW / 2;
                int prevW = availBtnW - btnW;

                selectButton.setX(contentX);
                selectButton.setY(btnY);
                selectButton.setWidth(btnW);
                selectButton.setHeight(20);

                previewButton.setX(contentX + btnW + btnGap);
                previewButton.setY(btnY);
                previewButton.setWidth(prevW);
                previewButton.setHeight(20);
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

            // 1. Full Thumbnail Background (Object-Fit: Cover, never stretched!)
            guiGraphics.fill(x, y, x + width, y + height, 0xFF141414);

            Identifier thumbId = screen.getThumbnailId(pack.getId());
            if (thumbId != null) {
                float cardAspect = (float) width / (float) height;
                float texAspect = 16.0f / 9.0f;
                float u0 = 0.0f, u1 = 1.0f, v0 = 0.0f, v1 = 1.0f;

                if (cardAspect > texAspect) {
                    // Card wider than 16:9 -> fit width, crop height centered
                    float visibleV = texAspect / cardAspect;
                    v0 = (1.0f - visibleV) * 0.5f;
                    v1 = 1.0f - v0;
                } else if (cardAspect < texAspect) {
                    // Card taller than 16:9 -> fit height, crop width centered
                    float visibleU = cardAspect / texAspect;
                    u0 = (1.0f - visibleU) * 0.5f;
                    u1 = 1.0f - u0;
                }

                guiGraphics.blit(thumbId, x, y, x + width, y + height, u0, u1, v0, v1);
            } else {
                guiGraphics.fill(x, y, x + width, y + height, 0xFF1C1C1C);
                guiGraphics.centeredText(font, Component.literal("§7[No Preview]"), x + width / 2, y + (height - 66) / 2 - 4, 0xFF666666);
            }

            // 2. Top-Right Floating Badges: [OFFICIAL] [Intro] [Loop] [Audio] [Pano] [Over]
            int badgeY = y + 5;
            List<Badge> badges = new ArrayList<>();
            if (pack.isOfficial()) {
                badges.add(new Badge("OFFICIAL", 0xA01D4ED8, 0xC03B82F6, 0xFFFFFFFF, true,
                    "§9Official Pack", "§7Curated and maintained by the mod author."));
            }
            if (pack.hasIntro()) {
                badges.add(new Badge("Intro", 0x88000000, 0x40FFFFFF, 0xFF4ADE80, false,
                    "§aStartup Intro Sequence", "§7Plays a custom fullscreen intro video on game launch."));
            }
            if (pack.hasLoop()) {
                badges.add(new Badge("Loop", 0x88000000, 0x40FFFFFF, 0xFFFBBF24, false,
                    "§6Title Screen Loop", "§7Replaces the title screen with an animated video loop."));
            }
            if (pack.hasAudio()) {
                badges.add(new Badge("Audio", 0x88000000, 0x40FFFFFF, 0xFF38BDF8, false,
                    "§bCustom Soundtrack", "§7Includes custom audio and intro music."));
            }
            if (pack.hasPanorama()) {
                badges.add(new Badge("Pano", 0x88000000, 0x40FFFFFF, 0xFFE879F9, false,
                    "§dCustom Panorama", "§7Includes custom title screen panorama background."));
            }
            if (pack.hasPanoramaOverlay()) {
                badges.add(new Badge("Over", 0x88000000, 0x40FFFFFF, 0xFFFACC15, false,
                    "§ePanorama Overlay", "§7Includes a custom title screen vignette/overlay."));
            }

            int totalBadgesW = 0;
            for (int i = 0; i < badges.size(); i++) {
                Badge b = badges.get(i);
                Component comp = b.bold
                    ? Component.literal(b.text).withStyle(ChatFormatting.BOLD)
                    : Component.literal(b.text);
                int w = font.width(comp) + 10;
                totalBadgesW += w + (i > 0 ? 3 : 0);
            }

            int curBadgeX = x + width - 6 - totalBadgesW;
            if (curBadgeX < x + 6) {
                curBadgeX = x + 6;
            }

            for (Badge badge : badges) {
                Component textComp = badge.bold
                    ? Component.literal(badge.text).withStyle(ChatFormatting.BOLD)
                    : Component.literal(badge.text);
                int textW = font.width(textComp);
                int tagW = textW + 10;
                if (curBadgeX + tagW > x + width - 6) break;

                guiGraphics.fill(curBadgeX, badgeY, curBadgeX + tagW, badgeY + 11, badge.bgColor);
                guiGraphics.outline(curBadgeX, badgeY, tagW, 11, badge.borderColor);
                guiGraphics.text(font, textComp, curBadgeX + 5, badgeY + 1, badge.textColor);

                // Contextual Micro-Tooltip on Badge Hover
                if (mouseX >= curBadgeX && mouseX < curBadgeX + tagW && mouseY >= badgeY && mouseY < badgeY + 11) {
                    List<Component> badgeTooltip = List.of(
                        Component.literal(badge.tooltipTitle),
                        Component.literal(badge.tooltipDesc)
                    );
                    guiGraphics.setComponentTooltipForNextFrame(font, badgeTooltip, mouseX, mouseY);
                }

                curBadgeX += tagW + 3;
            }

            // 3. Dark Overlay Docked at Bottom (Soft Translucency)
            int overlayHeight = 66;
            int overlayY = y + height - overlayHeight;
            guiGraphics.fill(x, overlayY, x + width, y + height, 0xB8101010);
            guiGraphics.fill(x, overlayY, x + width, overlayY + 1, 0x30FFFFFF);

            int contentX = x + 6;
            int maxTextW = width - 12;

            // Line 1: Title (Full 1-line with ellipsis)
            int line1Y = overlayY + 5;
            String titleStr = truncateWithEllipsis(font, pack.getName(), maxTextW);
            guiGraphics.text(font, Component.literal(titleStr).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), contentX, line1Y + 1, 0xFFFFFFFF);

            // Line 2: Author & FPS (Full 1-line with ellipsis)
            int line2Y = line1Y + 12;
            String meta = "by " + pack.getManifest().getAuthor() + " • " + pack.getFps() + " FPS";
            String trimmedMeta = truncateWithEllipsis(font, meta, maxTextW);
            guiGraphics.text(font, Component.literal(trimmedMeta).withStyle(ChatFormatting.GRAY), contentX, line2Y, 0xFFAAAAAA);

            // Line 3: 1-Line Description Summary (Full 1-line with ellipsis)
            int line3Y = line2Y + 11;
            String desc = pack.getManifest().getDescription();
            if (desc != null && !desc.trim().isEmpty()) {
                String trimmedDesc = truncateWithEllipsis(font, desc.trim(), maxTextW);
                guiGraphics.text(font, Component.literal(trimmedDesc).withStyle(ChatFormatting.DARK_GRAY), contentX, line3Y, 0xFF888888);
            }

            // 4. Buttons State & Render
            int btnY = overlayY + 42;
            if (isActive) {
                selectButton.active = false;
                selectButton.setMessage(Component.literal("§aActive ✔"));
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
                guiGraphics.outline(x, y, width, height, 0xFF22C55E);
                guiGraphics.outline(x + 1, y + 1, width - 2, height - 2, 0xFF16A34A);
            } else if (isCardHovered) {
                guiGraphics.outline(x, y, width, height, 0xFFFFFFFF);
            } else {
                guiGraphics.outline(x, y, width, height, 0xFF2A2A2A);
            }

            // 6. Contextual Tooltips: Title & Description Hover
            boolean isTitleHovered = mouseX >= contentX && mouseX < contentX + maxTextW 
                && mouseY >= line1Y && mouseY < line2Y;
            if (isTitleHovered && font.width(pack.getName()) > maxTextW) {
                guiGraphics.setComponentTooltipForNextFrame(font, List.of(Component.literal(pack.getName())), mouseX, mouseY);
            }

            boolean isDescHovered = mouseX >= contentX && mouseX < contentX + maxTextW 
                && mouseY >= line2Y && mouseY < btnY;
            if (isDescHovered) {
                List<FormattedCharSequence> descLines = new ArrayList<>();
                descLines.add(Component.literal("§f§l" + pack.getName() + " §7(v" + pack.getManifest().getVersion() + ")").getVisualOrderText());
                descLines.add(Component.literal("§7by " + pack.getManifest().getAuthor() + " • " + pack.getFps() + " FPS").getVisualOrderText());

                if (desc != null && !desc.trim().isEmpty()) {
                    descLines.add(Component.empty().getVisualOrderText());
                    int maxWrapWidth = 220;
                    for (String paragraph : desc.trim().split("\r?\n")) {
                        if (paragraph.trim().isEmpty()) {
                            descLines.add(Component.empty().getVisualOrderText());
                        } else {
                            descLines.addAll(font.split(Component.literal("§f" + paragraph), maxWrapWidth));
                        }
                    }
                }

                guiGraphics.setTooltipForNextFrame(font, descLines, mouseX, mouseY);
            }
        }

        private static class Badge {
            final String text;
            final int bgColor;
            final int borderColor;
            final int textColor;
            final boolean bold;
            final String tooltipTitle;
            final String tooltipDesc;

            Badge(String text, int bgColor, int borderColor, int textColor, boolean bold, String tooltipTitle, String tooltipDesc) {
                this.text = text;
                this.bgColor = bgColor;
                this.borderColor = borderColor;
                this.textColor = textColor;
                this.bold = bold;
                this.tooltipTitle = tooltipTitle;
                this.tooltipDesc = tooltipDesc;
            }
        }
    }
}
