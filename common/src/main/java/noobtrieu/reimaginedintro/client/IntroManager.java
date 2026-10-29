package noobtrieu.reimaginedintro.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import noobtrieu.reimaginedintro.ReimaginedIntro;

public class IntroManager {
    private static long startTimeMs = -1;
    private static boolean hasPlayed = false;
    private static boolean skipped = false;

    public static synchronized void startIntro() {
        if (startTimeMs == -1) {
            ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Starting intro sequence and audio.");
            startTimeMs = System.currentTimeMillis();
            FrameStreamer.init();
            IntroSoundController.play();
        }
    }

    public static long getElapsedMs() {
        if (startTimeMs == -1) {
            // If somehow accessed directly on TitleScreen before startIntro, initialize to loop
            startTimeMs = System.currentTimeMillis() - FrameStreamer.INTRO_TOTAL_DURATION_MS;
            FrameStreamer.init();
        }
        return System.currentTimeMillis() - startTimeMs;
    }

    public static boolean isIntroComplete() {
        return skipped || (startTimeMs != -1 && getElapsedMs() >= FrameStreamer.INTRO_TOTAL_DURATION_MS);
    }

    public static boolean hasPlayed() {
        return hasPlayed;
    }

    public static void setHasPlayed(boolean played) {
        hasPlayed = played;
    }

    public static void skip() {
        if (!skipped) {
            ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Intro skipped by user.");
            skipped = true;
            IntroSoundController.stop();
            // Jump timeline to start of loop
            startTimeMs = System.currentTimeMillis() - FrameStreamer.INTRO_TOTAL_DURATION_MS;
            hasPlayed = true;
            FrameStreamer.warmupLoop();
            FrameStreamer.loadLastIntroFrame();

            // Transition directly to TitleScreen with smooth UI fade-in
            Minecraft.getInstance().setScreen(new TitleScreen(true));
        }
    }

    public static void onIntroFinished() {
        ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Intro finished naturally. Transitioning to TitleScreen.");
        hasPlayed = true;
        // Align timeline so loop begins smoothly at frame 1 without jump
        startTimeMs = System.currentTimeMillis() - FrameStreamer.INTRO_TOTAL_DURATION_MS;
        FrameStreamer.loadLastIntroFrame();
        Minecraft.getInstance().setScreen(new TitleScreen(true));
    }

    public static void resetForReplay() {
        ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Replaying intro.");
        IntroSoundController.stop();
        startTimeMs = -1;
        skipped = false;
        hasPlayed = false;
        startIntro();
    }
}
