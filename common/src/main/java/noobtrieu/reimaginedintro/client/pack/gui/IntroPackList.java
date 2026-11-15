package noobtrieu.reimaginedintro.client.pack.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;
import noobtrieu.reimaginedintro.client.pack.online.OnlinePackRepository;
import noobtrieu.reimaginedintro.client.pack.online.RemoteIntroPack;

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
        List<PackCard> allCards = new ArrayList<>();
        for (IntroPack pack : packs) {
            allCards.add(PackCard.forLocal(this.screen, pack));
        }
        rebuildRows(allCards);
    }

    public void setRemoteEntries(Collection<RemoteIntroPack> packs) {
        List<PackCard> allCards = new ArrayList<>();
        for (RemoteIntroPack pack : packs) {
            allCards.add(PackCard.forRemote(this.screen, pack));
        }
        rebuildRows(allCards);
    }

    private void rebuildRows(List<PackCard> allCards) {
        this.clearEntries();
        int cols = getColumnCount();
        for (int i = 0; i < allCards.size(); i += cols) {
            int end = Math.min(i + cols, allCards.size());
            this.addEntry(new GridRowEntry(this.screen, this, allCards.subList(i, end)));
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
        private final List<PackCard> cards;
        private final List<GuiEventListener> children = new ArrayList<>();
        private final List<NarratableEntry> narratables = new ArrayList<>();

        public GridRowEntry(IntroPackSelectionScreen screen, IntroPackList parentList, List<PackCard> cards) {
            this.screen = screen;
            this.parentList = parentList;
            this.cards = new ArrayList<>(cards);
            for (PackCard card : this.cards) {
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
                        IntroPack installed = card.resolveInstalledPack();
                        if (installed != null) {
                            screen.selectPack(installed);
                            return true;
                        }
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
        private final IntroPack localPack;
        private final RemoteIntroPack remotePack;

        private final Button primaryButton;
        private final Button secondaryButton;
        private final Button uninstallButton;
        private final List<GuiEventListener> children = new ArrayList<>();
        private final List<NarratableEntry> narratables = new ArrayList<>();

        private Runnable primaryAction = () -> {};
        private Runnable secondaryAction = () -> {};
        private Runnable uninstallAction = () -> {};

        private int x;
        private int y;
        private int width;
        private int height;

        private PackCard(IntroPackSelectionScreen screen, IntroPack localPack, RemoteIntroPack remotePack) {
            this.screen = screen;
            this.localPack = localPack;
            this.remotePack = remotePack;

            this.primaryButton = Button.builder(Component.literal("Select"), b -> primaryAction.run())
                .bounds(0, 0, 50, 20)
                .build();
            this.secondaryButton = Button.builder(Component.literal("Preview"), b -> secondaryAction.run())
                .bounds(0, 0, 50, 20)
                .build();
            this.uninstallButton = Button.builder(Component.literal("§c✕"), b -> uninstallAction.run())
                .bounds(0, 0, 20, 20)
                .tooltip(Tooltip.create(Component.literal("Uninstall pack")))
                .build();

            this.children.add(this.primaryButton);
            this.narratables.add(this.primaryButton);
            this.children.add(this.secondaryButton);
            this.narratables.add(this.secondaryButton);
            this.children.add(this.uninstallButton);
            this.narratables.add(this.uninstallButton);
        }

        public static PackCard forLocal(IntroPackSelectionScreen screen, IntroPack pack) {
            return new PackCard(screen, pack, null);
        }

        public static PackCard forRemote(IntroPackSelectionScreen screen, RemoteIntroPack remote) {
            return new PackCard(screen, null, remote);
        }

        public IntroPack resolveInstalledPack() {
            if (localPack != null) return localPack;
            return remotePack != null ? IntroPackManager.getPack(remotePack.getId()) : null;
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

            syncButtonsState();
            layoutButtons();
        }

        private void layoutButtons() {
            int overlayHeight = 66;
            int overlayY = y + height - overlayHeight;
            int btnY = overlayY + 42;
            int contentX = x + 6;
            int availBtnW = width - 12;
            int btnGap = 4;
            int uninstallW = uninstallButton.visible ? 20 : 0;
            int mainAvailW = uninstallButton.visible ? (availBtnW - uninstallW - btnGap) : availBtnW;

            if (secondaryButton.visible) {
                int twoAvailW = mainAvailW - btnGap;
                int btnW = twoAvailW / 2;
                int secW = twoAvailW - btnW;

                primaryButton.setX(contentX);
                primaryButton.setY(btnY);
                primaryButton.setWidth(btnW);
                primaryButton.setHeight(20);

                secondaryButton.setX(contentX + btnW + btnGap);
                secondaryButton.setY(btnY);
                secondaryButton.setWidth(secW);
                secondaryButton.setHeight(20);
            } else {
                primaryButton.setX(contentX);
                primaryButton.setY(btnY);
                primaryButton.setWidth(mainAvailW);
                primaryButton.setHeight(20);

                secondaryButton.setX(0);
                secondaryButton.setY(0);
                secondaryButton.setWidth(0);
                secondaryButton.setHeight(0);
            }

            if (uninstallButton.visible) {
                uninstallButton.setX(contentX + availBtnW - uninstallW);
                uninstallButton.setY(btnY);
                uninstallButton.setWidth(uninstallW);
                uninstallButton.setHeight(20);
            } else {
                uninstallButton.setX(0);
                uninstallButton.setY(0);
                uninstallButton.setWidth(0);
                uninstallButton.setHeight(0);
            }
        }

        private void configureUninstallButton(IntroPack installedPack) {
            boolean show = IntroPackManager.canUninstall(installedPack);
            uninstallButton.visible = show;
            uninstallButton.active = show;
            uninstallAction = show ? () -> screen.confirmUninstallPack(installedPack) : () -> {};
        }

        private void syncButtonsState() {
            if (remotePack == null) {
                boolean isActive = isPackActive(localPack);
                if (isActive) {
                    primaryButton.active = false;
                    primaryButton.setMessage(Component.literal("§aActive ✔"));
                } else {
                    primaryButton.active = true;
                    primaryButton.setMessage(Component.literal("Select"));
                }
                primaryButton.setTooltip(null);
                primaryAction = () -> screen.selectPack(localPack);

                if (localPack.hasIntro()) {
                    secondaryButton.visible = true;
                    secondaryButton.active = true;
                    secondaryButton.setMessage(Component.literal("Preview"));
                    secondaryButton.setTooltip(null);
                    secondaryAction = () -> screen.previewPack(localPack);
                } else {
                    secondaryButton.visible = false;
                    secondaryButton.active = false;
                }
                configureUninstallButton(localPack);
                return;
            }

            // Remote Marketplace Card State
            OnlinePackRepository.DownloadProgress prog = OnlinePackRepository.getDownloadProgress(remotePack.getId());
            if (prog != null && prog.status == OnlinePackRepository.Status.DOWNLOADING) {
                int pct = prog.getPercent();
                String label = pct >= 0 ? ("Downloading " + pct + "%") : "Downloading...";
                primaryButton.active = false;
                primaryButton.setMessage(Component.literal("§b" + label));
                primaryButton.setTooltip(null);
                secondaryButton.visible = false;
                secondaryButton.active = false;
                configureUninstallButton(null);
                return;
            }
            if (prog != null && prog.status == OnlinePackRepository.Status.INSTALLING) {
                primaryButton.active = false;
                primaryButton.setMessage(Component.literal("§eInstalling..."));
                primaryButton.setTooltip(null);
                secondaryButton.visible = false;
                secondaryButton.active = false;
                configureUninstallButton(null);
                return;
            }

            IntroPack installed = IntroPackManager.getPack(remotePack.getId());
            if (installed != null) {
                boolean hasUpdate = RemoteIntroPack.isNewerVersion(installed.getManifest().getVersion(), remotePack.getVersion());
                boolean isActive = isPackActive(installed);

                if (hasUpdate) {
                    primaryButton.active = true;
                    primaryButton.setMessage(Component.literal("§eUpdate"));
                    primaryButton.setTooltip(Tooltip.create(Component.literal(
                        "Update v" + installed.getManifest().getVersion() + " → v" + remotePack.getVersion()
                    )));
                    primaryAction = () -> screen.downloadRemotePack(remotePack);

                    secondaryButton.visible = true;
                    secondaryButton.active = !isActive;
                    secondaryButton.setMessage(Component.literal(isActive ? "§aActive ✔" : "Select"));
                    secondaryButton.setTooltip(null);
                    secondaryAction = () -> screen.selectPack(installed);
                } else {
                    primaryButton.active = !isActive;
                    primaryButton.setMessage(Component.literal(isActive ? "§aActive ✔" : "Select"));
                    primaryButton.setTooltip(null);
                    primaryAction = () -> screen.selectPack(installed);

                    if (installed.hasIntro()) {
                        secondaryButton.visible = true;
                        secondaryButton.active = true;
                        secondaryButton.setMessage(Component.literal("Preview"));
                        secondaryButton.setTooltip(null);
                        secondaryAction = () -> screen.previewPack(installed);
                    } else {
                        secondaryButton.visible = false;
                        secondaryButton.active = false;
                    }
                }
                configureUninstallButton(installed);
                return;
            }

            if (prog != null && prog.status == OnlinePackRepository.Status.FAILED) {
                primaryButton.active = true;
                primaryButton.setMessage(Component.literal("§cRetry Download"));
                String err = prog.errorMessage != null ? prog.errorMessage : "Download failed";
                primaryButton.setTooltip(Tooltip.create(Component.literal("§c" + err)));
                primaryAction = () -> screen.downloadRemotePack(remotePack);
                secondaryButton.visible = false;
                secondaryButton.active = false;
                configureUninstallButton(null);
                return;
            }

            primaryButton.active = true;
            primaryButton.setMessage(Component.literal("Download"));
            primaryButton.setTooltip(null);
            primaryAction = () -> screen.downloadRemotePack(remotePack);
            secondaryButton.visible = false;
            secondaryButton.active = false;
            configureUninstallButton(null);
        }

        private static boolean isPackActive(IntroPack pack) {
            return pack != null
                && IntroPackManager.getActivePack() != null
                && IntroPackManager.getActivePack().getId().equals(pack.getId());
        }

        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            syncButtonsState();
            layoutButtons();

            IntroPack installed = resolveInstalledPack();
            boolean isActive = isPackActive(installed);
            boolean isCardHovered = isMouseOver(mouseX, mouseY);

            String packName = remotePack != null ? remotePack.getName() : localPack.getName();
            String packAuthor = remotePack != null ? remotePack.getAuthor() : localPack.getManifest().getAuthor();
            String packVersion = remotePack != null ? remotePack.getVersion() : localPack.getManifest().getVersion();
            String packDesc = remotePack != null ? remotePack.getDescription() : localPack.getManifest().getDescription();
            int packFps = remotePack != null ? remotePack.getFps() : localPack.getFps();

            // 1. Full Thumbnail Background (Object-Fit: Cover, never stretched!)
            guiGraphics.fill(x, y, x + width, y + height, 0xFF141414);

            Identifier thumbId = remotePack != null
                ? screen.getRemoteThumbnailId(remotePack)
                : screen.getThumbnailId(localPack.getId());

            if (thumbId != null) {
                float cardAspect = (float) width / (float) height;
                float texAspect = 16.0f / 9.0f;
                float u0 = 0.0f, u1 = 1.0f, v0 = 0.0f, v1 = 1.0f;

                if (cardAspect > texAspect) {
                    float visibleV = texAspect / cardAspect;
                    v0 = (1.0f - visibleV) * 0.5f;
                    v1 = 1.0f - v0;
                } else if (cardAspect < texAspect) {
                    float visibleU = cardAspect / texAspect;
                    u0 = (1.0f - visibleU) * 0.5f;
                    u1 = 1.0f - u0;
                }

                guiGraphics.blit(thumbId, x, y, x + width, y + height, u0, u1, v0, v1);
            } else {
                guiGraphics.fill(x, y, x + width, y + height, 0xFF1C1C1C);
                String placeholder = remotePack != null ? "§7[Loading Preview...]" : "§7[No Preview]";
                if (remotePack != null && (remotePack.getThumbnailUrl() == null || remotePack.getThumbnailUrl().isEmpty())) {
                    placeholder = "§7[No Preview]";
                }
                guiGraphics.centeredText(font, Component.literal(placeholder), x + width / 2, y + (height - 66) / 2 - 4, 0xFF666666);
            }

            // 2. Top-Right Floating Badges
            List<Badge> badges = buildBadges();
            int badgeY = y + 5;
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

                if (mouseX >= curBadgeX && mouseX < curBadgeX + tagW && mouseY >= badgeY && mouseY < badgeY + 11) {
                    List<Component> badgeTooltip = List.of(
                        Component.literal(badge.tooltipTitle),
                        Component.literal(badge.tooltipDesc)
                    );
                    guiGraphics.setComponentTooltipForNextFrame(font, badgeTooltip, mouseX, mouseY);
                }

                curBadgeX += tagW + 3;
            }

            // 3. Dark Overlay Docked at Bottom
            int overlayHeight = 66;
            int overlayY = y + height - overlayHeight;
            guiGraphics.fill(x, overlayY, x + width, y + height, 0xB8101010);
            guiGraphics.fill(x, overlayY, x + width, overlayY + 1, 0x30FFFFFF);

            // Download progress accent bar on top edge of bottom overlay
            if (remotePack != null) {
                OnlinePackRepository.DownloadProgress prog = OnlinePackRepository.getDownloadProgress(remotePack.getId());
                if (prog != null && prog.status == OnlinePackRepository.Status.DOWNLOADING) {
                    int pct = prog.getPercent();
                    if (pct > 0) {
                        int fillW = (int) ((width * pct) / 100.0f);
                        guiGraphics.fill(x, overlayY - 2, x + fillW, overlayY, 0xFF38BDF8);
                    }
                }
            }

            int contentX = x + 6;
            int maxTextW = width - 12;

            // Line 1: Title
            int line1Y = overlayY + 5;
            String titleStr = truncateWithEllipsis(font, packName, maxTextW);
            guiGraphics.text(font, Component.literal(titleStr).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), contentX, line1Y + 1, 0xFFFFFFFF);

            // Line 2: Author, FPS, and optional remote size
            int line2Y = line1Y + 12;
            String sizeLabel = (remotePack != null && remotePack.getFileSizeBytes() > 0)
                ? (" • " + RemoteIntroPack.formatFileSize(remotePack.getFileSizeBytes()))
                : "";
            String meta = "by " + packAuthor + " • " + packFps + " FPS" + sizeLabel;
            String trimmedMeta = truncateWithEllipsis(font, meta, maxTextW);
            guiGraphics.text(font, Component.literal(trimmedMeta).withStyle(ChatFormatting.GRAY), contentX, line2Y, 0xFFAAAAAA);

            // Line 3: 1-Line Description Summary
            int line3Y = line2Y + 11;
            if (packDesc != null && !packDesc.trim().isEmpty()) {
                String trimmedDesc = truncateWithEllipsis(font, packDesc.trim(), maxTextW);
                guiGraphics.text(font, Component.literal(trimmedDesc).withStyle(ChatFormatting.DARK_GRAY), contentX, line3Y, 0xFF888888);
            }

            // 4. Buttons Render
            int btnY = overlayY + 42;
            primaryButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            if (secondaryButton.visible) {
                secondaryButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            }
            if (uninstallButton.visible) {
                uninstallButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            }

            // 5. Card Outline Borders
            if (isActive) {
                guiGraphics.outline(x, y, width, height, 0xFF22C55E);
                guiGraphics.outline(x + 1, y + 1, width - 2, height - 2, 0xFF16A34A);
            } else if (remotePack != null && installed != null) {
                guiGraphics.outline(x, y, width, height, isCardHovered ? 0xFFFFFFFF : 0xFF38BDF8);
            } else if (isCardHovered) {
                guiGraphics.outline(x, y, width, height, 0xFFFFFFFF);
            } else {
                guiGraphics.outline(x, y, width, height, 0xFF2A2A2A);
            }

            // 6. Contextual Tooltips: Title & Description Hover
            boolean isTitleHovered = mouseX >= contentX && mouseX < contentX + maxTextW
                && mouseY >= line1Y && mouseY < line2Y;
            if (isTitleHovered && font.width(packName) > maxTextW) {
                guiGraphics.setComponentTooltipForNextFrame(font, List.of(Component.literal(packName)), mouseX, mouseY);
            }

            boolean isDescHovered = mouseX >= contentX && mouseX < contentX + maxTextW
                && mouseY >= line2Y && mouseY < btnY;
            if (isDescHovered) {
                List<FormattedCharSequence> descLines = new ArrayList<>();
                descLines.add(Component.literal("§f§l" + packName + " §7(v" + packVersion + ")").getVisualOrderText());
                descLines.add(Component.literal("§7" + meta).getVisualOrderText());

                if (packDesc != null && !packDesc.trim().isEmpty()) {
                    descLines.add(Component.empty().getVisualOrderText());
                    int maxWrapWidth = 220;
                    for (String paragraph : packDesc.trim().split("\r?\n")) {
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

        private List<Badge> buildBadges() {
            boolean isOfficial = remotePack != null ? remotePack.isOfficial() : localPack.isOfficial();
            boolean hasIntro = remotePack != null ? remotePack.hasIntro() : localPack.hasIntro();
            boolean hasLoop = remotePack != null ? remotePack.hasLoop() : localPack.hasLoop();
            boolean hasAudio = remotePack != null ? remotePack.hasAudio() : localPack.hasAudio();
            boolean hasPanorama = remotePack != null ? remotePack.hasPanorama() : localPack.hasPanorama();
            boolean hasOverlay = remotePack != null ? remotePack.hasPanoramaOverlay() : localPack.hasPanoramaOverlay();

            List<Badge> badges = new ArrayList<>();
            if (isOfficial) {
                badges.add(new Badge("OFFICIAL", 0xA01D4ED8, 0xC03B82F6, 0xFFFFFFFF, true,
                    "§9Official Pack", "§7Curated and maintained by the mod author."));
            }
            if (hasIntro) {
                badges.add(new Badge("Intro", 0x88000000, 0x40FFFFFF, 0xFF4ADE80, false,
                    "§aStartup Intro Sequence", "§7Plays a custom fullscreen intro video on game launch."));
            }
            if (hasLoop) {
                badges.add(new Badge("Loop", 0x88000000, 0x40FFFFFF, 0xFFFBBF24, false,
                    "§6Title Screen Loop", "§7Replaces the title screen with an animated video loop."));
            }
            if (hasAudio) {
                badges.add(new Badge("Audio", 0x88000000, 0x40FFFFFF, 0xFF38BDF8, false,
                    "§bCustom Soundtrack", "§7Includes custom audio and intro music."));
            }
            if (hasPanorama) {
                badges.add(new Badge("Pano", 0x88000000, 0x40FFFFFF, 0xFFE879F9, false,
                    "§dCustom Panorama", "§7Includes custom title screen panorama background."));
            }
            if (hasOverlay) {
                badges.add(new Badge("Over", 0x88000000, 0x40FFFFFF, 0xFFFACC15, false,
                    "§ePanorama Overlay", "§7Includes a custom title screen vignette/overlay."));
            }
            return badges;
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
