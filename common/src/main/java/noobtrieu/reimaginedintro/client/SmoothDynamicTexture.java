package noobtrieu.reimaginedintro.client;

import net.minecraft.client.renderer.texture.DynamicTexture;

public class SmoothDynamicTexture extends DynamicTexture {
    public SmoothDynamicTexture(int width, int height, boolean useCalloc) {
        super(width, height, useCalloc);
        // Enable linear (bilinear) filtering & disable mipmap for smooth frame playback
        this.setFilter(true, false);
    }
}
