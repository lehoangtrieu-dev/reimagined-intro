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
import noobtrieu.reimaginedintro.client.pack.FrameCacheManager;
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

    private static String truncateStyledWithEllipsis(Font font, String text, int maxWidth, ChatFormatting... formatting) {
        if (text == null || text.isEmpty()) return "";
        if (font.width(Component.literal(text).withStyle(formatting)) <= maxWidth) {
            return text;
        }
        int dotsW = font.width(Component.literal("...").withStyle(formatting));
        if (maxWidth <= dotsW) {
            return "...";
        }
        String candidate = font.plainSubstrByWidth(text, maxWidth - dotsW);
        while (!candidate.isEmpty() && font.width(Component.literal(candidate + "...").withStyle(formatting)) > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate + "...";
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
        private static final int OVERLAY_HEIGHT = 53;
        private static final int BUTTON_OFFSET_Y = 29;

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
            int overlayY = y + height - OVERLAY_HEIGHT;
            int btnY = overlayY + BUTTON_OFFSET_Y;
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
                guiGraphics.centeredText(font, Component.literal(placeholder), x + width / 2, y + (height - OVERLAY_HEIGHT) / 2 - 4, 0xFF666666);
            }

            // 2. Top-Right Official Badge (only shown on official packs to keep thumbnails uncluttered)
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

                curBadgeX += tagW + 3;
            }

            // 3. Dark Overlay Docked at Bottom
            int overlayY = y + height - OVERLAY_HEIGHT;
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

            // Line 1: Name only
            int line1Y = overlayY + 5;
            String safeName = packName != null ? packName : "";
            String shownName = truncateStyledWithEllipsis(font, safeName, maxTextW, ChatFormatting.WHITE, ChatFormatting.BOLD);
            guiGraphics.text(font, Component.literal(shownName).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), contentX, line1Y, 0xFFFFFFFF);

            // Line 2: Author only
            int line2Y = overlayY + 16;
            String safeAuthor = (packAuthor != null && !packAuthor.trim().isEmpty()) ? packAuthor.trim() : "Unknown";
            String authorLine = "by " + safeAuthor;
            String trimmedAuthor = truncateWithEllipsis(font, authorLine, maxTextW);
            guiGraphics.text(font, Component.literal(trimmedAuthor).withStyle(ChatFormatting.GRAY), contentX, line2Y, 0xFFAAAAAA);

            // 4. Buttons Render
            int btnY = overlayY + BUTTON_OFFSET_Y;
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

            // 6. Card Details Tooltip (shown when hovering card body above buttons)
            if (isCardHovered && mouseY < btnY) {
                List<FormattedCharSequence> tooltipLines = buildCardHoverTooltip(
                    font, installed, packVersion, packFps, packDesc
                );
                if (!tooltipLines.isEmpty()) {
                    guiGraphics.setTooltipForNextFrame(font, tooltipLines, mouseX, mouseY);
                }
            }
        }

        private List<FormattedCharSequence> buildCardHoverTooltip(
            Font font,
            IntroPack installed,
            String packVersion,
            int packFps,
            String packDesc
        ) {
            List<FormattedCharSequence> lines = new ArrayList<>();

            // Specs Line: v1.0.0 • 1440p@25 • 75.6 MB
            List<String> specParts = new ArrayList<>();
            if (packVersion != null && !packVersion.trim().isEmpty()) {
                specParts.add("v" + packVersion.trim());
            }
            String resLabel = remotePack != null ? remotePack.getResolution() : FrameCacheManager.formatResolutionLabel(localPack);
            if (remotePack != null && resLabel == null && installed != null) {
                resLabel = FrameCacheManager.formatResolutionLabel(installed);
            }
            specParts.add((resLabel != null && !resLabel.isEmpty()) ? (resLabel + "@" + packFps) : (packFps + " FPS"));
            long fileSizeBytes = remotePack != null
                ? (remotePack.getFileSizeBytes() > 0 ? remotePack.getFileSizeBytes() : (installed != null ? installed.getFileSizeBytes() : 0L))
                : localPack.getFileSizeBytes();
            if (fileSizeBytes > 0) {
                specParts.add(RemoteIntroPack.formatFileSize(fileSizeBytes));
            }
            lines.add(Component.literal(String.join(" • ", specParts))
                .withStyle(ChatFormatting.GRAY)
                .getVisualOrderText());

            // Feature Summary
            boolean hasIntro = remotePack != null ? remotePack.hasIntro() : localPack.hasIntro();
            boolean hasLoop = remotePack != null ? remotePack.hasLoop() : localPack.hasLoop();
            boolean hasAudio = remotePack != null ? remotePack.hasAudio() : localPack.hasAudio();

            List<String> featureParts = new ArrayList<>();
            if (hasIntro) featureParts.add("Intro");
            if (hasLoop) featureParts.add("Title Screen");
            if (hasAudio) featureParts.add("Audio");
            if (!featureParts.isEmpty()) {
                lines.add(Component.literal(String.join(" • ", featureParts))
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .getVisualOrderText());
            }

            // Wrapped Description
            String rawDesc = packDesc != null ? packDesc.trim() : "";
            if (!rawDesc.isEmpty()) {
                lines.add(Component.empty().getVisualOrderText());
                int maxWrapWidth = 220;
                for (String paragraph : rawDesc.split("\\r?\\n")) {
                    if (paragraph.trim().isEmpty()) {
                        lines.add(Component.empty().getVisualOrderText());
                    } else {
                        lines.addAll(font.split(Component.literal(paragraph).withStyle(ChatFormatting.WHITE), maxWrapWidth));
                    }
                }
            }

            return lines;
        }

        private List<Badge> buildBadges() {
            boolean isOfficial = remotePack != null ? remotePack.isOfficial() : localPack.isOfficial();

            List<Badge> badges = new ArrayList<>();
            if (isOfficial) {
                badges.add(new Badge("OFFICIAL", 0xA01D4ED8, 0xC03B82F6, 0xFFFFFFFF, true));
            }
            return badges;
        }

        private static class Badge {
            final String text;
            final int bgColor;
            final int borderColor;
            final int textColor;
            final boolean bold;

            Badge(String text, int bgColor, int borderColor, int textColor, boolean bold) {
                this.text = text;
                this.bgColor = bgColor;
                this.borderColor = borderColor;
                this.textColor = textColor;
                this.bold = bold;
            }
        }
    }
}
