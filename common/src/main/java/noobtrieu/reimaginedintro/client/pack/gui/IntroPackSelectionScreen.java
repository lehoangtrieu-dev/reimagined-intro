package noobtrieu.reimaginedintro.client.pack.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.IntroManager;
import noobtrieu.reimaginedintro.client.IntroScreen;
import noobtrieu.reimaginedintro.client.NativeImageDecoder;
import noobtrieu.reimaginedintro.client.SmoothDynamicTexture;
import noobtrieu.reimaginedintro.client.config.ModConfigScreen;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class IntroPackSelectionScreen extends Screen {
    private final Screen parent;
    private IntroPackList packList;
    private EditBox searchBox;
    private Button openFolderButton;
    private Button refreshButton;
    private Button optionsButton;
    private Button doneButton;

    private final Map<String, Identifier> packThumbnails = new HashMap<>();
    private final Map<String, DynamicTexture> dynamicTextures = new HashMap<>();

    public IntroPackSelectionScreen(Screen parent) {
        super(Component.literal("Intro Packs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        loadThumbnails();

        // 1. Pack List
        int listY = 44;
        int listHeight = this.height - listY - 34;
        this.packList = this.addRenderableWidget(
            new IntroPackList(this, this.minecraft, this.width, listHeight, listY, 68)
        );

        // 2. Header Bar Widgets
        int totalHeaderW = 340;
        int headerStartX = (this.width - totalHeaderW) / 2;

        this.searchBox = new EditBox(this.font, headerStartX, 20, 175, 20, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search intros...").withStyle(ChatFormatting.GRAY));
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);

        this.openFolderButton = Button.builder(Component.literal("Open Folder 📁"), b -> openIntrosFolder())
            .bounds(headerStartX + 180, 20, 110, 20)
            .tooltip(Tooltip.create(Component.literal("Open .minecraft/intros/ in file explorer")))
            .build();
        this.addRenderableWidget(this.openFolderButton);

        this.refreshButton = Button.builder(Component.literal("🔄"), b -> refreshPacks())
            .bounds(headerStartX + 295, 20, 24, 20)
            .tooltip(Tooltip.create(Component.literal("Re-scan intros directory")))
            .build();
        this.addRenderableWidget(this.refreshButton);

        // 3. Bottom Bar Widgets
        int bottomY = this.height - 28;
        int bottomStartX = this.width / 2 - 105;

        this.optionsButton = Button.builder(Component.literal("Options..."), b -> {
            Minecraft.getInstance().gui.setScreen(new ModConfigScreen(this));
        }).bounds(bottomStartX, bottomY, 100, 20).build();
        this.addRenderableWidget(this.optionsButton);

        this.doneButton = Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
            .bounds(bottomStartX + 110, bottomY, 100, 20)
            .build();
        this.addRenderableWidget(this.doneButton);

        refreshList();
    }

    public Identifier getThumbnailId(String packId) {
        return packThumbnails.get(packId);
    }

    private void loadThumbnails() {
        for (IntroPack pack : IntroPackManager.getAvailablePacks()) {
            if (packThumbnails.containsKey(pack.getId())) continue;

            try (InputStream stream = pack.openThumbnail()) {
                if (stream != null) {
                    NativeImage img = NativeImageDecoder.decode(stream);
                    if (img != null) {
                        String cleanId = pack.getId().replaceAll("[^a-zA-Z0-9_.-]", "_");
                        Identifier texId = ReimaginedIntro.id("thumb_" + cleanId);
                        SmoothDynamicTexture tex = new SmoothDynamicTexture("thumb_" + cleanId, img.getWidth(), img.getHeight(), false);
                        tex.setPixels(img);
                        tex.upload();
                        Minecraft.getInstance().getTextureManager().register(texId, tex);
                        packThumbnails.put(pack.getId(), texId);
                        dynamicTextures.put(pack.getId(), tex);
                    }
                }
            } catch (Exception e) {
                ReimaginedIntro.LOGGER.error("Failed to load thumbnail for intro pack: " + pack.getId(), e);
            }
        }
    }

    public void selectPack(IntroPack pack) {
        IntroPackManager.setActivePack(pack);
    }

    public void previewPack(IntroPack pack) {
        if (!pack.hasIntro()) return;
        IntroPackManager.setActivePack(pack);
        IntroManager.setReturnScreen(this);
        IntroManager.resetForReplay();
        Minecraft.getInstance().gui.setScreen(new IntroScreen(this));
    }

    private void openIntrosFolder() {
        try {
            Path dir = IntroPackManager.getIntrosDir();
            Files.createDirectories(dir);
            com.mojang.blaze3d.Blaze3D.openPath(dir);
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.error("Failed to open intros directory", e);
        }
    }

    public void refreshPacks() {
        IntroPackManager.reloadPacks();
        loadThumbnails();
        refreshList();
    }

    public void refreshList() {
        String query = searchBox != null ? searchBox.getValue() : "";
        onSearchChanged(query);
    }

    private void onSearchChanged(String query) {
        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        List<IntroPack> filtered = new ArrayList<>();
        for (IntroPack pack : IntroPackManager.getAvailablePacks()) {
            if (q.isEmpty()
                || pack.getName().toLowerCase(Locale.ROOT).contains(q)
                || pack.getId().toLowerCase(Locale.ROOT).contains(q)
                || pack.getManifest().getAuthor().toLowerCase(Locale.ROOT).contains(q)
                || pack.getManifest().getDescription().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(pack);
            }
        }
        if (packList != null) {
            packList.setEntries(filtered);
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) return;
        Path introsDir = IntroPackManager.getIntrosDir();
        boolean imported = false;

        for (Path path : paths) {
            try {
                String name = path.getFileName().toString();
                if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    Files.createDirectories(introsDir);
                    Files.copy(path, introsDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    imported = true;
                } else if (Files.isDirectory(path)) {
                    Files.createDirectories(introsDir);
                    copyDirectory(path, introsDir.resolve(name));
                    imported = true;
                }
            } catch (Exception e) {
                ReimaginedIntro.LOGGER.error("Failed to copy dropped intro pack: " + path, e);
            }
        }

        if (imported) {
            refreshPacks();
        }
    }

    private static void copyDirectory(Path source, Path target) throws Exception {
        try (var stream = Files.walk(source)) {
            stream.forEach(p -> {
                try {
                    Path dest = target.resolve(source.relativize(p));
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Dark Atmospheric Canvas Backdrop
        guiGraphics.fill(0, 0, this.width, this.height, 0xD0101010);

        // Header Top Bar
        guiGraphics.fill(0, 0, this.width, 44, 0xF0181818);
        guiGraphics.fill(0, 43, this.width, 44, 0xFF333333);

        // Footer Bottom Bar
        guiGraphics.fill(0, this.height - 34, this.width, this.height, 0xF0181818);
        guiGraphics.fill(0, this.height - 35, this.width, this.height - 34, 0xFF333333);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        // Title
        Component titleComp = Component.literal("Intro Packs").withStyle(ChatFormatting.BOLD);
        int titleW = this.font.width(titleComp);
        guiGraphics.text(this.font, titleComp, (this.width - titleW) / 2, 7, 0xFFFFFFFF);

        // Empty state hint
        if (packList != null && packList.children().isEmpty()) {
            guiGraphics.centeredText(this.font, Component.literal("§7No matching intro packs found."), this.width / 2, this.height / 2, 0xFFAAAAAA);
        }
    }

    @Override
    public void removed() {
        for (DynamicTexture tex : dynamicTextures.values()) {
            tex.close();
        }
        dynamicTextures.clear();
        packThumbnails.clear();
        super.removed();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }
}

