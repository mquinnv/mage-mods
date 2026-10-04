package net.mage.cubewheel.tracker.local.mc;

import net.mage.cubewheel.capture.CaptureLog;
import net.mage.cubewheel.mixin.TextDisplayAccessor;
import net.mage.cubewheel.tracker.local.ModelHitbox;
import net.mage.cubewheel.tracker.local.NameResolver;
import net.mage.cubewheel.tracker.local.RemovalKills;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Minecraft adapter: what names a hit mob. One bounded entity query around the mob's box, run once per
 * hit mob (never per tick): its own custom name, else a named rider/vehicle or the nearest name tag
 * (text display, armor stand) or named entity (see {@link NameResolver}); which model it belongs to (see
 * {@link net.mage.cubewheel.tracker.local.RemovalKills#model}); plus the entities within {@link #CAPTURE_RANGE}
 * blocks for capture. {@link #hitbox} gathers the facts {@link ModelHitbox} decides on, once per counted removal.
 */
final class NearbyProbe {
	static final double CAPTURE_RANGE = 4;
	private static final int MAX_ENTITIES = 32;
	private static boolean textInvokerBroken;

	/**
	 * The name hint (or null), the model keys (its cloud vehicle, the nearest cloud carrying model bones; empty for an
	 * entity with its own name), what the hint's tag is tied to (see {@link RemovalKills#acceptReprobe}) and, when
	 * {@code forCapture}, what was around.
	 */
	record Result(RemovalKills.Hint hint, List<Integer> modelKeys, List<Integer> tagTies, List<CaptureLog.Nearby> near) {}

	private NearbyProbe() {}

	static Result of(Entity target, boolean forCapture) {
		if (target.hasCustomName()) {
			String own = target.getCustomName().getString();
			if (!own.isBlank()) {
				return new Result(new RemovalKills.Hint(own, RemovalKills.Method.OWN, "custom name"), List.of(), List.of(),
						forCapture ? around(target, new ArrayList<>(), new ArrayList<>()) : List.of());
			}
		}
		List<NameResolver.Candidate> candidates = new ArrayList<>();
		List<Integer> modelKeys = new ArrayList<>();
		List<CaptureLog.Nearby> near = around(target, candidates, modelKeys);
		Optional<NameResolver.Candidate> pick = NameResolver.pick(candidates);
		RemovalKills.Hint hint = pick.map(c -> new RemovalKills.Hint(c.name(), RemovalKills.Method.LINKED,
				c.relation().name().toLowerCase(java.util.Locale.ROOT) + " " + c.source(), c.entityId())).orElse(null);
		return new Result(hint, modelKeys, pick.map(c -> tagTies(target, c)).orElse(List.of()), forCapture ? near : List.of());
	}

	/** The picked tag's id, the entity it rides, and the target when one rides the other. */
	private static List<Integer> tagTies(Entity target, NameResolver.Candidate c) {
		List<Integer> ties = new ArrayList<>();
		if (c.entityId() < 0) return ties;
		ties.add(c.entityId());
		Entity tag = target.level().getEntity(c.entityId());
		Entity v = tag == null ? null : tag.getVehicle();
		if (v != null) ties.add(v.getId());
		if (c.relation() == NameResolver.Relation.RIDER || c.relation() == NameResolver.Relation.VEHICLE) ties.add(target.getId());
		return ties;
	}

	/** What {@link ModelHitbox} needs of a removed entity; one bounded query for model parts. */
	static ModelHitbox.Facts hitbox(Entity e) {
		Entity v = e.getVehicle();
		boolean owned = e instanceof OwnableEntity o && o.getOwnerReference() != null;
		boolean parts = !e.level().getEntities(e, e.getBoundingBox().inflate(NameResolver.RANGE),
				p -> p instanceof Display.ItemDisplay && p.getVehicle() instanceof AreaEffectCloud).isEmpty();
		return new ModelHitbox.Facts(e.typeHolder().getRegisteredName(), v == null ? null : v.typeHolder().getRegisteredName(),
				e.isInvisible(), e instanceof Mob, owned, parts);
	}

	/**
	 * Fills {@code candidates} with named linked/nearby entities and {@code modelKeys} with the model's clouds (the
	 * target's own cloud vehicle, the nearest cloud within {@link NameResolver#RANGE} carrying several item_display
	 * bones); returns everything within capture range.
	 */
	private static List<CaptureLog.Nearby> around(Entity target, List<NameResolver.Candidate> candidates,
			List<Integer> modelKeys) {
		AABB box = target.getBoundingBox();
		Set<Entity> seen = new LinkedHashSet<>();
		for (Entity p : target.getPassengers()) seen.add(p);
		Entity vehicle = target.getVehicle();
		if (vehicle != null) seen.add(vehicle);
		// Every entity in the box is looked at for a name: a model can have more parts than the capture cap (the Mana
		// Wolf has 32 bone item_displays and 2 clouds before its name tag, 2026-10-03), so the cap only limits capture.
		seen.addAll(target.level().getEntities(target, box.inflate(CAPTURE_RANGE), e -> !(e instanceof Player)));
		if (vehicle instanceof AreaEffectCloud) modelKeys.add(vehicle.getId());
		Entity bones = null;
		double bonesSq = NameResolver.RANGE * NameResolver.RANGE;
		List<CaptureLog.Nearby> near = new ArrayList<>();
		for (Entity e : seen) {
			if (e == target || e instanceof Player) continue;
			double distSq = box.distanceToSqr(e.position());
			if (e instanceof AreaEffectCloud && distSq <= bonesSq && boneCount(e) >= MIN_BONES) {
				bones = e;
				bonesSq = distSq;
			}
			String custom = e.hasCustomName() ? e.getCustomName().getString() : null;
			String text = e instanceof Display.TextDisplay td ? text(td) : null;
			NameResolver.Relation rel = e.getVehicle() == target ? NameResolver.Relation.RIDER
					: e == vehicle ? NameResolver.Relation.VEHICLE
					: e instanceof Display || e instanceof ArmorStand ? NameResolver.Relation.TAG
					: NameResolver.Relation.OTHER;
			String name = text != null ? NameResolver.firstLine(text) : custom != null ? NameResolver.firstLine(custom) : null;
			String source = e.typeHolder().getRegisteredName() + " #" + e.getId();
			if (name != null) candidates.add(new NameResolver.Candidate(rel, name, distSq, source, e.getId()));
			// Capture keeps the named ones whatever the cap, so a capture always shows the tag that was used.
			if (near.size() >= MAX_ENTITIES && name == null) continue;
			List<Integer> passengers = new ArrayList<>();
			for (Entity p : e.getPassengers()) passengers.add(p.getId());
			Entity v = e.getVehicle();
			near.add(new CaptureLog.Nearby(e.getId(), e.typeHolder().getRegisteredName(), custom, text,
					v == null ? null : v.getId(), passengers, Math.sqrt(distSq)));
		}
		if (bones != null && !modelKeys.contains(bones.getId())) modelKeys.add(bones.getId());
		return near;
	}

	/**
	 * A cloud carrying at least this many item_displays carries a model's bones (a Viper's carries 15; the cloud an
	 * interaction hitbox rides carries one).
	 */
	private static final int MIN_BONES = 2;

	private static int boneCount(Entity cloud) {
		int n = 0;
		for (Entity p : cloud.getPassengers()) if (p instanceof Display.ItemDisplay) n++;
		return n;
	}

	/** A text display's text: through the optional invoker, else its render state (may be null before rendering). */
	private static String text(Display.TextDisplay td) {
		Component c = null;
		if (!textInvokerBroken) {
			try {
				c = ((TextDisplayAccessor) td).cubewheel$getText();
			} catch (VirtualMachineError e) {
				throw e;
			} catch (Throwable t) {
				textInvokerBroken = true; // the accessor did not apply: use the render state from now on
			}
		}
		if (c == null) {
			Display.TextDisplay.TextRenderState state = td.textRenderState();
			if (state != null) c = state.text();
		}
		String s = c == null ? null : c.getString();
		return s == null || s.isBlank() ? null : s;
	}
}
