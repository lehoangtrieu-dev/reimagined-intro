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
import noobtrieu.reimaginedintro.client.FrameStreamer;
import noobtrieu.reimaginedintro.client.pack.FrameCacheManager;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;

public class ModConfigScreen extends Screen {
    private static final long CACHE_POLL_MS = 250L;

    private final Screen parent;
    private CycleButton<VideoResolution> resolutionButton;
    private Button clearCacheButton;
    private Button cacheNowButton;
    private VideoResolution selectedResolution;
    private long lastCachePollMs;
    private int titleY = 16;

    public ModConfigScreen(Screen parent) {
        super(Component.literal("Reimagined Intro Options"));
        this.parent = parent;
        this.selectedResolution = ModConfig.get().getVideoResolution();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int rowStep = this.height < 280 ? 21 : 22;
        int doneY = this.height < 280 ? this.height - 24 : this.height - 28;
        int cacheY = doneY - 24;
        int startY = Math.max(22, Math.min(this.height / 2 - 92, cacheY - (rowStep * 6 + 26)));
        this.titleY = Math.max(6, Math.min(16, startY - 12));

        if (this.selectedResolution == null) {
            this.selectedResolution = ModConfig.get().getVideoResolution();
        }

        // 1. Startup Intro Toggle
        this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.literal("§aYes"), Component.literal("§cNo"), ModConfig.get().enableIntro)
                .create(centerX - 100, startY, 200, 20, Component.literal("Enable Reimagined Intro"), (btn, val) -> {
                    ModConfig.get().enableIntro = val;
                })
        );

        // 2. Video Resolution & Clear Cache Button (below Enable Reimagined Intro)
        this.resolutionButton = this.addRenderableWidget(
            CycleButton.builder((VideoResolution res) -> Component.literal(res.getLabel()), this.selectedResolution)
                .withValues(VideoResolution.values())
                .withTooltip(val -> Tooltip.create(Component.literal(
                    "Downscale intro/loop frames and cache them on disk to reduce lag and RAM usage.\nApplied when clicking Cache or Done. Packs already within the target height skip re-encoding."
                )))
                .create(centerX - 100, startY + rowStep, 176, 20, Component.literal("Video Resolution"), (btn, val) -> {
                    this.selectedResolution = val;
                    refreshCacheControls(true);
                })
        );

        this.clearCacheButton = this.addRenderableWidget(
            Button.builder(Component.literal("§c✕"), b -> {
                FrameCacheManager.clearAllCache();
                this.selectedResolution = VideoResolution.ORIGINAL;
                if (this.resolutionButton != null) {
                    this.resolutionButton.setValue(VideoResolution.ORIGINAL);
                }
                ModConfig.get().setVideoResolution(VideoResolution.ORIGINAL);
                ModConfig.save();
                FrameStreamer.cleanup();
                refreshCacheControls(true);
            })
                .bounds(centerX + 80, startY + rowStep, 20, 20)
                .tooltip(Tooltip.create(Component.literal("Clear all cached frames and reset resolution to Original")))
                .build()
        );

        // 3. Title Screen Loop Toggle
        this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.literal("§6Animated"), Component.literal("§7Static"), ModConfig.get().enableTitleScreenLoop)
                .create(centerX - 100, startY + rowStep * 2, 200, 20, Component.literal("Title Screen"), (btn, val) -> {
                    ModConfig.get().enableTitleScreenLoop = val;
                })
        );

        // 4. Skip Prompt Toggle
        this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.literal("§aYes"), Component.literal("§cNo"), ModConfig.get().showSkipPrompt)
                .create(centerX - 100, startY + rowStep * 3, 200, 20, Component.literal("Show \"ESC to skip\""), (btn, val) -> {
                    ModConfig.get().showSkipPrompt = val;
                })
        );

        // 5. Options Menu Button Toggle
        this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.literal("§aShown"), Component.literal("§cHidden"), ModConfig.get().showOptionsButton)
                .withTooltip(val -> Tooltip.create(Component.literal(
                    "Show or hide the \"Reimagined Intro...\" button in Minecraft's Options menu.\nTip: You can still press F7 on the Title Screen."
                )))
                .create(centerX - 100, startY + rowStep * 4, 200, 20, Component.literal("Options Button"), (btn, val) -> {
                    ModConfig.get().showOptionsButton = val;
                })
        );

        // 6. Volume Slider
        this.addRenderableWidget(new AbstractSliderButton(centerX - 100, startY + rowStep * 5, 200, 20, Component.empty(), ModConfig.get().introVolume) {
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

        // 7. Manage Intro Packs
        this.addRenderableWidget(
            Button.builder(Component.literal("Manage Intro Packs..."), b -> {
                applyResolutionSelection();
                ModConfig.save();
                Minecraft.getInstance().gui.setScreen(new noobtrieu.reimaginedintro.client.pack.gui.IntroPackSelectionScreen(this));
            }).bounds(centerX - 100, startY + rowStep * 6 + 4, 200, 20).build()
        );

        // 8. Generate Cache Button (directly above Done)
        this.cacheNowButton = this.addRenderableWidget(
            Button.builder(Component.literal("Cache Resolution"), b -> {
                applyResolutionSelection();
                ModConfig.save();
                refreshCacheControls(true);
            })
                .bounds(centerX - 100, cacheY, 200, 20)
                .tooltip(Tooltip.create(Component.literal("Generate cached downscaled frames for the selected resolution")))
                .build()
        );

        // 9. Done Button
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

        int titleWidth = this.font.width(this.title);
        guiGraphics.text(this.font, this.title, this.width / 2 - titleWidth / 2, this.titleY, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        applyResolutionSelection();
        ModConfig.save();
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    private void applyResolutionSelection() {
        VideoResolution target = this.selectedResolution != null ? this.selectedResolution : VideoResolution.ORIGINAL;
        VideoResolution current = ModConfig.get().getVideoResolution();
        if (current != target) {
            ModConfig.get().setVideoResolution(target);
            FrameStreamer.cleanup();
        }
        if (target != VideoResolution.ORIGINAL) {
            IntroPack active = IntroPackManager.getActivePack();
            if (active != null) {
                FrameCacheManager.prepareCacheAsync(active, target, null);
            }
        }
    }

    private void refreshCacheControls(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - this.lastCachePollMs < CACHE_POLL_MS) {
            return;
        }
        this.lastCachePollMs = now;
        boolean hasCachedFiles = FrameCacheManager.getTotalCacheBytes() > 0L;

        VideoResolution target = this.selectedResolution != null ? this.selectedResolution : VideoResolution.ORIGINAL;
        VideoResolution activeConfigRes = ModConfig.get().getVideoResolution();
        if (this.clearCacheButton != null) {
            this.clearCacheButton.active = hasCachedFiles
                || target != VideoResolution.ORIGINAL
                || activeConfigRes != VideoResolution.ORIGINAL;
        }

        if (this.cacheNowButton != null) {
            IntroPack active = IntroPackManager.getActivePack();
            if (target == VideoResolution.ORIGINAL || active == null) {
                this.cacheNowButton.setMessage(Component.literal("Cache Resolution"));
                this.cacheNowButton.active = false;
                return;
            }

            FrameCacheManager.CacheProgress progress = FrameCacheManager.getProgress(active, target);
            if (progress != null && progress.status == FrameCacheManager.Status.GENERATING) {
                this.cacheNowButton.setMessage(Component.literal("§eCaching " + target.getLabel() + "... " + progress.getPercent() + "%"));
                this.cacheNowButton.active = false;
            } else if (FrameCacheManager.isCacheReady(active, target)) {
                this.cacheNowButton.setMessage(Component.literal("§a" + target.getLabel() + " Cached"));
                this.cacheNowButton.active = false;
            } else {
                this.cacheNowButton.setMessage(Component.literal("Cache " + target.getLabel() + " Frames"));
                this.cacheNowButton.active = true;
            }
        }
    }
}
