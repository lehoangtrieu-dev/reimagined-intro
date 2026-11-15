package noobtrieu.reimaginedintro.client.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Target video resolution presets for intro and loop frame playback.
 */
public enum VideoResolution {
    ORIGINAL("original", "Resolution: Max", Integer.MAX_VALUE),
    P1080("1080p", "Resolution: 1080p", 1080),
    P720("720p", "Resolution: 720p", 720),
    P480("480p", "Resolution: 480p", 480);

    private final String id;
    private final String label;
    private final int maxHeight;

    VideoResolution(String id, String label, int maxHeight) {
        this.id = id;
        this.label = label;
        this.maxHeight = maxHeight;
    }

    public String getId() {
        return this.id;
    }

    public String getLabel() {
        return this.label;
    }

    public int getMaxHeight() {
        return this.maxHeight;
    }

    public boolean needsDownscale(int srcHeight) {
        return this != ORIGINAL && srcHeight > this.maxHeight;
    }

    /**
     * Returns {@link #ORIGINAL} plus only the presets strictly smaller than {@code srcHeight}.
     */
    public static List<VideoResolution> getAvailableResolutions(int srcHeight) {
        if (srcHeight <= 0) {
            return List.of(ORIGINAL);
        }
        List<VideoResolution> result = new ArrayList<>();
        for (VideoResolution res : values()) {
            if (res == ORIGINAL || res.needsDownscale(srcHeight)) {
                result.add(res);
            }
        }
        return result;
    }

    /**
     * Falls back to {@link #ORIGINAL} if this preset is greater than or equal to {@code srcHeight}.
     */
    public VideoResolution clampToSourceHeight(int srcHeight) {
        if (this == ORIGINAL || srcHeight <= 0 || needsDownscale(srcHeight)) {
            return this;
        }
        return ORIGINAL;
    }

    /**
     * Computes target width and height while preserving aspect ratio and never upscaling.
     */
    public int[] computeTargetDimensions(int srcWidth, int srcHeight) {
        if (srcWidth <= 0 || srcHeight <= 0 || !needsDownscale(srcHeight)) {
            return new int[]{srcWidth, srcHeight};
        }
        int targetHeight = this.maxHeight;
        int targetWidth = Math.max(1, Math.round((float) srcWidth * targetHeight / (float) srcHeight));
        return new int[]{targetWidth, targetHeight};
    }

    public static VideoResolution fromId(String id) {
        if (id != null) {
            for (VideoResolution res : values()) {
                if (res.id.equalsIgnoreCase(id.trim())) {
                    return res;
                }
            }
        }
        return ORIGINAL;
    }
}
