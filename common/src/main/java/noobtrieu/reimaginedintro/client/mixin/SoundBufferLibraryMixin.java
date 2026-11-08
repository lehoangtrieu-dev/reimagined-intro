package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import noobtrieu.reimaginedintro.client.pack.DefaultIntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {
    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void getCustomIntroStream(Identifier id, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if ("reimagined-intro".equals(id.getNamespace()) && ("sounds/intro.ogg".equals(id.getPath()) || "intro".equals(id.getPath()))) {
            IntroPack pack = IntroPackManager.getActivePack();
            if (pack != null && pack.hasAudio() && !(pack instanceof DefaultIntroPack)) {
                cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
                    try {
                        InputStream is = pack.openAudio();
                        if (is == null) {
                            throw new IOException("Failed to open audio stream for intro pack: " + pack.getName());
                        }
                        return (AudioStream) (looping ? new LoopingAudioStream(JOrbisAudioStream::new, is) : new JOrbisAudioStream(is));
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, Util.nonCriticalIoPool()));
            }
        }
    }
}

