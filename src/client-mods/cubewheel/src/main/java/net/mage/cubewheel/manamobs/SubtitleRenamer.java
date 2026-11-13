package net.mage.cubewheel.manamobs;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.tracker.local.ModelMob;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;

/** Called by the subtitle mixin: swaps a vanilla mob subtitle for one naming the mana mob it came from. */
public final class SubtitleRenamer {
	private static final AtomicBoolean LOGGED = new AtomicBoolean();

	private SubtitleRenamer() {}

	/** The subtitle to show for {@code sound}: a renamed one for a mana mob, else {@code vanilla} itself. Never throws. */
	public static Component apply(Component vanilla, SoundInstance sound) {
		try {
			if (vanilla == null || sound == null) return vanilla;
			if (!CubeWheelClient.config().current().manaNames.subtitles) return vanilla;
			if (!ModelMobRegistry.active()) return vanilla;
			if (!(vanilla.getContents() instanceof TranslatableContents tc)) return vanilla;
			Optional<String> type = SubtitleNames.entityType(tc.getKey());
			if (type.isEmpty()) return vanilla;
			Optional<EntityType<?>> entityType = Optional.ofNullable(Identifier.tryParse(type.get()))
					.flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional);
			if (entityType.isEmpty()) return vanilla;
			// projectiles, potions, lightning and other non-mob sounds stay vanilla; nearSound would learn their "voice"
			if (entityType.get().getCategory() == MobCategory.MISC) return vanilla;
			Optional<ModelMob> mob = ModelMobRegistry.nearSound(type.get(), new Vec3(sound.getX(), sound.getY(), sound.getZ()));
			if (mob.isEmpty()) return vanilla;
			String vanillaName = entityType.get().getDescription().getString();
			return Component.literal(SubtitleNames.rename(vanilla.getString(), vanillaName, mob.get().name()));
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
}
