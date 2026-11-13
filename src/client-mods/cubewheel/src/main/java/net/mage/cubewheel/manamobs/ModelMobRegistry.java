package net.mage.cubewheel.manamobs;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.tracker.local.ModelBounds;
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
import net.minecraft.world.entity.Interaction;
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

	/** The disconnect: forgets the mobs. */
	public static void onDisconnect() {
		STATE.clear();
		lastLevel = null;
		ticks = 0;
	}

	private static void tick(Minecraft mc) {
		if (broken) return;
		try {
			if (mc.level != lastLevel) { // a level change (warp, dimension) drops the mobs
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
					e.isInvisible(), e instanceof Mob, owned, custom, text, e.getX(), e.getY(), e.getZ(), extent(e)));
		}
		return out;
	}

	/**
	 * What {@code e} covers relative to its position: an interaction's box, or where an item_display bone's
	 * translation can put it at any yaw (every bone entity sits at its cloud's position; the model is drawn by
	 * the translations). Null (a point) for anything else, or a display not yet given a render state.
	 */
	private static ModelBounds extent(Entity e) {
		if (e instanceof Interaction) {
			AABB b = e.getBoundingBox();
			return new ModelBounds(b.minX - e.getX(), b.minY - e.getY(), b.minZ - e.getZ(),
					b.maxX - e.getX(), b.maxY - e.getY(), b.maxZ - e.getZ());
		}
		if (e instanceof Display.ItemDisplay d) {
			Display.RenderState state = d.renderState();
			if (state == null) return null;
			var t = state.transformation().get(1.0f).translation();
			return ModelBounds.aroundAxis(t.x(), t.y(), t.z());
		}
		return null;
	}

	/** The model mob entity {@code id} is a part of (hitbox, recycled slime, bone, tag); empty when not {@link #active}. */
	public static Optional<ModelMob> byEntity(int id) {
		return active() ? STATE.byEntity(id) : Optional.empty();
	}

	/**
	 * Which model mob a mob sound at {@code pos} belongs to, and why, matched against each hitbox's live position (a
	 * map lookup per mob, falling back to the snapshot when the hitbox is not loaded). No candidate when not
	 * {@link #active}.
	 */
	public static RegistryState.SoundMatch matchSound(Vec3 pos) {
		if (!active()) return new RegistryState.SoundMatch(RegistryState.SoundDecision.NO_CANDIDATE, null, Double.NaN,
				Double.NaN, Double.NaN);
		ClientLevel level = Minecraft.getInstance().level;
		return STATE.matchSound(pos.x, pos.y, pos.z, id -> {
			Entity e = level == null ? null : level.getEntity(id);
			return e == null ? null : new double[] {e.getX(), e.getY(), e.getZ()};
		});
	}

	/** The model mobs near the player, as last grouped; empty when not {@link #active}. */
	public static List<ModelMob> mobs() {
		return active() ? STATE.mobs() : List.of();
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

	/**
	 * True in a mana world while the registry holds at least one model mob. The world check covers the up-to-a-second
	 * gap after leaving a mana world before the next tick clears the state ({@code currentWorld} is cached per second).
	 */
	public static boolean active() {
		return !STATE.isEmpty() && LocalSignals.currentWorld().special();
	}
}
