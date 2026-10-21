package noobtrieu.reimaginedintro.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import noobtrieu.reimaginedintro.ReimaginedIntro;

import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FrameStreamer {
    public static final Identifier TEXTURE_ID = ReimaginedIntro.id("active_frame");
    public static final int INTRO_FRAME_COUNT = 161;
    public static final int LOOP_FRAME_COUNT = 238;
    public static final long FRAME_DURATION_MS = 40; // 25 FPS
    public static final long INTRO_TOTAL_DURATION_MS = INTRO_FRAME_COUNT * FRAME_DURATION_MS; // 6440 ms

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

    public static synchronized void init() {
        if (initialized) return;

        // Dynamically detect frame resolution (e.g. 2560x1440 Standard vs 1920x1080 Lite)
        NativeImage firstFrame = loadFrame(false, 1);
        int width = firstFrame != null ? firstFrame.getWidth() : 2560;
        int height = firstFrame != null ? firstFrame.getHeight() : 1440;

        dynamicTexture = new SmoothDynamicTexture("reimagined_frame", width, height, false);
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, dynamicTexture);
        initialized = true;
        lastUploadedFrame = -1;

        if (firstFrame != null) {
            predecodedCache.put(cacheKey(false, 1), firstFrame);
        }

        // Start prefetching the next intro frames
        prefetchUpcoming(false, 2, 5);
    }

    public static void loadLastIntroFrame() {
        if (!initialized) init();
        uploadFrame(false, INTRO_FRAME_COUNT);
        lastUploadedFrame = INTRO_FRAME_COUNT;
    }

    public static void warmupLoop() {
        prefetchUpcoming(true, 1, 4);
    }

    public static void update(long elapsedMs) {
        if (!initialized) init();

        boolean inLoop = elapsedMs >= INTRO_TOTAL_DURATION_MS;
        int targetFrame;

        if (!inLoop) {
            isLoopingPhase = false;
            targetFrame = (int) (elapsedMs / FRAME_DURATION_MS) + 1;
            if (targetFrame > INTRO_FRAME_COUNT) targetFrame = INTRO_FRAME_COUNT;
        } else {
            isLoopingPhase = true;
            long loopElapsed = elapsedMs - INTRO_TOTAL_DURATION_MS;
            targetFrame = (int) ((loopElapsed / FRAME_DURATION_MS) % LOOP_FRAME_COUNT) + 1;
        }

        if (targetFrame != lastUploadedFrame) {
            uploadFrame(inLoop, targetFrame);
            lastUploadedFrame = targetFrame;

            // Free and remove any skipped or obsolete frames from off-heap memory
            pruneStaleFrames(inLoop, targetFrame);

            // Prefetch ahead by 4 frames
            prefetchUpcoming(inLoop, targetFrame + 1, targetFrame + 4);

            // If nearing end of intro, prefetch loop start frames in the background
            if (!inLoop && targetFrame >= INTRO_FRAME_COUNT - 8) {
                prefetchUpcoming(true, 1, 4);
            }
        }
    }

    private static void pruneStaleFrames(boolean isLoop, int currentFrame) {
        if (predecodedCache.isEmpty()) return;

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
                    int distance = (frame - currentFrame + LOOP_FRAME_COUNT) % LOOP_FRAME_COUNT;
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

        if (image != null && dynamicTexture != null) {
            dynamicTexture.setPixels(image);
            dynamicTexture.upload();
        }
    }

    private static void prefetchUpcoming(boolean isLoop, int startFrame, int endFrame) {
        prefetchExecutor.submit(() -> {
            int max = isLoop ? LOOP_FRAME_COUNT : INTRO_FRAME_COUNT;
            for (int f = startFrame; f <= endFrame; f++) {
                int frame = isLoop ? (((f - 1) % LOOP_FRAME_COUNT) + 1) : Math.min(f, max);
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
        String base = isLoop ? "loop" : "intro";
        String jpgPath = String.format("/assets/reimagined-intro/textures/%s/frame_%04d.jpg", base, frameIndex);
        String pngPath = String.format("/assets/reimagined-intro/textures/%s/frame_%04d.png", base, frameIndex);

        InputStream stream = ReimaginedIntro.class.getResourceAsStream(jpgPath);
        if (stream == null) {
            stream = ReimaginedIntro.class.getResourceAsStream(pngPath);
        }

        if (stream != null) {
            try (InputStream is = stream) {
                return NativeImageDecoder.decode(is);
            } catch (Throwable e) {
                ReimaginedIntro.LOGGER.error("Failed to decode frame: " + base + " #" + frameIndex, e);
            }
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

    public static void cleanup() {
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
