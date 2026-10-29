package noobtrieu.reimaginedintro;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import noobtrieu.reimaginedintro.client.migration.LegacyMigrationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReimaginedIntro {
	public static final String MOD_ID = "reimagined-intro";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceLocation INTRO_SOUND_ID = id("intro");
	public static final SoundEvent INTRO_SOUND_EVENT = SoundEvent.createVariableRangeEvent(INTRO_SOUND_ID);

	public static void init() {
		LegacyMigrationHandler.checkAndPrompt();
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
