package noobtrieu.reimaginedintro.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.FrameStreamer;
import noobtrieu.reimaginedintro.client.ModKeybinds;
import noobtrieu.reimaginedintro.client.pack.FrameCacheManager;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;

import java.util.List;

public class ModConfigScreen extends Screen {
    private static final Identifier LOGO_TEXTURE = ReimaginedIntro.id("textures/gui/logo.png");
    private static final float LOGO_ASPECT = 1024.0f / 250.0f;
    private static final long CACHE_POLL_MS = 250L;
    private static final int COL_WIDTH = 156;
    private static final int COL_GAP = 6;
    private static final int ROW_STEP = 24;

    private final Screen parent;
    private CycleButton<VideoResolution> resolutionButton;
    private CycleButton<VideoFps> fpsButton;
    private Button clearCacheButton;
    private Button cacheNowButton;
    private VideoResolution selectedResolution;
    private VideoFps selectedFps;
    private long lastCachePollMs;
    private int logoX;
    private int logoY = 16;
    private int logoWidth = 318;
    private int logoHeight = 78;

    public ModConfigScreen(Screen parent) {
        super(Component.literal("Reimagined Intro Options"));
        this.parent = parent;
        this.selectedResolution = ModConfig.get().getVideoResolution();
        this.selectedFps = ModConfig.get().getVideoFps();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int totalWidth = COL_WIDTH * 2 + COL_GAP;
        int leftX = centerX - totalWidth / 2;
        int rightX = leftX + COL_WIDTH + COL_GAP;

        int doneY = Math.max(176, this.height - 28);
        int logoGap = Math.clamp((this.height - 200) / 8 + 10, 8, 18);
        int gridHeight = ROW_STEP * 3 + 28;
        int availableLogoHeight = Math.max(24, doneY - 16 - logoGap - gridHeight);
        int maxLogoWidth = Math.max(120, Math.min(totalWidth, this.width - 24));

        int heightFromWidth = Math.round(maxLogoWidth / LOGO_ASPECT);
        this.logoHeight = Math.min(heightFromWidth, availableLogoHeight);
        this.logoWidth = Math.min(maxLogoWidth, Math.round(this.logoHeight * LOGO_ASPECT));
        this.logoX = centerX - this.logoWidth / 2;

        int totalContentHeight = this.logoHeight + logoGap + gridHeight;
        this.logoY = Math.max(8, (doneY - totalContentHeight) / 2);
        int startY = this.logoY + this.logoHeight + logoGap;

        IntroPack activePack = IntroPackManager.getActivePack();
        boolean hasIntro = activePack != null && activePack.hasIntro() && activePack.getIntroFrameCount() > 0;
        boolean hasLoop = activePack != null && activePack.hasLoop() && activePack.getLoopFrameCount() > 0;
        boolean hasAudio = hasIntro && activePack.hasAudio();
        boolean hasFrames = hasIntro || hasLoop;
        int srcFps = activePack != null ? activePack.getFps() : 30;

        List<VideoResolution> availableResolutions = FrameCacheManager.getAvailableResolutions(activePack);
        if (this.selectedResolution == null) {
            this.selectedResolution = ModConfig.get().getVideoResolution();
        }
        if (!availableResolutions.contains(this.selectedResolution)) {
            this.selectedResolution = VideoResolution.ORIGINAL;
        }

        List<VideoFps> availableFps = VideoFps.getAvailableFps(srcFps, hasFrames);
        if (this.selectedFps == null) {
            this.selectedFps = ModConfig.get().getVideoFps();
        }
        if (!availableFps.contains(this.selectedFps)) {
            this.selectedFps = VideoFps.ORIGINAL;
        }

        // Row 1: Startup Intro | Title Screen Loop
        CycleButton<Boolean> introButton = this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.literal("§aYes"), Component.literal("§cNo"), hasIntro && ModConfig.get().enableIntro)
                .withTooltip(val -> Tooltip.create(Component.literal(
                    hasIntro
                        ? "Play the intro video when Minecraft launches"
                        : "The active pack does not include a startup intro"
                )))
                .create(leftX, startY, COL_WIDTH, 20, Component.literal("Startup Intro"), (btn, val) -> {
                    if (hasIntro) {
                        ModConfig.get().enableIntro = val;
                    }
                })
        );
        introButton.active = hasIntro;

        CycleButton<Boolean> titleScreenButton = this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.literal("§6Animated"), Component.literal("§7Static"), hasLoop && ModConfig.get().enableTitleScreenLoop)
                .withTooltip(val -> Tooltip.create(Component.literal(
                    hasLoop
                        ? "Use animated loop frames instead of the panorama on the Title Screen"
                        : "The active pack does not include an animated Title Screen loop"
                )))
                .create(rightX, startY, COL_WIDTH, 20, Component.literal("Title Screen"), (btn, val) -> {
                    if (hasLoop) {
                        ModConfig.get().enableTitleScreenLoop = val;
                    }
                })
        );
        titleScreenButton.active = hasLoop;

        // Row 2: Skip Prompt | Options Button
        CycleButton<Boolean> skipPromptButton = this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.literal("§aYes"), Component.literal("§cNo"), hasIntro && ModConfig.get().showSkipPrompt)
                .withTooltip(val -> Tooltip.create(Component.literal(
                    hasIntro
                        ? "Show \"ESC to skip\" prompt during the startup intro"
                        : "The active pack does not include a startup intro"
                )))
                .create(leftX, startY + ROW_STEP, COL_WIDTH, 20, Component.literal("Skip Prompt"), (btn, val) -> {
                    if (hasIntro) {
                        ModConfig.get().showSkipPrompt = val;
                    }
                })
        );
        skipPromptButton.active = hasIntro;

        this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.literal("§aShown"), Component.literal("§cHidden"), ModConfig.get().showOptionsButton)
                .withTooltip(val -> Tooltip.create(Component.literal(
                    ModKeybinds.OPEN_OPTIONS.isUnbound()
                        ? "Show or hide the \"Reimagined Intro...\" button in Minecraft's Options menu.\nTip: You can bind a key in Controls to open this from the Title Screen."
                        : "Show or hide the \"Reimagined Intro...\" button in Minecraft's Options menu.\nTip: You can still press "
                            + ModKeybinds.getOpenOptionsKeyDisplay().getString()
                            + " on the Title Screen."
                )))
                .create(rightX, startY + ROW_STEP, COL_WIDTH, 20, Component.literal("Options Button"), (btn, val) -> {
                    ModConfig.get().showOptionsButton = val;
                })
        );

        // Row 3: Volume Slider | Manage Intro Packs
        AbstractSliderButton volumeSlider = this.addRenderableWidget(
            new AbstractSliderButton(leftX, startY + ROW_STEP * 2, COL_WIDTH, 20, Component.empty(), hasAudio ? ModConfig.get().introVolume : 0.0) {
                {
                    updateMessage();
                }

                @Override
                protected void updateMessage() {
                    if (!hasAudio) {
                        setMessage(Component.literal("Intro Volume: No Audio"));
                        return;
                    }
                    int pct = (int) Math.round(this.value * 100.0);
                    setMessage(Component.literal("Intro Volume: " + pct + "%"));
                }

                @Override
                protected void applyValue() {
                    if (hasAudio) {
                        ModConfig.get().introVolume = (float) this.value;
                    }
                }
            }
        );
        volumeSlider.active = hasAudio;
        volumeSlider.setTooltip(Tooltip.create(Component.literal(
            !hasIntro
                ? "This pack has no startup intro"
                : (!hasAudio ? "This pack has no audio" : "Adjust intro volume")
        )));

        this.addRenderableWidget(
            Button.builder(Component.literal("Manage Intro Packs..."), b -> {
                applyVideoSettings();
                ModConfig.save();
                this.selectedResolution = null;
                this.selectedFps = null;
                if (this.parent instanceof noobtrieu.reimaginedintro.client.pack.gui.IntroPackSelectionScreen) {
                    Minecraft.getInstance().gui.setScreen(this.parent);
                } else {
                    Minecraft.getInstance().gui.setScreen(new noobtrieu.reimaginedintro.client.pack.gui.IntroPackSelectionScreen(this));
                }
            }).bounds(rightX, startY + ROW_STEP * 2, COL_WIDTH, 20).build()
        );

        // Row 4 (Grouped 1-line): [Resolution] [FPS] [Apply] [Clear] = 104 + 4 + 104 + 4 + 78 + 4 + 20 = 318
        int cacheRowY = startY + ROW_STEP * 3 + 8;
        int resWidth = 104;
        int fpsWidth = 104;
        int applyWidth = 78;
        int clearWidth = 20;
        int rowGap = 4;
        int fpsX = leftX + resWidth + rowGap;
        int applyX = fpsX + fpsWidth + rowGap;
        int clearX = applyX + applyWidth + rowGap;

        boolean hasResOptions = availableResolutions.size() > 1;
        this.resolutionButton = this.addRenderableWidget(
            CycleButton.builder((VideoResolution res) -> Component.literal(res.getLabel()), this.selectedResolution)
                .withValues(availableResolutions)
                .displayOnlyValue()
                .withTooltip(val -> Tooltip.create(Component.literal(
                    hasResOptions
                        ? "Lower video resolution to reduce lag"
                        : "This pack is already 480p or lower"
                )))
                .create(leftX, cacheRowY, resWidth, 20, Component.literal("Resolution"), (btn, val) -> {
                    this.selectedResolution = val;
                    refreshCacheControls(true);
                })
        );
        this.resolutionButton.active = hasResOptions;

        boolean hasFpsOptions = availableFps.size() > 1;
        this.fpsButton = this.addRenderableWidget(
            CycleButton.builder((VideoFps fps) -> Component.literal(fps.getLabel()), this.selectedFps)
                .withValues(availableFps)
                .displayOnlyValue()
                .withTooltip(val -> Tooltip.create(Component.literal(
                    hasFpsOptions
                        ? "Lower frame rate to improve performance"
                        : "This pack is already 15 FPS or lower"
                )))
                .create(fpsX, cacheRowY, fpsWidth, 20, Component.literal("FPS"), (btn, val) -> {
                    this.selectedFps = val;
                    refreshCacheControls(true);
                })
        );
        this.fpsButton.active = hasFpsOptions;

        this.cacheNowButton = this.addRenderableWidget(
            Button.builder(Component.literal("Apply"), b -> {
                applyVideoSettings();
                ModConfig.save();
                refreshCacheControls(true);
            })
                .bounds(applyX, cacheRowY, applyWidth, 20)
                .tooltip(Tooltip.create(Component.literal("Prepare the video at the selected quality")))
                .build()
        );

        this.clearCacheButton = this.addRenderableWidget(
            Button.builder(Component.literal("§c✕"), b -> {
                FrameCacheManager.clearAllCache();
                this.selectedResolution = VideoResolution.ORIGINAL;
                this.selectedFps = VideoFps.ORIGINAL;
                if (this.resolutionButton != null) {
                    this.resolutionButton.setValue(VideoResolution.ORIGINAL);
                }
                if (this.fpsButton != null) {
                    this.fpsButton.setValue(VideoFps.ORIGINAL);
                }
                ModConfig.get().setVideoResolution(VideoResolution.ORIGINAL);
                ModConfig.get().setVideoFps(VideoFps.ORIGINAL);
                ModConfig.save();
                FrameStreamer.cleanup();
                refreshCacheControls(true);
            })
                .bounds(clearX, cacheRowY, clearWidth, 20)
                .tooltip(Tooltip.create(Component.literal("Reset quality to Max and clear saved files")))
                .build()
        );

        // Done Button
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(centerX - 100, doneY, 200, 20)
                .build()
        );

        refreshCacheControls(true);
    }

    @Override
    public void tick() {
        super.tick();
        refreshCacheControls(false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.blit(
            LOGO_TEXTURE,
            this.logoX,
            this.logoY,
            this.logoX + this.logoWidth,
            this.logoY + this.logoHeight,
            0.0f,
            1.0f,
            0.0f,
            1.0f
        );
    }

    @Override
    public void onClose() {
        applyVideoSettings();
        ModConfig.save();
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    private void applyVideoSettings() {
        IntroPack active = IntroPackManager.getActivePack();
        boolean hasFrames = active != null
            && ((active.hasIntro() && active.getIntroFrameCount() > 0)
                || (active.hasLoop() && active.getLoopFrameCount() > 0));
        int srcFps = active != null ? active.getFps() : 30;

        VideoResolution targetRes = FrameCacheManager.clampResolutionForPack(
            active,
            this.selectedResolution != null ? this.selectedResolution : VideoResolution.ORIGINAL
        );
        this.selectedResolution = targetRes;

        VideoFps rawFps = this.selectedFps != null ? this.selectedFps : VideoFps.ORIGINAL;
        VideoFps targetFps = hasFrames ? rawFps.clampToSourceFps(srcFps) : VideoFps.ORIGINAL;
        this.selectedFps = targetFps;

        boolean cleanupNeeded = false;
        if (ModConfig.get().getVideoResolution() != targetRes) {
            ModConfig.get().setVideoResolution(targetRes);
            cleanupNeeded = true;
        }
        if (ModConfig.get().getVideoFps() != targetFps) {
            ModConfig.get().setVideoFps(targetFps);
            cleanupNeeded = true;
        }
        if (cleanupNeeded) {
            FrameStreamer.cleanup();
        }
        if (targetRes != VideoResolution.ORIGINAL && active != null) {
            FrameCacheManager.prepareCacheAsync(active, targetRes, null);
        }
    }

    private void refreshCacheControls(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - this.lastCachePollMs < CACHE_POLL_MS) {
            return;
        }
        this.lastCachePollMs = now;
        boolean hasCachedFiles = FrameCacheManager.getTotalCacheBytes() > 0L;

        VideoResolution targetRes = this.selectedResolution != null ? this.selectedResolution : VideoResolution.ORIGINAL;
        VideoFps targetFps = this.selectedFps != null ? this.selectedFps : VideoFps.ORIGINAL;
        VideoResolution activeConfigRes = ModConfig.get().getVideoResolution();
        VideoFps activeConfigFps = ModConfig.get().getVideoFps();
        if (this.clearCacheButton != null) {
            this.clearCacheButton.active = hasCachedFiles
                || targetRes != VideoResolution.ORIGINAL
                || activeConfigRes != VideoResolution.ORIGINAL
                || targetFps != VideoFps.ORIGINAL
                || activeConfigFps != VideoFps.ORIGINAL;
        }

        if (this.cacheNowButton != null) {
            IntroPack active = IntroPackManager.getActivePack();
            if (targetRes == VideoResolution.ORIGINAL || active == null) {
                this.cacheNowButton.setMessage(Component.literal("Apply"));
                this.cacheNowButton.active = false;
                return;
            }

            FrameCacheManager.CacheProgress progress = FrameCacheManager.getProgress(active, targetRes);
            if (progress != null && progress.status == FrameCacheManager.Status.GENERATING) {
                this.cacheNowButton.setMessage(Component.literal("§e" + progress.getPercent() + "%"));
                this.cacheNowButton.active = false;
            } else if (FrameCacheManager.isCacheReady(active, targetRes)) {
                this.cacheNowButton.setMessage(Component.literal("§aReady"));
                this.cacheNowButton.active = false;
            } else {
                this.cacheNowButton.setMessage(Component.literal("Apply"));
                this.cacheNowButton.active = true;
            }
        }
    }
}
