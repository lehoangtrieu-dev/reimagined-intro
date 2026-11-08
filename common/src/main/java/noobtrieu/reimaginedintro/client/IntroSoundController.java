package noobtrieu.reimaginedintro.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;

public class IntroSoundController {
    private static SimpleSoundInstance currentSound = null;
    private static boolean playingIntro = false;

    public static void play() {
        IntroPack pack = IntroPackManager.getActivePack();
        if (pack == null || !pack.hasAudio()) {
            return;
        }

        if (currentSound != null && Minecraft.getInstance().getSoundManager().isActive(currentSound)) {
            return;
        }

        float masterVol = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
        if (masterVol <= 0.0F) {
            return;
        }

        float volume = noobtrieu.reimaginedintro.client.config.ModConfig.get().introVolume;
        if (volume <= 0.0F) {
            return;
        }

        currentSound = new SimpleSoundInstance(
            ReimaginedIntro.INTRO_SOUND_ID,
            SoundSource.MASTER,
            volume,
            1.0F,
            RandomSource.create(),
            false,
            0,
            SoundInstance.Attenuation.NONE,
            0.0, 0.0, 0.0,
            true
        );

        playingIntro = true;
        Minecraft.getInstance().getSoundManager().play(currentSound);
    }

    public static boolean isPlaying() {
        return currentSound != null && Minecraft.getInstance().getSoundManager().isActive(currentSound);
    }

    public static boolean isPlayingIntro() {
        return playingIntro;
    }

    public static void stop() {
        playingIntro = false;
        if (currentSound != null) {
            Minecraft.getInstance().getSoundManager().stop(currentSound);
            currentSound = null;
        }
    }
}
