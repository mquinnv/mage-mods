package net.mage.cubewheel.manamobs;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.capture.CaptureLog;
import net.mage.cubewheel.tracker.local.RegistryState;
import net.mage.cubewheel.tracker.local.mc.LocalSignals;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;

/**
 * Called by the subtitle mixin: swaps a vanilla mob subtitle for one naming the mana mob it came from. Logs once
 * that the hook runs; with CubeWheel capture on, writes a {@code subtitle} line with the decision for every
 * {@code subtitles.entity.*} subtitle in a mana world.
 */
public final class SubtitleRenamer {
	private static final String ENTITY_PREFIX = "subtitles.entity.";
	private static final AtomicBoolean LOGGED = new AtomicBoolean();
	private static final AtomicBoolean HOOKED = new AtomicBoolean();

	private SubtitleRenamer() {}

	/** The subtitle to show for {@code sound}: a renamed one for a mana mob, else {@code vanilla} itself. Never throws. */
	public static Component apply(Component vanilla, SoundInstance sound) {
		try {
			if (HOOKED.compareAndSet(false, true)) CubeWheelClient.LOG.info("[cubewheel] subtitle hook active");
			if (vanilla == null || sound == null) return vanilla;
			if (!(vanilla.getContents() instanceof TranslatableContents tc)) return vanilla;
			String key = tc.getKey();
			if (key == null || !key.startsWith(ENTITY_PREFIX)) return vanilla;
			CaptureLog capture = capturing();
			if (!CubeWheelClient.config().current().manaNames.subtitles) return note(capture, key, "toggle_off", vanilla);
			if (!ModelMobRegistry.active()) return note(capture, key, "inactive", vanilla);
			Optional<String> type = SubtitleNames.entityType(key);
			Optional<EntityType<?>> entityType = type.map(Identifier::tryParse).flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional);
			if (entityType.isEmpty()) return note(capture, key, "not_entity", vanilla);
			// projectiles, potions, lightning and other non-mob sounds stay vanilla: an arrow hitting a mana mob is not the mob
			if (entityType.get().getCategory() == MobCategory.MISC) return note(capture, key, "misc", vanilla);
			RegistryState.SoundMatch match = ModelMobRegistry.matchSound(new Vec3(sound.getX(), sound.getY(), sound.getZ()));
			if (capture != null) {
				boolean has = match.mob() != null;
				capture.subtitle(key, match.decision().name().toLowerCase(java.util.Locale.ROOT), has ? match.mob().name() : null,
						has ? match.dx() : null, has ? match.dy() : null, has ? match.dz() : null, System.currentTimeMillis());
			}
			if (!match.renamed()) return vanilla;
			String vanillaName = entityType.get().getDescription().getString();
			return Component.literal(SubtitleNames.rename(vanilla.getString(), vanillaName, match.mob().name()));
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			try {
				if (LOGGED.compareAndSet(false, true)) CubeWheelClient.LOG.warn("[cubewheel] mana-mob subtitle rename failed; using vanilla subtitles", t);
			} catch (Throwable ignored) {
				// logging is unusable: stay silent rather than break the subtitle overlay
			}
			return vanilla;
		}
	}

	/** The capture log when capture is on and this is a mana world, else null (the cheap check comes first). */
	private static CaptureLog capturing() {
		CaptureLog c = CubeWheelClient.capture();
		if (c == null || !c.enabled()) return null;
		return LocalSignals.currentWorld().special() ? c : null;
	}

	private static Component note(CaptureLog capture, String key, String decision, Component vanilla) {
		if (capture != null) capture.subtitle(key, decision, null, null, null, null, System.currentTimeMillis());
		return vanilla;
	}
}
