package noobtrieu.reimaginedintro.client.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Target video frame-rate presets for intro and loop playback.
 * Lowering FPS samples fewer source frames over the same duration so audio stays in sync.
 */
public enum VideoFps {
    ORIGINAL("original", "FPS: Max", Integer.MAX_VALUE),
    FPS_60("60", "FPS: 60", 60),
    FPS_30("30", "FPS: 30", 30),
    FPS_15("15", "FPS: 15", 15);

    private final String id;
    private final String label;
    private final int maxFps;

    VideoFps(String id, String label, int maxFps) {
        this.id = id;
        this.label = label;
        this.maxFps = maxFps;
    }

    public String getId() {
        return this.id;
    }

    public String getLabel() {
        return this.label;
    }

    public int getMaxFps() {
        return this.maxFps;
    }

    public boolean needsDownsample(int srcFps) {
        return this != ORIGINAL && srcFps > this.maxFps;
    }

    public int getEffectiveFps(int srcFps) {
        int safeSrcFps = Math.max(1, srcFps);
        return needsDownsample(safeSrcFps) ? this.maxFps : safeSrcFps;
    }

    /**
     * Returns {@link #ORIGINAL} plus only the presets strictly lower than {@code srcFps}.
     */
    public static List<VideoFps> getAvailableFps(int srcFps, boolean hasFrames) {
        if (!hasFrames || srcFps <= 0) {
            return List.of(ORIGINAL);
        }
        List<VideoFps> result = new ArrayList<>();
        for (VideoFps fps : values()) {
            if (fps == ORIGINAL || fps.needsDownsample(srcFps)) {
                result.add(fps);
            }
        }
        return result;
    }

    /**
     * Falls back to {@link #ORIGINAL} when this preset is greater than or equal to {@code srcFps}.
     */
    public VideoFps clampToSourceFps(int srcFps) {
        if (this == ORIGINAL || srcFps <= 0 || needsDownsample(srcFps)) {
            return this;
        }
        return ORIGINAL;
    }

    /**
     * Computes the 0-based sampled step index at {@code elapsedMs}, wrapped for looping sequences.
     */
    public long computeSampleStep(long elapsedMs, int srcFps, int srcFrameCount, boolean isLoop) {
        if (srcFrameCount <= 0) {
            return 0L;
        }
        int safeSrcFps = Math.max(1, srcFps);
        int effectiveFps = getEffectiveFps(safeSrcFps);
        long safeElapsed = Math.max(0L, elapsedMs);
        long step = (safeElapsed * effectiveFps) / 1000L;
        if (!isLoop) {
            return step;
        }
        int totalLoopSteps = getTotalSampleSteps(safeSrcFps, srcFrameCount);
        return step % totalLoopSteps;
    }

    public int getTotalSampleSteps(int srcFps, int srcFrameCount) {
        if (srcFrameCount <= 0) {
            return 0;
        }
        int safeSrcFps = Math.max(1, srcFps);
        int effectiveFps = getEffectiveFps(safeSrcFps);
        if (effectiveFps >= safeSrcFps) {
            return srcFrameCount;
        }
        return Math.max(1, (int) Math.round(srcFrameCount * ((double) effectiveFps / safeSrcFps)));
    }

    /**
     * Maps a 0-based sampled step index to a 1-based source frame index in {@code [1, srcFrameCount]}.
     */
    public int mapStepToSourceFrame(long sampleStep, int srcFps, int srcFrameCount, boolean isLoop) {
        if (srcFrameCount <= 0) {
            return 0;
        }
        int safeSrcFps = Math.max(1, srcFps);
        int effectiveFps = getEffectiveFps(safeSrcFps);
        long step = Math.max(0L, sampleStep);
        if (isLoop) {
            int totalSteps = getTotalSampleSteps(safeSrcFps, srcFrameCount);
            step = step % totalSteps;
        }
        if (effectiveFps >= safeSrcFps) {
            return (int) Math.min(srcFrameCount, step + 1L);
        }
        int mapped = 1 + (int) Math.floor(step * ((double) safeSrcFps / effectiveFps));
        return Math.max(1, Math.min(srcFrameCount, mapped));
    }

    public static VideoFps fromId(String id) {
        if (id != null) {
            for (VideoFps fps : values()) {
                if (fps.id.equalsIgnoreCase(id.trim())) {
                    return fps;
                }
            }
        }
        return ORIGINAL;
    }
}
