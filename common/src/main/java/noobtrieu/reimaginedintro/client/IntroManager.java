package noobtrieu.reimaginedintro.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.pack.IntroPack;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;

public class IntroManager {
    private static long startTimeMs = -1;
    private static boolean hasPlayed = false;
    private static boolean skipped = false;
    private static Screen returnScreen = null;

    public static synchronized void setReturnScreen(Screen screen) {
        returnScreen = screen;
    }

    public static Screen getReturnScreen() {
        return returnScreen;
    }

    public static synchronized void startIntro() {
        if (startTimeMs == -1) {
            IntroPack activePack = IntroPackManager.getActivePack();
            if (activePack == null || !activePack.hasIntro()) {
                ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Active pack has no intro animation. Skipping directly to TitleScreen.");
                hasPlayed = true;
                skipped = true;
                if (returnScreen != null) {
                    Screen back = returnScreen;
                    returnScreen = null;
                    Minecraft.getInstance().gui.setScreen(back);
                }
                return;
            }

            ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Starting intro sequence for pack: " + activePack.getName());
            startTimeMs = System.currentTimeMillis();
            FrameStreamer.init();
            IntroSoundController.play();
        }
    }

    public static long getElapsedMs() {
        if (startTimeMs == -1) {
            // If somehow accessed directly on TitleScreen before startIntro, initialize to loop
            startTimeMs = System.currentTimeMillis() - FrameStreamer.getIntroTotalDurationMs();
            FrameStreamer.init();
        }
        return System.currentTimeMillis() - startTimeMs;
    }

    public static boolean isIntroComplete() {
        IntroPack activePack = IntroPackManager.getActivePack();
        if (activePack == null || !activePack.hasIntro()) {
            return true;
        }
        return skipped || (startTimeMs != -1 && getElapsedMs() >= FrameStreamer.getIntroTotalDurationMs());
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
            startTimeMs = System.currentTimeMillis() - FrameStreamer.getIntroTotalDurationMs();
            hasPlayed = true;
            FrameStreamer.warmupLoop();
            FrameStreamer.loadLastIntroFrame();

            if (returnScreen != null) {
                Screen back = returnScreen;
                returnScreen = null;
                Minecraft.getInstance().gui.setScreen(back);
            } else {
                Minecraft.getInstance().gui.setScreen(new TitleScreen(true));
            }
        }
    }

    public static void onIntroFinished() {
        ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Intro finished naturally.");
        hasPlayed = true;
        // Align timeline so loop begins smoothly at frame 1 without jump
        startTimeMs = System.currentTimeMillis() - FrameStreamer.getIntroTotalDurationMs();
        FrameStreamer.loadLastIntroFrame();

        if (returnScreen != null) {
            Screen back = returnScreen;
            returnScreen = null;
            Minecraft.getInstance().gui.setScreen(back);
        } else {
            Minecraft.getInstance().gui.setScreen(new TitleScreen(true));
        }
    }

    public static void resetForReplay() {
        IntroPack activePack = IntroPackManager.getActivePack();
        if (activePack == null || !activePack.hasIntro()) {
            return;
        }
        ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Replaying intro for pack: " + activePack.getName());
        IntroSoundController.stop();
        startTimeMs = -1;
        skipped = false;
        hasPlayed = false;
        startIntro();
    }
}
