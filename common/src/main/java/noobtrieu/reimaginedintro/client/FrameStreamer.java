package noobtrieu.reimaginedintro.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;

import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FrameStreamer {
    public static final Identifier TEXTURE_ID = ReimaginedIntro.id("active_frame");

    private static DynamicTexture dynamicTexture;
    private static int lastUploadedFrame = -1;
    private static boolean isLoopingPhase = false;
    private static boolean initialized = false;

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

    public static long getFrameDurationMs() {
        IntroPack pack = IntroPackManager.getActivePack();
        return pack != null ? pack.getFrameDurationMs() : 40L;
    }

    public static long getIntroTotalDurationMs() {
        IntroPack pack = IntroPackManager.getActivePack();
        return (pack != null && pack.hasIntro()) ? pack.getIntroDurationMs() : 0L;
    }

    public static synchronized void init() {
        if (initialized) return;

        IntroPack pack = IntroPackManager.getActivePack();
        if (pack == null || (!pack.hasIntro() && !pack.hasLoop())) {
            initialized = true;
            return;
        }

        boolean startWithLoop = !pack.hasIntro() && pack.hasLoop();
        NativeImage firstFrame = loadFrame(startWithLoop, 1);
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
        }

        // Start prefetching upcoming frames
        prefetchUpcoming(startWithLoop, 2, Math.min(5, startWithLoop ? pack.getLoopFrameCount() : pack.getIntroFrameCount()));
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
            prefetchUpcoming(true, 1, Math.min(4, loopCount));
        }
    }

    public static void update(long elapsedMs) {
        if (!initialized) init();

        int introCount = getIntroFrameCount();
        int loopCount = getLoopFrameCount();
        long introDuration = getIntroTotalDurationMs();
        long frameDuration = getFrameDurationMs();

        boolean inLoop = introCount == 0 || elapsedMs >= introDuration;
        int targetFrame;

        if (!inLoop) {
            isLoopingPhase = false;
            targetFrame = (int) (elapsedMs / frameDuration) + 1;
            if (targetFrame > introCount) targetFrame = introCount;
        } else {
            if (loopCount <= 0) {
                return;
            }
            isLoopingPhase = true;
            long loopElapsed = introCount > 0 ? (elapsedMs - introDuration) : elapsedMs;
            targetFrame = (int) ((loopElapsed / frameDuration) % loopCount) + 1;
        }

        if (targetFrame != lastUploadedFrame) {
            uploadFrame(inLoop, targetFrame);
            lastUploadedFrame = targetFrame;

            // Free and remove any skipped or obsolete frames from off-heap memory
            pruneStaleFrames(inLoop, targetFrame);

            // Prefetch ahead by 4 frames
            prefetchUpcoming(inLoop, targetFrame + 1, targetFrame + 4);

            // If nearing end of intro, prefetch loop start frames in the background
            if (!inLoop && introCount > 0 && targetFrame >= introCount - 8 && loopCount > 0) {
                prefetchUpcoming(true, 1, Math.min(4, loopCount));
            }
        }
    }

    private static void pruneStaleFrames(boolean isLoop, int currentFrame) {
        if (predecodedCache.isEmpty()) return;
        int loopCount = getLoopFrameCount();

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
                    isStale = distance > 8;
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
        }
    }

    private static void prefetchUpcoming(boolean isLoop, int startFrame, int endFrame) {
        prefetchExecutor.submit(() -> {
            int max = isLoop ? getLoopFrameCount() : getIntroFrameCount();
            if (max <= 0) return;
            for (int f = startFrame; f <= endFrame; f++) {
                int frame = isLoop ? (((f - 1) % max) + 1) : Math.min(f, max);
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

        try (InputStream stream = isLoop ? pack.openLoopFrame(frameIndex) : pack.openIntroFrame(frameIndex)) {
            if (stream != null) {
                return NativeImageDecoder.decode(stream);
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

    public static boolean isLooping() {
        return isLoopingPhase;
    }

    public static synchronized void cleanup() {
        predecodedCache.values().forEach(NativeImage::close);
        predecodedCache.clear();
        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
        initialized = false;
        lastUploadedFrame = -1;
    }
}
