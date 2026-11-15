package noobtrieu.reimaginedintro.client.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultIntroPackTest {

    @Test
    public void testDefaultIntroPackProperties() {
        DefaultIntroPack pack = new DefaultIntroPack();
        assertEquals(DefaultIntroPack.DEFAULT_ID, pack.getId());
        assertEquals("Reimagined Intro", pack.getName());
        assertTrue(pack.isOfficial());
        assertEquals(25, pack.getFps());
        assertEquals(40L, pack.getFrameDurationMs());

        assertTrue(pack.hasIntro());
        assertEquals(161, pack.getIntroFrameCount());
        assertEquals(6440L, pack.getIntroDurationMs());

        assertTrue(pack.hasLoop());
        assertEquals(238, pack.getLoopFrameCount());

        assertTrue(pack.hasAudio());
        assertFalse(pack.hasPanorama());
        assertTrue(pack.hasPanoramaOverlay());

        assertNotNull(pack.openThumbnail());
        assertNotNull(pack.openPanoramaOverlay());
        assertNotNull(pack.openAudio());
        assertNotNull(pack.openIntroFrame(1));
        assertNotNull(pack.openLoopFrame(1));

        assertFalse(IntroPackManager.canUninstall(pack));
        assertFalse(IntroPackManager.canUninstall(null));
        assertFalse(IntroPackManager.uninstallPack(pack));
    }
}

