package noobtrieu.reimaginedintro.client.pack;

import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.config.VideoResolution;
import noobtrieu.reimaginedintro.platform.Services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates, stores, and serves disk-cached downscaled JPEG frames outside {@code intros/},
 * using {@link StbFrameTranscoder} for image resizing and encoding.
 */
public final class FrameCacheManager {
    private static final int FORMAT_VERSION = 1;
    private static final int JPEG_QUALITY = 90;
    private static final String META_FILE = ".cache_meta";
    private static final String PENDING_FILE = ".cache_pending";
    private static final int WORKER_COUNT = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));

    private static volatile Path cacheRootOverride;

    private static final ExecutorService COORDINATOR_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ReimaginedIntro-CacheCoordinator");
        t.setDaemon(true);
        return t;
    });

    private static final ExecutorService WORKER_EXECUTOR = Executors.newFixedThreadPool(WORKER_COUNT, r -> {
        Thread t = new Thread(r, "ReimaginedIntro-FrameScaler");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<String, CacheProgress> PROGRESS_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<Void>> ACTIVE_JOBS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicBoolean> CANCELLATION_FLAGS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CacheMeta> VERIFIED_META = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, int[]> PACK_DIMENSIONS = new ConcurrentHashMap<>();

    private FrameCacheManager() {}

    public enum Status {
        IDLE,
        GENERATING,
        READY,
        FAILED
    }

    public static final class CacheProgress {
        public volatile Status status = Status.IDLE;
        public volatile int completedFrames = 0;
        public volatile int totalFrames = 0;
        public volatile boolean passthrough = false;
        public volatile String errorMessage;
        public volatile String packId;
        public volatile String packName;
        public volatile VideoResolution resolution;

        public int getPercent() {
            if (status == Status.READY) return 100;
            int total = this.totalFrames;
            if (total <= 0) return 0;
            return Math.min(100, (this.completedFrames * 100) / total);
        }
    }

    private record CacheMeta(String fingerprint, boolean passthrough) {}

    static synchronized void setCacheRootForTest(Path path) {
        cacheRootOverride = path;
        VERIFIED_META.clear();
        PROGRESS_MAP.clear();
        PACK_DIMENSIONS.clear();
    }

    static synchronized void resetCacheRootForTest() {
        cacheRootOverride = null;
        VERIFIED_META.clear();
        PROGRESS_MAP.clear();
        PACK_DIMENSIONS.clear();
    }

    public static Path getCacheRoot() {
        if (cacheRootOverride != null) {
            return cacheRootOverride;
        }
        return Services.PLATFORM.getGameDir().resolve("cache").resolve("reimagined-intro");
    }

    static String sanitizeSegment(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String taskKey(String packId, VideoResolution resolution) {
        return packId + "::" + resolution.getId();
    }

    public static Path getPackCacheDir(String packId) {
        return getCacheRoot().resolve(sanitizeSegment(packId));
    }

    public static Path getResolutionCacheDir(String packId, VideoResolution resolution) {
        return getPackCacheDir(packId).resolve(resolution.getId());
    }

    static String buildFingerprint(IntroPack pack, VideoResolution resolution) {
        return buildPackContentKey(pack)
            + "|" + resolution.getId()
            + "|v" + FORMAT_VERSION;
    }

    private static String buildPackContentKey(IntroPack pack) {
        String version = (pack.getManifest() != null && pack.getManifest().getVersion() != null)
            ? pack.getManifest().getVersion()
            : "0";
        int introCount = pack.hasIntro() ? pack.getIntroFrameCount() : 0;
        int loopCount = pack.hasLoop() ? pack.getLoopFrameCount() : 0;
        return pack.getId() + "|" + version + "|" + introCount + "|" + loopCount;
    }

    /**
     * Returns the cached {@code {width, height}} of the pack's first video frame, or {@code null}
     * if the pack has no video frames or dimensions cannot be probed.
     */
    public static int[] getPackSourceDimensions(IntroPack pack) {
        if (pack == null) return null;
        int introCount = pack.hasIntro() ? pack.getIntroFrameCount() : 0;
        int loopCount = pack.hasLoop() ? pack.getLoopFrameCount() : 0;
        if (introCount <= 0 && loopCount <= 0) {
            return null;
        }
        String key = buildPackContentKey(pack);
        int[] cached = PACK_DIMENSIONS.get(key);
        if (cached != null) {
            return cached.clone();
        }
        int[] probed = probeFirstFrameDimensions(pack, introCount, loopCount);
        if (probed != null && probed.length >= 2 && probed[0] > 0 && probed[1] > 0) {
            int[] stored = new int[]{probed[0], probed[1]};
            PACK_DIMENSIONS.put(key, stored);
            return stored.clone();
        }
        return null;
    }

    /**
     * Returns {@link VideoResolution#ORIGINAL} plus presets strictly lower than the pack's source height.
     */
    public static List<VideoResolution> getAvailableResolutions(IntroPack pack) {
        if (pack == null || countPackFrames(pack) <= 0) {
            return List.of(VideoResolution.ORIGINAL);
        }
        int[] dims = getPackSourceDimensions(pack);
        if (dims == null) {
            return Arrays.asList(VideoResolution.values());
        }
        return VideoResolution.getAvailableResolutions(dims[1]);
    }

    /**
     * Clamps {@code desired} to {@link VideoResolution#ORIGINAL} when the active pack's source height
     * is already less than or equal to {@code desired}.
     */
    public static VideoResolution clampResolutionForPack(IntroPack pack, VideoResolution desired) {
        if (desired == null || desired == VideoResolution.ORIGINAL) {
            return VideoResolution.ORIGINAL;
        }
        if (pack == null || countPackFrames(pack) <= 0) {
            return VideoResolution.ORIGINAL;
        }
        int[] dims = getPackSourceDimensions(pack);
        if (dims == null) {
            return desired;
        }
        return desired.clampToSourceHeight(dims[1]);
    }

    public static CacheProgress getProgress(IntroPack pack, VideoResolution resolution) {
        if (pack == null || resolution == null || resolution == VideoResolution.ORIGINAL) {
            return null;
        }
        return PROGRESS_MAP.get(taskKey(pack.getId(), resolution));
    }

    public static CacheProgress getActiveGeneratingProgress() {
        for (CacheProgress p : PROGRESS_MAP.values()) {
            if (p.status == Status.GENERATING) {
                return p;
            }
        }
        return null;
    }

    public static boolean isCacheReady(IntroPack pack, VideoResolution resolution) {
        if (pack == null || resolution == null || resolution == VideoResolution.ORIGINAL) {
            return true;
        }
        return resolveValidMeta(pack, resolution) != null;
    }

    /**
     * Opens a cached downscaled frame stream if valid cache exists and downscaling was required.
     * Returns {@code null} for {@link VideoResolution#ORIGINAL}, passthrough packs, or incomplete caches.
     */
    public static InputStream openCachedFrame(IntroPack pack, VideoResolution resolution, boolean isLoop, int frameIndex) {
        if (pack == null || resolution == null || resolution == VideoResolution.ORIGINAL) {
            return null;
        }
        CacheMeta meta = resolveValidMeta(pack, resolution);
        if (meta == null || meta.passthrough()) {
            return null;
        }

        Path framePath = frameFilePath(getResolutionCacheDir(pack.getId(), resolution), isLoop, frameIndex);
        if (!Files.isRegularFile(framePath)) {
            return null;
        }
        try {
            return Files.newInputStream(framePath);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Asynchronously probes and generates cached downscaled JPEG frames for the given pack and resolution.
     */
    public static CompletableFuture<Void> prepareCacheAsync(IntroPack pack, VideoResolution resolution, Runnable onUpdate) {
        if (pack == null || resolution == null || resolution == VideoResolution.ORIGINAL) {
            return CompletableFuture.completedFuture(null);
        }

        String key = taskKey(pack.getId(), resolution);
        cancelOtherResolutionsForPack(pack.getId(), key);
        CompletableFuture<Void> existing = ACTIVE_JOBS.get(key);
        if (existing != null && !existing.isDone()) {
            return existing;
        }

        CacheMeta existingMeta = resolveValidMeta(pack, resolution);
        int totalFrames = countPackFrames(pack);
        if (existingMeta != null) {
            CacheProgress ready = PROGRESS_MAP.computeIfAbsent(key, k -> new CacheProgress());
            ready.packId = pack.getId();
            ready.packName = pack.getName();
            ready.resolution = resolution;
            ready.totalFrames = totalFrames;
            ready.completedFrames = totalFrames;
            ready.passthrough = existingMeta.passthrough();
            ready.errorMessage = null;
            ready.status = Status.READY;
            if (onUpdate != null) onUpdate.run();
            return CompletableFuture.completedFuture(null);
        }

        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        CANCELLATION_FLAGS.put(key, cancelFlag);

        CacheProgress progress = new CacheProgress();
        progress.packId = pack.getId();
        progress.packName = pack.getName();
        progress.resolution = resolution;
        progress.totalFrames = totalFrames;
        progress.completedFrames = 0;
        progress.status = Status.GENERATING;
        PROGRESS_MAP.put(key, progress);
        if (onUpdate != null) onUpdate.run();

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                generateCacheBlocking(pack, resolution, progress, cancelFlag, onUpdate);
            } catch (Throwable t) {
                if (!cancelFlag.get()) {
                    ReimaginedIntro.LOGGER.error("Failed generating {} frame cache for pack {}", resolution.getLabel(), pack.getId(), t);
                    progress.errorMessage = t.getMessage() != null ? t.getMessage() : "Cache generation failed";
                    progress.status = Status.FAILED;
                }
            } finally {
                ACTIVE_JOBS.remove(key);
                if (onUpdate != null) onUpdate.run();
            }
        }, COORDINATOR_EXECUTOR);

        ACTIVE_JOBS.put(key, future);
        return future;
    }

    /**
     * Cancels active cache jobs and deletes all cached frames for {@code packId}.
     */
    public static void invalidatePackCache(String packId) {
        if (packId == null || packId.isBlank()) return;
        String prefix = packId + "::";
        cancelMatchingJobs(prefix);
        VERIFIED_META.keySet().removeIf(k -> k.startsWith(prefix));
        PROGRESS_MAP.keySet().removeIf(k -> k.startsWith(prefix));
        PACK_DIMENSIONS.keySet().removeIf(k -> k.startsWith(packId + "|"));

        deleteDirectoryQuietly(getPackCacheDir(packId));
    }

    /**
     * Cancels all active cache jobs and deletes the entire frame cache directory.
     */
    public static void clearAllCache() {
        cancelMatchingJobs(null);
        VERIFIED_META.clear();
        PROGRESS_MAP.clear();
        PACK_DIMENSIONS.clear();
        deleteDirectoryQuietly(getCacheRoot());
    }

    public static long getTotalCacheBytes() {
        Path root = getCacheRoot();
        if (!Files.exists(root)) return 0L;
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0L;
                    }
                })
                .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    static byte[] transcodeJpegToResolution(byte[] encodedBytes, VideoResolution resolution) {
        return StbFrameTranscoder.transcodeJpeg(encodedBytes, resolution, JPEG_QUALITY);
    }

    private static void generateCacheBlocking(
        IntroPack pack,
        VideoResolution resolution,
        CacheProgress progress,
        AtomicBoolean cancelled,
        Runnable onUpdate
    ) throws IOException {
        String key = taskKey(pack.getId(), resolution);
        String fingerprint = buildFingerprint(pack, resolution);
        Path resDir = getResolutionCacheDir(pack.getId(), resolution);

        int introCount = pack.hasIntro() ? pack.getIntroFrameCount() : 0;
        int loopCount = pack.hasLoop() ? pack.getLoopFrameCount() : 0;
        int totalFrames = introCount + loopCount;
        progress.totalFrames = totalFrames;

        if (totalFrames <= 0) {
            Files.createDirectories(resDir);
            writeMeta(resDir, fingerprint, true);
            VERIFIED_META.put(key, new CacheMeta(fingerprint, true));
            progress.passthrough = true;
            progress.status = Status.READY;
            return;
        }

        int[] sampleDims = getPackSourceDimensions(pack);
        if (cancelled.get()) return;

        if (sampleDims == null) {
            throw new IOException("Unable to read sample frame dimensions for pack " + pack.getId());
        }

        if (!resolution.needsDownscale(sampleDims[1])) {
            deleteDirectoryQuietly(resDir);
            Files.createDirectories(resDir);
            writeMeta(resDir, fingerprint, true);
            VERIFIED_META.put(key, new CacheMeta(fingerprint, true));
            progress.passthrough = true;
            progress.completedFrames = totalFrames;
            progress.status = Status.READY;
            ReimaginedIntro.LOGGER.info(
                "Pack {} ({}x{}) already fits {}; skipping disk transcode",
                pack.getId(), sampleDims[0], sampleDims[1], resolution.getLabel()
            );
            return;
        }

        preparePendingDirectory(resDir, fingerprint);

        Path introDir = resDir.resolve("intro");
        Path loopDir = resDir.resolve("loop");
        if (introCount > 0) Files.createDirectories(introDir);
        if (loopCount > 0) Files.createDirectories(loopDir);

        AtomicInteger completed = new AtomicInteger(0);
        AtomicBoolean anyFailed = new AtomicBoolean(false);
        List<CompletableFuture<Void>> frameTasks = new ArrayList<>(totalFrames);

        for (int i = 1; i <= introCount; i++) {
            final int idx = i;
            frameTasks.add(submitFrameTask(pack, resolution, false, idx, resDir, progress, completed, cancelled, anyFailed, onUpdate));
        }
        for (int i = 1; i <= loopCount; i++) {
            final int idx = i;
            frameTasks.add(submitFrameTask(pack, resolution, true, idx, resDir, progress, completed, cancelled, anyFailed, onUpdate));
        }

        CompletableFuture.allOf(frameTasks.toArray(new CompletableFuture[0])).join();

        if (cancelled.get()) {
            progress.status = Status.IDLE;
            return;
        }
        if (anyFailed.get()) {
            throw new IOException("One or more frames failed to transcode for " + pack.getId());
        }

        writeMeta(resDir, fingerprint, false);
        Files.deleteIfExists(resDir.resolve(PENDING_FILE));
        VERIFIED_META.put(key, new CacheMeta(fingerprint, false));
        progress.passthrough = false;
        progress.completedFrames = totalFrames;
        progress.status = Status.READY;
        ReimaginedIntro.LOGGER.info(
            "Cached {} downscaled {} frames for pack {}",
            totalFrames, resolution.getLabel(), pack.getId()
        );
    }

    private static CompletableFuture<Void> submitFrameTask(
        IntroPack pack,
        VideoResolution resolution,
        boolean isLoop,
        int frameIndex,
        Path resDir,
        CacheProgress progress,
        AtomicInteger completed,
        AtomicBoolean cancelled,
        AtomicBoolean anyFailed,
        Runnable onUpdate
    ) {
        return CompletableFuture.runAsync(() -> {
            if (cancelled.get() || anyFailed.get()) return;

            Path dest = frameFilePath(resDir, isLoop, frameIndex);
            try {
                if (Files.isRegularFile(dest) && Files.size(dest) > 0L) {
                    progress.completedFrames = completed.incrementAndGet();
                    if (onUpdate != null && (progress.completedFrames % 10 == 0 || progress.completedFrames == progress.totalFrames)) {
                        onUpdate.run();
                    }
                    return;
                }

                byte[] originalBytes;
                try (InputStream is = isLoop ? pack.openLoopFrame(frameIndex) : pack.openIntroFrame(frameIndex)) {
                    if (is == null) {
                        throw new IOException("Missing frame stream: " + (isLoop ? "loop" : "intro") + " #" + frameIndex);
                    }
                    originalBytes = is.readAllBytes();
                }

                if (cancelled.get() || anyFailed.get()) return;

                byte[] resizedJpeg = StbFrameTranscoder.transcodeJpeg(originalBytes, resolution, JPEG_QUALITY);
                if (resizedJpeg == null) {
                    throw new IOException("STB transcode returned null for frame #" + frameIndex);
                }

                Path tmp = dest.resolveSibling(dest.getFileName() + ".tmp");
                Files.write(tmp, resizedJpeg);
                try {
                    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicUnsupported) {
                    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
                }

                int done = completed.incrementAndGet();
                progress.completedFrames = done;
                if (onUpdate != null && (done % 10 == 0 || done == progress.totalFrames)) {
                    onUpdate.run();
                }
            } catch (Throwable t) {
                anyFailed.set(true);
                ReimaginedIntro.LOGGER.error(
                    "Failed caching {} frame #{} for pack {}",
                    isLoop ? "loop" : "intro", frameIndex, pack.getId(), t
                );
            }
        }, WORKER_EXECUTOR);
    }

    private static int[] probeFirstFrameDimensions(IntroPack pack, int introCount, int loopCount) {
        byte[] sample = null;
        try {
            if (introCount > 0) {
                try (InputStream is = pack.openIntroFrame(1)) {
                    if (is != null) sample = is.readAllBytes();
                }
            }
            if (sample == null && loopCount > 0) {
                try (InputStream is = pack.openLoopFrame(1)) {
                    if (is != null) sample = is.readAllBytes();
                }
            }
        } catch (IOException e) {
            return null;
        }
        return StbFrameTranscoder.probeDimensions(sample);
    }

    private static CacheMeta resolveValidMeta(IntroPack pack, VideoResolution resolution) {
        String key = taskKey(pack.getId(), resolution);
        String expectedFp = buildFingerprint(pack, resolution);

        CacheMeta cached = VERIFIED_META.get(key);
        if (cached != null) {
            if (expectedFp.equals(cached.fingerprint())) {
                return cached;
            }
            VERIFIED_META.remove(key);
        }

        Path metaFile = getResolutionCacheDir(pack.getId(), resolution).resolve(META_FILE);
        if (!Files.isRegularFile(metaFile)) {
            return null;
        }

        try {
            String content = Files.readString(metaFile, StandardCharsets.UTF_8);
            String fp = null;
            boolean passthrough = false;
            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("fingerprint=")) {
                    fp = trimmed.substring("fingerprint=".length());
                } else if (trimmed.startsWith("passthrough=")) {
                    passthrough = Boolean.parseBoolean(trimmed.substring("passthrough=".length()));
                }
            }
            if (expectedFp.equals(fp)) {
                CacheMeta meta = new CacheMeta(fp, passthrough);
                VERIFIED_META.put(key, meta);
                return meta;
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static void preparePendingDirectory(Path resDir, String fingerprint) throws IOException {
        Path pendingFile = resDir.resolve(PENDING_FILE);
        Path metaFile = resDir.resolve(META_FILE);
        boolean resumable = false;
        if (Files.isRegularFile(pendingFile) && !Files.exists(metaFile)) {
            try {
                String existing = Files.readString(pendingFile, StandardCharsets.UTF_8).trim();
                resumable = fingerprint.equals(existing);
            } catch (IOException ignored) {}
        }

        if (!resumable) {
            deleteDirectoryQuietly(resDir);
            Files.createDirectories(resDir);
            Files.writeString(pendingFile, fingerprint, StandardCharsets.UTF_8);
        }
    }

    private static void writeMeta(Path resDir, String fingerprint, boolean passthrough) throws IOException {
        Path metaFile = resDir.resolve(META_FILE);
        Path tmp = resDir.resolve(META_FILE + ".tmp");
        String content = "fingerprint=" + fingerprint + "\npassthrough=" + passthrough + "\n";
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, metaFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, metaFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path frameFilePath(Path resDir, boolean isLoop, int frameIndex) {
        return resDir.resolve(isLoop ? "loop" : "intro")
            .resolve(String.format(Locale.ROOT, "frame_%04d.jpg", frameIndex));
    }

    private static int countPackFrames(IntroPack pack) {
        int intro = pack.hasIntro() ? pack.getIntroFrameCount() : 0;
        int loop = pack.hasLoop() ? pack.getLoopFrameCount() : 0;
        return intro + loop;
    }

    private static void cancelOtherResolutionsForPack(String packId, String activeKey) {
        String prefix = packId + "::";
        for (Map.Entry<String, AtomicBoolean> entry : CANCELLATION_FLAGS.entrySet()) {
            if (entry.getKey().startsWith(prefix) && !entry.getKey().equals(activeKey)) {
                entry.getValue().set(true);
            }
        }
    }

    private static void cancelMatchingJobs(String prefixOrNull) {
        for (Map.Entry<String, AtomicBoolean> entry : CANCELLATION_FLAGS.entrySet()) {
            if (prefixOrNull == null || entry.getKey().startsWith(prefixOrNull)) {
                entry.getValue().set(true);
            }
        }
        List<CompletableFuture<Void>> toWait = new ArrayList<>();
        for (Map.Entry<String, CompletableFuture<Void>> entry : ACTIVE_JOBS.entrySet()) {
            if (prefixOrNull == null || entry.getKey().startsWith(prefixOrNull)) {
                toWait.add(entry.getValue());
            }
        }
        for (CompletableFuture<Void> job : toWait) {
            try {
                job.get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
    }

    private static void deleteDirectoryQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
