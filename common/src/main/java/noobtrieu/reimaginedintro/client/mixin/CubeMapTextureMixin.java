package noobtrieu.reimaginedintro.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.server.packs.resources.ResourceManager;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.NativeImageDecoder;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;

@Mixin(CubeMapTexture.class)
public abstract class CubeMapTextureMixin {
    @Inject(method = "loadContents", at = @At("HEAD"), cancellable = true)
    private void loadCustomCubemap(ResourceManager resourceManager, CallbackInfoReturnable<TextureContents> cir) {
        IntroPack pack = IntroPackManager.getActivePack();
        if (pack != null && pack.hasPanorama()) {
            // Suffix order in Minecraft 26.3 CubeMapTexture:
            // index 0: _1.png -> panorama_1.png
            // index 1: _3.png -> panorama_3.png
            // index 2: _5.png -> panorama_5.png
            // index 3: _4.png -> panorama_4.png
            // index 4: _0.png -> panorama_0.png
            // index 5: _2.png -> panorama_2.png
            int[] faceIndices = { 1, 3, 5, 4, 0, 2 };
            NativeImage combined = null;
            int width = -1;
            int height = -1;

            try {
                for (int i = 0; i < 6; i++) {
                    int faceIndex = faceIndices[i];
                    InputStream stream = pack.openPanoramaFace(faceIndex);
                    if (stream == null) {
                        throw new IOException("Missing panorama face: panorama_" + faceIndex + ".png in pack " + pack.getName());
                    }
                    NativeImage face = NativeImageDecoder.decode(stream);
                    if (face == null) {
                        throw new IOException("Failed to decode panorama face: panorama_" + faceIndex + ".png");
                    }

                    if (i == 0) {
                        width = face.getWidth();
                        height = face.getHeight();
                        combined = new NativeImage(width, height * 6, false);
                    } else if (face.getWidth() != width || face.getHeight() != height) {
                        face.close();
                        throw new IOException("Panorama face dimensions do not match: face 0 is " + width + "x" + height + ", but face " + faceIndex + " is " + face.getWidth() + "x" + face.getHeight());
                    }

                    face.copyRect(combined, 0, 0, 0, height * i, width, height, false, true);
                    face.close();
                }

                cir.setReturnValue(new TextureContents(combined, null));
            } catch (Throwable t) {
                ReimaginedIntro.LOGGER.error("Failed to load custom panorama from pack: " + pack.getName(), t);
                if (combined != null) {
                    combined.close();
                }
            }
        }
    }
}

