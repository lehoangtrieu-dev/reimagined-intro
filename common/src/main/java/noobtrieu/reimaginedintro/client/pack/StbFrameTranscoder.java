package noobtrieu.reimaginedintro.client.pack;

import com.mojang.blaze3d.platform.NativeImage;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.config.VideoResolution;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.stb.STBIWriteCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Isolates LWJGL STB and Minecraft {@link NativeImage} calls from {@link FrameCacheManager}
 * so cache metadata and filesystem operations stay decoupled from native graphics dependencies.
 */
public final class StbFrameTranscoder {
    private StbFrameTranscoder() {}

    public static int[] probeDimensions(byte[] encodedBytes) {
        if (encodedBytes == null || encodedBytes.length == 0) {
            return null;
        }
        ByteBuffer direct = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            direct = MemoryUtil.memAlloc(encodedBytes.length);
            direct.put(encodedBytes).flip();
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(direct, x, y, comp)) {
                return null;
            }
            return new int[]{x.get(0), y.get(0)};
        } finally {
            if (direct != null) MemoryUtil.memFree(direct);
        }
    }

    public static byte[] transcodeJpeg(byte[] encodedBytes, VideoResolution resolution, int quality) {
        ByteBuffer inputBuf = null;
        ByteBuffer decoded = null;
        ByteBuffer resized = null;
        STBIWriteCallback callback = null;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            inputBuf = MemoryUtil.memAlloc(encodedBytes.length);
            inputBuf.put(encodedBytes).flip();

            IntBuffer wBuf = stack.mallocInt(1);
            IntBuffer hBuf = stack.mallocInt(1);
            IntBuffer compBuf = stack.mallocInt(1);

            decoded = STBImage.stbi_load_from_memory(inputBuf, wBuf, hBuf, compBuf, 3);
            if (decoded == null) {
                return null;
            }

            int srcW = wBuf.get(0);
            int srcH = hBuf.get(0);
            int[] target = resolution.computeTargetDimensions(srcW, srcH);
            int dstW = target[0];
            int dstH = target[1];

            ByteBuffer bufferToEncode;
            if (dstW != srcW || dstH != srcH) {
                resized = MemoryUtil.memAlloc(dstW * dstH * 3);
                ByteBuffer out = STBImageResize.stbir_resize_uint8_srgb(
                    decoded, srcW, srcH, srcW * 3,
                    resized, dstW, dstH, dstW * 3,
                    STBImageResize.STBIR_RGB
                );
                if (out == null) {
                    return null;
                }
                bufferToEncode = resized;
            } else {
                bufferToEncode = decoded;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
            callback = new STBIWriteCallback() {
                @Override
                public void invoke(long context, long data, int size) {
                    ByteBuffer chunk = MemoryUtil.memByteBuffer(data, size);
                    byte[] copy = new byte[size];
                    chunk.get(copy);
                    baos.write(copy, 0, size);
                }
            };

            int ok = STBImageWrite.stbi_write_jpg_to_func(callback, 0L, dstW, dstH, 3, bufferToEncode, quality);
            if (ok == 0 || baos.size() == 0) {
                return null;
            }
            return baos.toByteArray();
        } finally {
            if (callback != null) callback.free();
            if (resized != null) MemoryUtil.memFree(resized);
            if (decoded != null) STBImage.stbi_image_free(decoded);
            if (inputBuf != null) MemoryUtil.memFree(inputBuf);
        }
    }

    /**
     * Runtime safety helper: downscales an already-decoded {@link NativeImage} in memory when its height
     * exceeds the target {@link VideoResolution} and closes the oversized source image.
     */
    public static NativeImage downscaleNativeImageIfNeeded(NativeImage src, VideoResolution resolution) {
        if (src == null || resolution == null || !resolution.needsDownscale(src.getHeight())) {
            return src;
        }
        int[] dims = resolution.computeTargetDimensions(src.getWidth(), src.getHeight());
        NativeImage dst = null;
        try {
            dst = new NativeImage(src.format(), dims[0], dims[1], false);
            src.resizeSubRectTo(0, 0, src.getWidth(), src.getHeight(), dst);
            src.close();
            return dst;
        } catch (Throwable t) {
            if (dst != null) {
                dst.close();
            }
            ReimaginedIntro.LOGGER.warn("Runtime NativeImage downscale failed, falling back to original dimensions", t);
            return src;
        }
    }
}
