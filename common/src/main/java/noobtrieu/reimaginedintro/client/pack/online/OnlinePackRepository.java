package noobtrieu.reimaginedintro.client.pack.online;

import net.minecraft.client.Minecraft;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.FrameStreamer;
import noobtrieu.reimaginedintro.client.PanoramaOverlayManager;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;
import noobtrieu.reimaginedintro.client.pack.ZipIntroPack;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

/**
 * Handles asynchronous fetching of remote intro pack catalogs, thumbnails, and ZIP downloads
 * over HTTP/HTTPS (such as GitHub Raw or GitHub Releases) using standard {@code java.base} networking.
 */
public class OnlinePackRepository {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_REDIRECTS = 5;
    private static final long MAX_THUMBNAIL_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_PACK_BYTES = 512L * 1024L * 1024L;
    private static final String USER_AGENT = "ReimaginedIntro/4.0 (Minecraft Mod)";
    private static final String FALLBACK_V4_DEV_REGISTRY =
        "https://raw.githubusercontent.com/lehoangtrieu-dev/reimagined-intro/v4-dev/packs/registry.json";

    private static final ExecutorService NETWORK_EXECUTOR = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "ReimaginedIntro-Net");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile List<RemoteIntroPack> cachedCatalog = Collections.emptyList();
    private static volatile boolean fetchingCatalog = false;
    private static volatile String catalogError = null;
    private static volatile boolean catalogLoadedOnce = false;

    private static final Map<String, DownloadProgress> downloadStates = new ConcurrentHashMap<>();

    public enum Status {
        IDLE,
        DOWNLOADING,
        INSTALLING,
        COMPLETED,
        FAILED
    }

    public static class DownloadProgress {
        public volatile Status status = Status.IDLE;
        public volatile long bytesDownloaded = 0L;
        public volatile long totalBytes = -1L;
        public volatile String errorMessage = null;

        public int getPercent() {
            if (totalBytes <= 0L) return -1;
            return (int) Math.min(100L, Math.max(0L, (bytesDownloaded * 100L) / totalBytes));
        }
    }

    public static List<RemoteIntroPack> getCachedCatalog() {
        return cachedCatalog;
    }

    public static boolean isFetchingCatalog() {
        return fetchingCatalog;
    }

    public static String getCatalogError() {
        return catalogError;
    }

    public static boolean hasLoadedCatalogOnce() {
        return catalogLoadedOnce;
    }

    public static DownloadProgress getDownloadProgress(String packKey) {
        return downloadStates.get(packKey);
    }

    public static void clearFailedState(String packKey) {
        DownloadProgress p = downloadStates.get(packKey);
        if (p != null && p.status == Status.FAILED) {
            downloadStates.remove(packKey);
        }
    }

    public static void clearDownloadState(String packKey) {
        if (packKey != null) {
            downloadStates.remove(packKey);
        }
    }

    /**
     * Fetches the remote pack catalog asynchronously and invokes {@code onComplete} when finished.
     */
    public static CompletableFuture<List<RemoteIntroPack>> fetchCatalogAsync(boolean forceRefresh, Runnable onComplete) {
        if (!forceRefresh && catalogLoadedOnce && catalogError == null && !cachedCatalog.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return CompletableFuture.completedFuture(cachedCatalog);
        }
        if (fetchingCatalog) {
            return CompletableFuture.completedFuture(cachedCatalog);
        }

        fetchingCatalog = true;
        catalogError = null;

        return CompletableFuture.supplyAsync(() -> {
            String primaryUrl = ModConfig.get().getEffectiveRegistryUrl();
            try {
                List<RemoteIntroPack> packs = fetchCatalogFromUrl(primaryUrl);
                cachedCatalog = Collections.unmodifiableList(packs);
                catalogLoadedOnce = true;
                catalogError = null;
                return cachedCatalog;
            } catch (Exception primaryEx) {
                // If the default main branch URL returns 404 before v4-dev merges, try v4-dev automatically
                if (primaryUrl.equals(ModConfig.DEFAULT_REGISTRY_URL)) {
                    try {
                        List<RemoteIntroPack> fallbackPacks = fetchCatalogFromUrl(FALLBACK_V4_DEV_REGISTRY);
                        cachedCatalog = Collections.unmodifiableList(fallbackPacks);
                        catalogLoadedOnce = true;
                        catalogError = null;
                        return cachedCatalog;
                    } catch (Exception fallbackEx) {
                        ReimaginedIntro.LOGGER.warn("Fallback registry fetch failed: {}", fallbackEx.getMessage());
                    }
                }
                ReimaginedIntro.LOGGER.error("Failed to fetch online intro pack catalog from " + primaryUrl, primaryEx);
                catalogError = primaryEx.getMessage() != null ? primaryEx.getMessage() : "Connection failed";
                return cachedCatalog;
            } finally {
                fetchingCatalog = false;
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        }, NETWORK_EXECUTOR);
    }

    private static List<RemoteIntroPack> fetchCatalogFromUrl(String rawUrl) throws IOException {
        HttpURLConnection conn = openValidatedConnection(rawUrl);
        try (InputStream is = new BufferedInputStream(conn.getInputStream());
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return RemoteIntroPack.parseRegistry(reader);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Downloads raw thumbnail bytes asynchronously up to {@link #MAX_THUMBNAIL_BYTES}.
     */
    public static CompletableFuture<byte[]> downloadThumbnailAsync(String rawUrl) {
        return CompletableFuture.supplyAsync(() -> {
            if (rawUrl == null || rawUrl.trim().isEmpty()) {
                return null;
            }
            try {
                HttpURLConnection conn = openValidatedConnection(rawUrl);
                try (InputStream is = conn.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192];
                    long total = 0;
                    int read;
                    while ((read = is.read(buf)) != -1) {
                        total += read;
                        if (total > MAX_THUMBNAIL_BYTES) {
                            throw new IOException("Thumbnail exceeds size limit");
                        }
                        out.write(buf, 0, read);
                    }
                    return out.toByteArray();
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                ReimaginedIntro.LOGGER.debug("Failed to load remote thumbnail from {}: {}", rawUrl, e.getMessage());
                return null;
            }
        }, NETWORK_EXECUTOR);
    }

    /**
     * Downloads and installs a {@link RemoteIntroPack} into the local {@code intros/} directory.
     */
    public static CompletableFuture<Boolean> downloadPackAsync(RemoteIntroPack pack, Runnable onStateChanged) {
        String key = pack.getId();
        return downloadZipInternalAsync(
            key,
            pack.getDownloadUrl(),
            key + ".zip",
            pack.getSha256(),
            pack.getId(),
            onStateChanged
        );
    }

    /**
     * Downloads and installs a pack ZIP directly from any user-provided HTTP/HTTPS URL (e.g. GitHub Release asset).
     */
    public static CompletableFuture<Boolean> downloadFromDirectUrlAsync(String rawUrl, Runnable onStateChanged) {
        String normalized = normalizeUserUrl(rawUrl);
        String fileName = inferZipFileName(normalized);
        String key = "url_" + Integer.toHexString(normalized.hashCode());
        return downloadZipInternalAsync(key, normalized, fileName, null, null, onStateChanged);
    }

    private static CompletableFuture<Boolean> downloadZipInternalAsync(
        String stateKey,
        String downloadUrl,
        String defaultFileName,
        String expectedSha256,
        String replacePackId,
        Runnable onStateChanged
    ) {
        DownloadProgress existing = downloadStates.get(stateKey);
        if (existing != null && (existing.status == Status.DOWNLOADING || existing.status == Status.INSTALLING)) {
            return CompletableFuture.completedFuture(false);
        }

        DownloadProgress progress = new DownloadProgress();
        progress.status = Status.DOWNLOADING;
        downloadStates.put(stateKey, progress);
        if (onStateChanged != null) onStateChanged.run();

        return CompletableFuture.supplyAsync(() -> {
            Path introsDir = IntroPackManager.getIntrosDir();
            Path partFile = null;
            try {
                Files.createDirectories(introsDir);
                String safeName = sanitizeZipFileName(defaultFileName);
                partFile = introsDir.resolve(".downloading_" + safeName + ".part");

                HttpURLConnection conn = openValidatedConnection(downloadUrl);
                try {
                    long contentLen = conn.getContentLengthLong();
                    progress.totalBytes = contentLen;

                    MessageDigest sha256Digest = (expectedSha256 != null && !expectedSha256.isEmpty())
                        ? MessageDigest.getInstance("SHA-256")
                        : null;

                    try (InputStream in = new BufferedInputStream(conn.getInputStream());
                         OutputStream out = Files.newOutputStream(partFile)) {
                        byte[] buf = new byte[16 * 1024];
                        int read;
                        long lastNotify = 0L;
                        while ((read = in.read(buf)) != -1) {
                            progress.bytesDownloaded += read;
                            if (progress.bytesDownloaded > MAX_PACK_BYTES) {
                                throw new IOException("Pack exceeds 512 MB limit");
                            }
                            out.write(buf, 0, read);
                            if (sha256Digest != null) {
                                sha256Digest.update(buf, 0, read);
                            }
                            long now = System.currentTimeMillis();
                            if (onStateChanged != null && now - lastNotify > 150L) {
                                lastNotify = now;
                                onStateChanged.run();
                            }
                        }
                    }

                    if (sha256Digest != null) {
                        String actualHash = bytesToHex(sha256Digest.digest());
                        if (!actualHash.equalsIgnoreCase(expectedSha256)) {
                            throw new IOException("Checksum mismatch (SHA-256)");
                        }
                    }
                } finally {
                    conn.disconnect();
                }

                progress.status = Status.INSTALLING;
                if (onStateChanged != null) onStateChanged.run();

                validateZipFile(partFile);

                final Path finalPart = partFile;
                final Path defaultTarget = introsDir.resolve(safeName);
                Runnable installTask = () -> {
                    try {
                        Path targetFile = defaultTarget;
                        if (replacePackId != null) {
                            noobtrieu.reimaginedintro.client.pack.FrameCacheManager.invalidatePackCache(replacePackId);
                            IntroPack existingPack = IntroPackManager.getPack(replacePackId);
                            if (existingPack instanceof ZipIntroPack zipPack) {
                                if (zipPack.getZipFile() != null) {
                                    targetFile = zipPack.getZipFile().toPath();
                                }
                                if (IntroPackManager.getActivePack() == existingPack) {
                                    FrameStreamer.cleanup();
                                    PanoramaOverlayManager.cleanup();
                                }
                                zipPack.close();
                            }
                        }
                        Files.move(finalPart, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        IntroPackManager.reloadPacks();
                        ReimaginedIntro.LOGGER.info("Downloaded and installed intro pack: {}", targetFile.getFileName());
                    } catch (IOException ioEx) {
                        throw new RuntimeException(ioEx);
                    }
                };

                Minecraft mc = Minecraft.getInstance();
                if (mc != null && !mc.isSameThread()) {
                    CompletableFuture.runAsync(installTask, mc).join();
                } else {
                    installTask.run();
                }
                partFile = null;

                progress.status = Status.COMPLETED;
                if (onStateChanged != null) onStateChanged.run();
                return true;
            } catch (Exception e) {
                ReimaginedIntro.LOGGER.error("Failed to download intro pack from " + downloadUrl, e);
                progress.status = Status.FAILED;
                progress.errorMessage = e.getMessage() != null ? e.getMessage() : "Download failed";
                if (partFile != null) {
                    try {
                        Files.deleteIfExists(partFile);
                    } catch (IOException ignored) {}
                }
                if (onStateChanged != null) onStateChanged.run();
                return false;
            }
        }, NETWORK_EXECUTOR);
    }

    public static void validateZipFile(Path zipPath) throws IOException {
        if (!Files.exists(zipPath) || Files.size(zipPath) == 0L) {
            throw new IOException("Downloaded file is empty");
        }
        try (ZipFile zf = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            if (!zf.entries().hasMoreElements()) {
                throw new IOException("Downloaded ZIP archive is empty");
            }
        } catch (IOException e) {
            throw new IOException("Invalid ZIP archive: " + e.getMessage(), e);
        }
    }

    public static String normalizeUserUrl(String rawUrl) {
        if (rawUrl == null) return "";
        String trimmed = rawUrl.trim();
        // Convert GitHub file viewer URLs (github.com/owner/repo/blob/branch/path) into raw URLs automatically
        if (trimmed.startsWith("https://github.com/") && trimmed.contains("/blob/")) {
            trimmed = trimmed.replaceFirst("^https://github\\.com/", "https://raw.githubusercontent.com/")
                .replaceFirst("/blob/", "/");
        }
        return trimmed;
    }

    public static URI validateHttpUri(String rawUrl) throws IOException {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new IOException("Empty URL");
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                throw new IOException("Only HTTP/HTTPS URLs are supported");
            }
            if (uri.getHost() == null || uri.getHost().isEmpty()) {
                throw new IOException("Invalid URL host");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IOException("Malformed URL: " + e.getMessage(), e);
        }
    }

    private static HttpURLConnection openValidatedConnection(String rawUrl) throws IOException {
        String currentUrl = rawUrl;
        for (int hops = 0; hops <= MAX_REDIRECTS; hops++) {
            URI uri = validateHttpUri(currentUrl);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "*/*");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == 307
                || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new IOException("Redirect without Location header");
                }
                currentUrl = uri.resolve(location).toString();
                continue;
            }

            if (code < 200 || code >= 300) {
                conn.disconnect();
                throw new IOException("HTTP " + code);
            }
            return conn;
        }
        throw new IOException("Too many redirects");
    }

    public static String inferZipFileName(String rawUrl) {
        try {
            String path = URI.create(rawUrl).getPath();
            if (path != null) {
                int slash = path.lastIndexOf('/');
                String last = slash >= 0 ? path.substring(slash + 1) : path;
                if (!last.isEmpty()) {
                    return sanitizeZipFileName(last);
                }
            }
        } catch (Exception ignored) {}
        return "downloaded_intro_" + System.currentTimeMillis() + ".zip";
    }

    public static String sanitizeZipFileName(String rawName) {
        String cleaned = (rawName == null ? "" : rawName.trim()).replaceAll("[^a-zA-Z0-9_.-]", "_");
        cleaned = cleaned.replaceAll("\\.\\.+", "_");
        if (cleaned.isEmpty() || cleaned.equals(".zip")) {
            cleaned = "intro_pack";
        }
        if (!cleaned.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            cleaned += ".zip";
        }
        return cleaned;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
