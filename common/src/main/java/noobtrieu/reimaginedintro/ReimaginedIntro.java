package noobtrieu.reimaginedintro;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import noobtrieu.reimaginedintro.client.migration.LegacyMigrationHandler;
import noobtrieu.reimaginedintro.client.pack.IntroPackManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReimaginedIntro {
	public static final String MOD_ID = "reimagined-intro";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier INTRO_SOUND_ID = id("intro");
	public static final SoundEvent INTRO_SOUND_EVENT = SoundEvent.createVariableRangeEvent(INTRO_SOUND_ID);

	public static void init() {
		LegacyMigrationHandler.checkAndPrompt();
		IntroPackManager.init();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
