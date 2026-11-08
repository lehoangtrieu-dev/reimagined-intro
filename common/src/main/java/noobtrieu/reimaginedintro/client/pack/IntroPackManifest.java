package noobtrieu.reimaginedintro.client.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.Reader;

public class IntroPackManifest {
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    @SerializedName("format_version")
    private int formatVersion = 1;

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("author")
    private String author = "Unknown";

    @SerializedName("version")
    private String version = "1.0.0";

    @SerializedName("official")
    private boolean official = false;

    @SerializedName("description")
    private String description = "";

    @SerializedName("fps")
    private int fps = 30;

    public static IntroPackManifest parse(Reader reader) {
        IntroPackManifest manifest = GSON.fromJson(reader, IntroPackManifest.class);
        if (manifest != null) {
            manifest.validate();
        }
        return manifest;
    }

    private void validate() {
        if (id == null || id.trim().isEmpty()) {
            id = "unnamed_intro";
        }
        id = id.trim().toLowerCase().replaceAll("[^a-z0-9_.-]", "_");

        if (name == null || name.trim().isEmpty()) {
            name = id;
        }
        if (fps <= 0 || fps > 120) {
            fps = 30;
        }
        if (formatVersion <= 0) {
            formatVersion = 1;
        }
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public void setFormatVersion(int formatVersion) {
        this.formatVersion = formatVersion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public boolean isOfficial() {
        return official;
    }

    public void setOfficial(boolean official) {
        this.official = official;
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
}
