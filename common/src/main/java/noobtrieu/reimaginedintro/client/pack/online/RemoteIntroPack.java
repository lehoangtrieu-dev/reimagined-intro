package noobtrieu.reimaginedintro.client.pack.online;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Metadata entry for a remotely hosted intro pack in an online registry JSON.
 */
public class RemoteIntroPack {
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("author")
    private String author = "Unknown";

    @SerializedName("version")
    private String version = "1.0.0";

    @SerializedName("description")
    private String description = "";

    @SerializedName("fps")
    private int fps = 25;

    @SerializedName("official")
    private boolean official = false;

    @SerializedName("has_intro")
    private boolean hasIntro = true;

    @SerializedName("has_loop")
    private boolean hasLoop = false;

    @SerializedName("has_audio")
    private boolean hasAudio = false;

    @SerializedName("has_panorama")
    private boolean hasPanorama = false;

    @SerializedName("has_panorama_overlay")
    private boolean hasPanoramaOverlay = false;

    @SerializedName("thumbnail_url")
    private String thumbnailUrl;

    @SerializedName("download_url")
    private String downloadUrl;

    @SerializedName("file_size")
    private long fileSizeBytes = 0L;

    @SerializedName("sha256")
    private String sha256;

    public static class RegistryResponse {
        @SerializedName("format_version")
        public int formatVersion = 1;

        @SerializedName("packs")
        public List<RemoteIntroPack> packs = new ArrayList<>();
    }

    /**
     * Parses a registry JSON payload supporting either {@code { "packs": [...] }} or a top-level array {@code [...]}.
     */
    public static List<RemoteIntroPack> parseRegistry(Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        List<RemoteIntroPack> parsed = new ArrayList<>();
        if (root == null || root.isJsonNull()) {
            return parsed;
        }
        if (root.isJsonArray()) {
            Type listType = new TypeToken<List<RemoteIntroPack>>() {}.getType();
            List<RemoteIntroPack> items = GSON.fromJson(root, listType);
            if (items != null) parsed.addAll(items);
        } else if (root.isJsonObject()) {
            RegistryResponse resp = GSON.fromJson(root, RegistryResponse.class);
            if (resp != null && resp.packs != null) parsed.addAll(resp.packs);
        }

        List<RemoteIntroPack> valid = new ArrayList<>();
        for (RemoteIntroPack pack : parsed) {
            if (pack != null && pack.validate()) {
                valid.add(pack);
            }
        }
        return valid;
    }

    private boolean validate() {
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            return false;
        }
        if (id == null || id.trim().isEmpty()) {
            id = "unnamed_intro";
        }
        id = sanitizeId(id);
        if (name == null || name.trim().isEmpty()) {
            name = id;
        }
        if (author == null || author.trim().isEmpty()) {
            author = "Unknown";
        }
        if (version == null || version.trim().isEmpty()) {
            version = "1.0.0";
        }
        if (description == null) {
            description = "";
        }
        if (fps <= 0 || fps > 120) {
            fps = 25;
        }
        downloadUrl = downloadUrl.trim();
        String lowerDownload = downloadUrl.toLowerCase(Locale.ROOT);
        if (!lowerDownload.startsWith("https://") && !lowerDownload.startsWith("http://")) {
            return false;
        }
        if (thumbnailUrl != null) {
            thumbnailUrl = thumbnailUrl.trim();
            String lowerThumb = thumbnailUrl.toLowerCase(Locale.ROOT);
            if (!lowerThumb.startsWith("https://") && !lowerThumb.startsWith("http://")) {
                thumbnailUrl = null;
            }
        }
        if (sha256 != null) {
            sha256 = sha256.trim().toLowerCase(Locale.ROOT);
        }
        return true;
    }

    public static String sanitizeId(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "unnamed_intro";
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return cleaned.replaceAll("\\.\\.+", "_");
    }

    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.0f KB", kb);
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }

    public static boolean isNewerVersion(String installedVersion, String remoteVersion) {
        if (remoteVersion == null || remoteVersion.trim().isEmpty()) return false;
        if (installedVersion == null || installedVersion.trim().isEmpty()) return true;
        String[] a = installedVersion.trim().replaceFirst("^[vV]", "").split("[.-]");
        String[] b = remoteVersion.trim().replaceFirst("^[vV]", "").split("[.-]");
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            int va = i < a.length ? parseVersionPart(a[i]) : 0;
            int vb = i < b.length ? parseVersionPart(b[i]) : 0;
            if (vb > va) return true;
            if (vb < va) return false;
        }
        return false;
    }

    private static int parseVersionPart(String part) {
        try {
            String digits = part.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = sanitizeId(id);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getFps() {
        return fps;
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public boolean isOfficial() {
        return official;
    }

    public void setOfficial(boolean official) {
        this.official = official;
    }

    public boolean hasIntro() {
        return hasIntro;
    }

    public void setHasIntro(boolean hasIntro) {
        this.hasIntro = hasIntro;
    }

    public boolean hasLoop() {
        return hasLoop;
    }

    public void setHasLoop(boolean hasLoop) {
        this.hasLoop = hasLoop;
    }

    public boolean hasAudio() {
        return hasAudio;
    }

    public void setHasAudio(boolean hasAudio) {
        this.hasAudio = hasAudio;
    }

    public boolean hasPanorama() {
        return hasPanorama;
    }

    public void setHasPanorama(boolean hasPanorama) {
        this.hasPanorama = hasPanorama;
    }

    public boolean hasPanoramaOverlay() {
        return hasPanoramaOverlay;
    }

    public void setHasPanoramaOverlay(boolean hasPanoramaOverlay) {
        this.hasPanoramaOverlay = hasPanoramaOverlay;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }
}
