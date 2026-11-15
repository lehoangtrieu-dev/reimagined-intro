package noobtrieu.reimaginedintro.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import noobtrieu.reimaginedintro.client.config.VideoFps;
import noobtrieu.reimaginedintro.client.config.VideoResolution;
import noobtrieu.reimaginedintro.client.pack.FrameCacheManager;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;
import noobtrieu.reimaginedintro.client.pack.StbFrameTranscoder;

import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FrameStreamer {
    public static final Identifier TEXTURE_ID = ReimaginedIntro.id("active_frame");

    private static DynamicTexture dynamicTexture;
    private static int lastUploadedFrame = -1;
    private static boolean isLoopingPhase = false;
    private static volatile boolean initialized = false;
    private static volatile boolean hasValidTexture = false;

    // Cache of decoded upcoming NativeImages
    private static final ConcurrentHashMap<String, NativeImage> predecodedCache = new ConcurrentHashMap<>();
    private static final ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ReimaginedIntro-Prefetcher");
        t.setDaemon(true);
        return t;
    });

    public static int getIntroFrameCount() {
        IntroPack pack = IntroPackManager.getActivePack();
        return (pack != null && pack.hasIntro()) ? pack.getIntroFrameCount() : 0;
    }

    public static int getLoopFrameCount() {
        IntroPack pack = IntroPackManager.getActivePack();
        return (pack != null && pack.hasLoop()) ? pack.getLoopFrameCount() : 0;
    }

    public static int getSourceFps() {
        IntroPack pack = IntroPackManager.getActivePack();
        return pack != null ? Math.max(1, pack.getFps()) : 30;
    }

    public static long getFrameDurationMs() {
        int effectiveFps = ModConfig.get().getVideoFps().getEffectiveFps(getSourceFps());
        return Math.max(1L, 1000L / effectiveFps);
    }

    public static long getIntroTotalDurationMs() {
        IntroPack pack = IntroPackManager.getActivePack();
        return (pack != null && pack.hasIntro()) ? pack.getIntroDurationMs() : 0L;
    }

    public static synchronized void init() {
        if (initialized) return;

        IntroPack pack = IntroPackManager.getActivePack();
        boolean startWithLoop = pack != null && !pack.hasIntro() && pack.hasLoop();
        NativeImage firstFrame = pack != null ? loadFrame(startWithLoop, 1) : null;
        int width = firstFrame != null ? firstFrame.getWidth() : 1920;
        int height = firstFrame != null ? firstFrame.getHeight() : 1080;

        dynamicTexture = new SmoothDynamicTexture("reimagined_frame", width, height, false);
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, dynamicTexture);
        initialized = true;
        lastUploadedFrame = -1;

        if (firstFrame != null) {
            predecodedCache.put(cacheKey(startWithLoop, 1), firstFrame);
            uploadFrame(startWithLoop, 1);
            lastUploadedFrame = 1;
        } else {
            // Guarantee textureView is initialized on the GPU with black 1x1 fallback to prevent crashes
            NativeImage blank = new NativeImage(1, 1, false);
            blank.setPixel(0, 0, 0xFF000000);
            dynamicTexture.setPixels(blank);
            dynamicTexture.upload();
        }

        if (pack != null && (pack.hasIntro() || pack.hasLoop())) {
            // Start prefetching upcoming sampled frames (step 0 was already loaded above)
            prefetchUpcomingSteps(startWithLoop, 1L, 4);
        }
    }

    public static void loadLastIntroFrame() {
        if (!initialized) init();
        int introCount = getIntroFrameCount();
        if (introCount > 0) {
            uploadFrame(false, introCount);
            lastUploadedFrame = introCount;
        }
    }

    public static void warmupLoop() {
        int loopCount = getLoopFrameCount();
        if (loopCount > 0) {
            prefetchUpcomingSteps(true, 0L, 4);
        }
    }

    public static void update(long elapsedMs) {
        if (!initialized) init();

        int introCount = getIntroFrameCount();
        int loopCount = getLoopFrameCount();
        long introDuration = getIntroTotalDurationMs();
        int srcFps = getSourceFps();
        VideoFps fpsSetting = ModConfig.get().getVideoFps();

        boolean inLoop = introCount == 0 || elapsedMs >= introDuration;
        if (inLoop && loopCount <= 0) {
            return;
        }

        isLoopingPhase = inLoop;
        int activeCount = inLoop ? loopCount : introCount;
        long phaseElapsed = inLoop ? (introCount > 0 ? Math.max(0L, elapsedMs - introDuration) : Math.max(0L, elapsedMs)) : Math.max(0L, elapsedMs);
        long currentStep = fpsSetting.computeSampleStep(phaseElapsed, srcFps, activeCount, inLoop);
        int targetFrame = fpsSetting.mapStepToSourceFrame(currentStep, srcFps, activeCount, inLoop);

        if (targetFrame != lastUploadedFrame) {
            uploadFrame(inLoop, targetFrame);
            lastUploadedFrame = targetFrame;

            // Free and remove any skipped or obsolete frames from off-heap memory
            pruneStaleFrames(inLoop, targetFrame);

            // Prefetch ahead by 4 sampled frames
            prefetchUpcomingSteps(inLoop, currentStep + 1L, 4);

            // If nearing end of intro, prefetch loop start frames in the background
            int stride = Math.max(1, (int) Math.ceil((double) srcFps / fpsSetting.getEffectiveFps(srcFps)));
            if (!inLoop && introCount > 0 && targetFrame >= introCount - Math.max(8, 2 * stride) && loopCount > 0) {
                prefetchUpcomingSteps(true, 0L, 4);
            }
        }
    }

    private static void pruneStaleFrames(boolean isLoop, int currentFrame) {
        if (predecodedCache.isEmpty()) return;
        int loopCount = getLoopFrameCount();
        int srcFps = getSourceFps();
        int effectiveFps = ModConfig.get().getVideoFps().getEffectiveFps(srcFps);
        int keepWindow = Math.max(8, 5 * Math.max(1, (int) Math.ceil((double) srcFps / effectiveFps)));

        predecodedCache.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            boolean entryIsLoop = key.startsWith("l_");

            // If we've entered the loop phase, immediately free any remaining intro frames
            if (isLoop && !entryIsLoop) {
                entry.getValue().close();
                return true;
            }

            if (isLoop != entryIsLoop) {
                return false;
            }

            try {
                int frame = Integer.parseInt(key.substring(2));
                boolean isStale;
                if (!isLoop) {
                    isStale = frame < currentFrame;
                } else {
                    if (loopCount <= 0) return true;
                    int distance = (frame - currentFrame + loopCount) % loopCount;
                    isStale = distance > keepWindow;
                }

                if (isStale) {
                    entry.getValue().close();
                    return true;
                }
            } catch (Exception ignored) {
            }

            return false;
        });
    }

    private static void uploadFrame(boolean isLoop, int frameIndex) {
        String key = cacheKey(isLoop, frameIndex);
        NativeImage image = predecodedCache.remove(key);

        if (image == null) {
            // Direct load if prefetch wasn't ready in time
            image = loadFrame(isLoop, frameIndex);
        }

        if (image != null) {
            if (dynamicTexture == null || dynamicTexture.getPixels() == null
                || dynamicTexture.getPixels().getWidth() != image.getWidth()
                || dynamicTexture.getPixels().getHeight() != image.getHeight()) {
                if (dynamicTexture != null) {
                    dynamicTexture.close();
                }
                dynamicTexture = new SmoothDynamicTexture("reimagined_frame", image.getWidth(), image.getHeight(), false);
                Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, dynamicTexture);
            }
            dynamicTexture.setPixels(image);
            dynamicTexture.upload();
            hasValidTexture = true;
        }
    }

    private static void prefetchUpcomingSteps(boolean isLoop, long startStep, int stepCount) {
        int max = isLoop ? getLoopFrameCount() : getIntroFrameCount();
        if (max <= 0 || stepCount <= 0) return;

        int srcFps = getSourceFps();
        VideoFps fpsSetting = ModConfig.get().getVideoFps();
        prefetchExecutor.submit(() -> {
            int previousFrame = -1;
            for (int i = 0; i < stepCount; i++) {
                int frame = fpsSetting.mapStepToSourceFrame(startStep + i, srcFps, max, isLoop);
                if (frame <= 0 || (!isLoop && frame == previousFrame)) {
                    break;
                }
                previousFrame = frame;
                String key = cacheKey(isLoop, frame);
                if (!predecodedCache.containsKey(key)) {
                    NativeImage img = loadFrame(isLoop, frame);
                    if (img != null) {
                        predecodedCache.put(key, img);
                    }
                }
            }
        });
    }

    private static NativeImage loadFrame(boolean isLoop, int frameIndex) {
        IntroPack pack = IntroPackManager.getActivePack();
        if (pack == null) return null;

        VideoResolution resolution = ModConfig.get().getVideoResolution();
        if (resolution != VideoResolution.ORIGINAL) {
            try (InputStream cached = FrameCacheManager.openCachedFrame(pack, resolution, isLoop, frameIndex)) {
                if (cached != null) {
                    return NativeImageDecoder.decode(cached);
                }
            } catch (Throwable e) {
                ReimaginedIntro.LOGGER.warn("Failed reading cached {} frame #{} for pack {}, falling back to original",
                    isLoop ? "loop" : "intro", frameIndex, pack.getId(), e);
            }

            FrameCacheManager.CacheProgress progress = FrameCacheManager.getProgress(pack, resolution);
            if (!FrameCacheManager.isCacheReady(pack, resolution)
                && (progress == null || progress.status == FrameCacheManager.Status.IDLE)) {
                FrameCacheManager.prepareCacheAsync(pack, resolution, null);
            }
        }

        try (InputStream stream = isLoop ? pack.openLoopFrame(frameIndex) : pack.openIntroFrame(frameIndex)) {
            if (stream != null) {
                NativeImage decoded = NativeImageDecoder.decode(stream);
                return StbFrameTranscoder.downscaleNativeImageIfNeeded(decoded, resolution);
            }
        } catch (Throwable e) {
            ReimaginedIntro.LOGGER.error("Failed to decode " + (isLoop ? "loop" : "intro") + " frame #" + frameIndex + " from pack " + pack.getName(), e);
        }
        return null;
    }

    private static String cacheKey(boolean isLoop, int frame) {
        return (isLoop ? "l_" : "i_") + frame;
    }

    public static Identifier getTextureId() {
        return TEXTURE_ID;
    }

    public static boolean hasLoadedFrameTexture() {
        return initialized && hasValidTexture && dynamicTexture != null;
    }

    public static boolean isLooping() {
        return isLoopingPhase;
    }

    public static synchronized void cleanup() {
        predecodedCache.values().forEach(NativeImage::close);
        predecodedCache.clear();
        hasValidTexture = false;
        initialized = false;
        lastUploadedFrame = -1;

        DynamicTexture toClose = dynamicTexture;
        dynamicTexture = null;
        if (toClose != null) {
            Minecraft mc = Minecraft.getInstance();
            Runnable releaseTask = () -> {
                if (mc != null && mc.getTextureManager() != null) {
                    mc.getTextureManager().release(TEXTURE_ID);
                }
                toClose.close();
            };
            if (mc != null && !mc.isSameThread()) {
                mc.execute(releaseTask);
            } else {
                releaseTask.run();
            }
        }
    }
}
