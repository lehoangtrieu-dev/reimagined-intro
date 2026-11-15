package noobtrieu.reimaginedintro.client.pack.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
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
import noobtrieu.reimaginedintro.client.pack.online.OnlinePackRepository;
import noobtrieu.reimaginedintro.client.pack.online.RemoteIntroPack;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class IntroPackSelectionScreen extends Screen {
    public enum Tab {
        INSTALLED,
        BROWSE_ONLINE
    }

    private final Screen parent;
    private IntroPackList packList;
    private Tab activeTab = Tab.INSTALLED;

    private Button installedTabButton;
    private Button browseTabButton;
    private EditBox searchBox;
    private Button contextActionButton;
    private Button refreshButton;
    private Button optionsButton;
    private Button doneButton;

    private final Map<String, Identifier> packThumbnails = new HashMap<>();
    private final Map<String, Identifier> remoteThumbnails = new HashMap<>();
    private final Set<String> remoteThumbRequested = new HashSet<>();
    private final Map<String, DynamicTexture> dynamicTextures = new HashMap<>();
    private boolean screenClosed = false;

    private String directDownloadKey = null;
    private long directDownloadCompletedAt = 0L;

    public IntroPackSelectionScreen(Screen parent) {
        super(Component.literal("Intro Packs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.screenClosed = false;
        loadThumbnails();

        // 1. Pack List
        int listY = 44;
        int listHeight = this.height - listY - 34;
        this.packList = this.addRenderableWidget(
            new IntroPackList(this, this.minecraft, this.width, listHeight, listY, 142)
        );

        // 2. Header Bar: [Installed] [Online] [Search...] [Open Folder / + URL] [Refresh]
        int totalHeaderW = Math.min(560, this.width - 20);
        int headerStartX = (this.width - totalHeaderW) / 2;
        int installedW = 64;
        int browseW = 60;
        int actionW = 78;
        int refreshW = 24;
        int spacing = 4;
        int reservedW = installedW + spacing + browseW + spacing + spacing + actionW + spacing + refreshW;
        int searchW = Math.max(80, totalHeaderW - reservedW);

        int curX = headerStartX;
        this.installedTabButton = Button.builder(Component.literal("Installed"), b -> switchTab(Tab.INSTALLED))
            .bounds(curX, 20, installedW, 20)
            .build();
        this.addRenderableWidget(this.installedTabButton);
        curX += installedW + spacing;

        this.browseTabButton = Button.builder(Component.literal("Online"), b -> switchTab(Tab.BROWSE_ONLINE))
            .bounds(curX, 20, browseW, 20)
            .tooltip(Tooltip.create(Component.literal("Browse & download intro packs from GitHub")))
            .build();
        this.addRenderableWidget(this.browseTabButton);
        curX += browseW + spacing;

        String prevSearch = this.searchBox != null ? this.searchBox.getValue() : "";
        this.searchBox = new EditBox(this.font, curX, 20, searchW, 20, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search intros...").withStyle(ChatFormatting.GRAY));
        this.searchBox.setValue(prevSearch);
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);
        curX += searchW + spacing;

        this.contextActionButton = Button.builder(Component.literal("Open Folder"), b -> onContextActionPressed())
            .bounds(curX, 20, actionW, 20)
            .build();
        this.addRenderableWidget(this.contextActionButton);
        curX += actionW + spacing;

        this.refreshButton = Button.builder(Component.literal("🗘"), b -> onRefreshPressed())
            .bounds(curX, 20, refreshW, 20)
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

        updateHeaderState();
        refreshList();

        if (activeTab == Tab.BROWSE_ONLINE && !OnlinePackRepository.hasLoadedCatalogOnce()) {
            fetchOnlineCatalog(false);
        }
    }

    private void switchTab(Tab tab) {
        if (this.activeTab == tab) return;
        this.activeTab = tab;
        updateHeaderState();
        refreshList();
        if (tab == Tab.BROWSE_ONLINE && !OnlinePackRepository.hasLoadedCatalogOnce()) {
            fetchOnlineCatalog(false);
        }
    }

    private void updateHeaderState() {
        boolean isInstalled = activeTab == Tab.INSTALLED;
        if (installedTabButton != null) {
            installedTabButton.active = !isInstalled;
            installedTabButton.setMessage(Component.literal(isInstalled ? "§lInstalled" : "Installed"));
        }
        if (browseTabButton != null) {
            browseTabButton.active = isInstalled;
            browseTabButton.setMessage(Component.literal(!isInstalled ? "§lOnline" : "Online"));
        }
        if (contextActionButton != null) {
            if (isInstalled) {
                contextActionButton.setMessage(Component.literal("Open Folder"));
                contextActionButton.setTooltip(Tooltip.create(Component.literal("Open .minecraft/intros/ in file explorer")));
            } else {
                contextActionButton.setMessage(Component.literal("+ URL"));
                contextActionButton.setTooltip(Tooltip.create(Component.literal("Download a pack .zip directly from an internet URL")));
            }
        }
        if (refreshButton != null) {
            refreshButton.setTooltip(Tooltip.create(Component.literal(
                isInstalled ? "Re-scan intros directory" : "Refresh online pack catalog from GitHub"
            )));
        }
    }

    private void onContextActionPressed() {
        if (activeTab == Tab.INSTALLED) {
            openIntrosFolder();
        } else {
            Minecraft.getInstance().gui.setScreen(new InstallFromUrlScreen(this));
        }
    }

    private void onRefreshPressed() {
        if (activeTab == Tab.INSTALLED) {
            refreshPacks();
        } else {
            fetchOnlineCatalog(true);
        }
    }

    private void fetchOnlineCatalog(boolean forceRefresh) {
        OnlinePackRepository.fetchCatalogAsync(forceRefresh, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (!screenClosed && activeTab == Tab.BROWSE_ONLINE) {
                        refreshList();
                    }
                });
            }
        });
    }

    public Identifier getThumbnailId(String packId) {
        return packThumbnails.get(packId);
    }

    public Identifier getRemoteThumbnailId(RemoteIntroPack pack) {
        if (pack == null) return null;
        String packId = pack.getId();
        Identifier existing = remoteThumbnails.get(packId);
        if (existing != null) {
            return existing;
        }
        // If already installed locally and thumbnail is loaded, reuse it instantly
        Identifier localThumb = packThumbnails.get(packId);
        if (localThumb != null) {
            return localThumb;
        }
        String url = pack.getThumbnailUrl();
        if (url == null || url.isEmpty() || remoteThumbRequested.contains(packId)) {
            return null;
        }

        remoteThumbRequested.add(packId);
        OnlinePackRepository.downloadThumbnailAsync(url).thenAccept(bytes -> {
            if (bytes == null || bytes.length == 0 || screenClosed) return;
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                NativeImage img = NativeImageDecoder.decode(is);
                if (img == null) return;
                Minecraft mc = Minecraft.getInstance();
                if (mc == null) {
                    img.close();
                    return;
                }
                mc.execute(() -> {
                    if (screenClosed) {
                        img.close();
                        return;
                    }
                    String cleanId = packId.replaceAll("[^a-zA-Z0-9_.-]", "_");
                    String texKey = "remote_" + cleanId;
                    DynamicTexture prev = dynamicTextures.remove(texKey);
                    if (prev != null) prev.close();

                    Identifier texId = ReimaginedIntro.id("thumb_remote_" + cleanId);
                    SmoothDynamicTexture tex = new SmoothDynamicTexture("thumb_remote_" + cleanId, img.getWidth(), img.getHeight(), false);
                    tex.setPixels(img);
                    tex.upload();
                    mc.getTextureManager().register(texId, tex);
                    remoteThumbnails.put(packId, texId);
                    dynamicTextures.put(texKey, tex);
                });
            } catch (Exception e) {
                ReimaginedIntro.LOGGER.debug("Failed to decode remote thumbnail for {}: {}", packId, e.getMessage());
            }
        });
        return null;
    }

    private void loadThumbnails() {
        for (IntroPack pack : IntroPackManager.getAvailablePacks()) {
            if (packThumbnails.containsKey(pack.getId())) continue;
            loadThumbnailForPack(pack);
        }
    }

    private void loadThumbnailForPack(IntroPack pack) {
        try (InputStream stream = pack.openThumbnail()) {
            if (stream != null) {
                NativeImage img = NativeImageDecoder.decode(stream);
                if (img != null) {
                    String cleanId = pack.getId().replaceAll("[^a-zA-Z0-9_.-]", "_");
                    DynamicTexture old = dynamicTextures.remove(pack.getId());
                    if (old != null) old.close();

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

    public void confirmUninstallPack(IntroPack pack) {
        if (!IntroPackManager.canUninstall(pack)) return;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new ConfirmScreen(
            confirmed -> {
                if (confirmed) {
                    String packId = pack.getId();
                    if (IntroPackManager.uninstallPack(pack)) {
                        DynamicTexture oldTex = dynamicTextures.remove(packId);
                        if (oldTex != null) {
                            oldTex.close();
                        }
                        packThumbnails.remove(packId);
                        OnlinePackRepository.clearDownloadState(packId);
                        loadThumbnails();
                        refreshList();
                    }
                }
                mc.gui.setScreen(this);
            },
            Component.literal("Uninstall \"" + pack.getName() + "\"?"),
            Component.literal("This will permanently delete this intro pack from your intros folder."),
            Component.literal("§cUninstall"),
            CommonComponents.GUI_CANCEL
        ));
    }

    public void downloadRemotePack(RemoteIntroPack pack) {
        OnlinePackRepository.clearFailedState(pack.getId());
        OnlinePackRepository.downloadPackAsync(pack, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (screenClosed) return;
                    IntroPack updated = IntroPackManager.getPack(pack.getId());
                    if (updated != null && !packThumbnails.containsKey(updated.getId())) {
                        loadThumbnailForPack(updated);
                    }
                });
            }
        });
    }

    public void startDirectUrlDownload(String url) {
        String key = "url_" + Integer.toHexString(url.hashCode());
        this.directDownloadKey = key;
        this.directDownloadCompletedAt = 0L;
        OnlinePackRepository.clearFailedState(key);
        OnlinePackRepository.downloadFromDirectUrlAsync(url, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (screenClosed) return;
                    OnlinePackRepository.DownloadProgress p = OnlinePackRepository.getDownloadProgress(key);
                    if (p != null && p.status == OnlinePackRepository.Status.COMPLETED) {
                        this.directDownloadCompletedAt = System.currentTimeMillis();
                        loadThumbnails();
                        refreshList();
                    }
                });
            }
        });
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
        if (packList == null) return;

        if (activeTab == Tab.INSTALLED) {
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
            packList.setEntries(filtered);
        } else {
            List<RemoteIntroPack> filtered = new ArrayList<>();
            for (RemoteIntroPack pack : OnlinePackRepository.getCachedCatalog()) {
                if (q.isEmpty()
                    || pack.getName().toLowerCase(Locale.ROOT).contains(q)
                    || pack.getId().toLowerCase(Locale.ROOT).contains(q)
                    || pack.getAuthor().toLowerCase(Locale.ROOT).contains(q)
                    || pack.getDescription().toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(pack);
                }
            }
            packList.setRemoteEntries(filtered);
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
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        // Title
        Component titleComp = Component.literal("Intro Packs").withStyle(ChatFormatting.BOLD);
        int titleW = this.font.width(titleComp);
        guiGraphics.text(this.font, titleComp, (this.width - titleW) / 2, 7, 0xFFFFFFFF);

        // Center Status / Empty Hint
        if (packList != null && packList.children().isEmpty()) {
            if (activeTab == Tab.INSTALLED) {
                guiGraphics.centeredText(this.font, Component.literal("§7No matching intro packs found."), this.width / 2, this.height / 2, 0xFFAAAAAA);
            } else if (OnlinePackRepository.isFetchingCatalog()) {
                guiGraphics.centeredText(this.font, Component.literal("§bFetching online intro packs..."), this.width / 2, this.height / 2, 0xFF38BDF8);
            } else if (OnlinePackRepository.getCatalogError() != null) {
                guiGraphics.centeredText(
                    this.font,
                    Component.literal("§cCould not load catalog: " + OnlinePackRepository.getCatalogError()),
                    this.width / 2,
                    this.height / 2 - 8,
                    0xFFFF5555
                );
                guiGraphics.centeredText(
                    this.font,
                    Component.literal("§7Click '+ URL' above to download any .zip pack directly."),
                    this.width / 2,
                    this.height / 2 + 6,
                    0xFFAAAAAA
                );
            } else {
                guiGraphics.centeredText(this.font, Component.literal("§7No matching online packs found."), this.width / 2, this.height / 2, 0xFFAAAAAA);
            }
        }

        // Direct URL Download Status Banner
        if (directDownloadKey != null) {
            OnlinePackRepository.DownloadProgress p = OnlinePackRepository.getDownloadProgress(directDownloadKey);
            if (p != null) {
                String statusText = switch (p.status) {
                    case DOWNLOADING -> p.getPercent() >= 0
                        ? "§bDownloading URL pack... " + p.getPercent() + "%"
                        : "§bDownloading URL pack...";
                    case INSTALLING -> "§eValidating & installing URL pack...";
                    case COMPLETED -> {
                        if (directDownloadCompletedAt > 0 && System.currentTimeMillis() - directDownloadCompletedAt > 4000L) {
                            directDownloadKey = null;
                            yield null;
                        }
                        yield "§aURL pack installed! Switch to 'Installed' to activate.";
                    }
                    case FAILED -> "§cURL download failed: " + (p.errorMessage != null ? p.errorMessage : "Error");
                    case IDLE -> null;
                };
                if (statusText != null) {
                    guiGraphics.centeredText(this.font, Component.literal(statusText), this.width / 2, this.height - 42, 0xFFFFFFFF);
                }
            }
        }
    }

    @Override
    public void removed() {
        this.screenClosed = true;
        for (DynamicTexture tex : dynamicTextures.values()) {
            tex.close();
        }
        dynamicTextures.clear();
        packThumbnails.clear();
        remoteThumbnails.clear();
        remoteThumbRequested.clear();
        super.removed();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }
}
