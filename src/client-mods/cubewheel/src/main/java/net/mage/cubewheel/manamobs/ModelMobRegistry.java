package net.mage.cubewheel.manamobs;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.tracker.local.ModelMob;
import net.mage.cubewheel.tracker.local.ModelMobs;
import net.mage.cubewheel.tracker.local.NameResolver;
import net.mage.cubewheel.tracker.local.RegistryState;
import net.mage.cubewheel.tracker.local.TagHealth;
import net.mage.cubewheel.tracker.local.mc.LocalSignals;
import net.mage.cubewheel.tracker.local.mc.TextDisplays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minecraft adapter: once a second, in a mana (special) world, the ManaCube custom-model mobs within
 * {@link #RANGE} blocks of the player, grouped by {@link ModelMobs#group} into a {@link RegistryState} that the Jade
 * plugin and the subtitle mixin ask. Outside a mana world, or with both toggles off, it is empty and costs nothing.
 * A failure is logged once and switches this off until restart.
 */
public final class ModelMobRegistry {
	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	static final int RANGE = 48;
	static final int PERIOD_TICKS = 20;

	private static final RegistryState STATE = new RegistryState();
	private static int ticks;
	private static ClientLevel lastLevel;
	private static boolean broken;

	private ModelMobRegistry() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ModelMobRegistry::tick);
	}

	/** The disconnect: forgets the mobs and the learned voices. */
	public static void onDisconnect() {
		STATE.clearAll();
		lastLevel = null;
		ticks = 0;
	}

	private static void tick(Minecraft mc) {
		if (broken) return;
		try {
			if (mc.level != lastLevel) { // a level change (warp, dimension) drops the mobs; voices are kept
				lastLevel = mc.level;
				STATE.clear();
				ticks = 0;
			}
			if (mc.level == null || mc.player == null) return;
			if (++ticks < PERIOD_TICKS) return;
			ticks = 0;
			if (!enabled() || !LocalSignals.currentWorld().special()) {
				STATE.clear();
				return;
			}
			STATE.replace(ModelMobs.group(snapshot(mc.level, mc.player)));
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			broken = true;
			STATE.clear();
			LOG.error("[cubewheel] mana mob registry failed; switched off until restart", t);
		}
	}

	private static boolean enabled() {
		CubeWheelConfig.ManaNames n = CubeWheelClient.config().current().manaNames;
		return n.jade || n.subtitles;
	}

	/** Every non-player entity near the player: grouping needs the clouds, bones, text displays and interactions. */
	private static List<ModelMobs.Snap> snapshot(ClientLevel level, Player player) {
		AABB box = player.getBoundingBox().inflate(RANGE);
		List<ModelMobs.Snap> out = new ArrayList<>();
		for (Entity e : level.getEntities(player, box, p -> !(p instanceof Player))) {
			Entity v = e.getVehicle();
			List<Integer> passengers = new ArrayList<>();
			for (Entity p : e.getPassengers()) passengers.add(p.getId());
			String custom = e.hasCustomName() ? e.getCustomName().getString() : null;
			String text = e instanceof Display.TextDisplay td ? TextDisplays.text(td) : null;
			boolean owned = e instanceof OwnableEntity o && o.getOwnerReference() != null;
			out.add(new ModelMobs.Snap(e.getId(), e.typeHolder().getRegisteredName(), v == null ? null : v.getId(), passengers,
					e.isInvisible(), e instanceof Mob, owned, custom, text, e.getX(), e.getY(), e.getZ()));
		}
		return out;
	}

	public static Optional<ModelMob> byEntity(int id) {
		return STATE.byEntity(id);
	}

	/** The model mob a sound of entity type {@code soundType} ("minecraft:polar_bear") at {@code pos} belongs to. */
	public static Optional<ModelMob> nearSound(String soundType, Vec3 pos) {
		return STATE.nearSound(soundType, pos.x, pos.y, pos.z);
	}

	/** The health on {@code m}'s name tag right now (its text display changes as the mob is hurt), else empty. */
	public static OptionalDouble liveHealth(ModelMob m) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (m.tagEntity() == null || mc.level == null) return OptionalDouble.empty();
			if (!(mc.level.getEntity(m.tagEntity()) instanceof Display.TextDisplay td)) return OptionalDouble.empty();
			String text = TextDisplays.text(td);
			return text == null ? OptionalDouble.empty() : TagHealth.parse(NameResolver.firstLine(text));
		} catch (VirtualMachineError e) {
			throw e;
		} catch (RuntimeException e) {
			return OptionalDouble.empty();
		}
	}

	/** True in a mana world while the registry holds at least one model mob. */
	public static boolean active() {
		return !STATE.isEmpty();
	}
}
