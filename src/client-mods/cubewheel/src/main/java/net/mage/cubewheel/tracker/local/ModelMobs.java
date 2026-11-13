package net.mage.cubewheel.tracker.local;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.mage.cubewheel.capture.CaptureLog;

/**
 * Groups the entities of ManaCube custom-model mobs (ModelEngine) into named {@link ModelMob}s. The shape, from the
 * 2026-11-12 capture (Mana Wolf, Viper): a {@code minecraft:interaction} hitbox riding a cloud, a bones cloud carrying
 * the {@code item_display} bones, and a {@code text_display} tag riding a third cloud, all three clouds at exactly
 * the same x/z. There is no invisible base mob. The one invisible Mob nearby is the player's recycled slime, riding
 * a cloud of its own: it is attached to the nearest model (a look often hits it) but never anchors one, which is
 * why this does not use {@link ModelHitbox#isModelHitbox} (that accepts the slime, as the kill tracker needs).
 * Pure: no Minecraft/Fabric imports.
 */
public final class ModelMobs {
	private static final String INTERACTION = ModelHitbox.INTERACTION;
	private static final String CLOUD = ModelHitbox.CLOUD;
	private static final String ITEM_DISPLAY = "minecraft:item_display";
	private static final String TEXT_DISPLAY = "minecraft:text_display";
	/**
	 * A cloud carrying at least this many item_displays carries a model's bones (a Viper's carries 15; the cloud an
	 * interaction hitbox rides carries one).
	 */
	public static final int MIN_BONES = 2;
	/** Clouds this close in x and in z belong to the same model (the capture's offsets are exactly 0). */
	static final double CO_LOCATED = 0.01;
	/** A recycled slime this much nearer one model than the next attaches; closer to a tie, it attaches to neither. */
	static final double SLIME_TIE = 0.25;
	/** How far a model's box reaches past its bones (each bone's item model has a size of its own). */
	public static final double BONE_MARGIN = 0.5;

	/**
	 * One entity as the grouping sees it: the fields of {@link CaptureLog.Part}, plus {@code owned} (a tamed pet) and
	 * {@code extent}, which capture does not record. {@code extent} is what the entity covers relative to its own
	 * position: an interaction's box, or where an item_display's translation can put its bone
	 * ({@link ModelBounds#aroundAxis}); null for a point.
	 */
	public record Snap(int id, String type, Integer vehicle, List<Integer> passengers, boolean invisible, boolean mob,
			boolean owned, String customName, String text, double x, double y, double z, ModelBounds extent) {
		public Snap {
			passengers = passengers == null ? List.of() : List.copyOf(passengers);
		}

		/** A snap with no extent (a point). */
		public Snap(int id, String type, Integer vehicle, List<Integer> passengers, boolean invisible, boolean mob,
				boolean owned, String customName, String text, double x, double y, double z) {
			this(id, type, vehicle, passengers, invisible, mob, owned, customName, text, x, y, z, null);
		}

		public Snap withExtent(ModelBounds e) {
			return new Snap(id, type, vehicle, passengers, invisible, mob, owned, customName, text, x, y, z, e);
		}

		/** {@link #extent}, or the point at the entity's own position. */
		ModelBounds local() {
			return extent == null ? ModelBounds.point(0, 0, 0) : extent;
		}

		/** A captured part; {@code owned} is false. */
		public static Snap from(CaptureLog.Part p) {
			return new Snap(p.entity(), p.type(), p.vehicle(), p.passengers(), p.invisible(), p.mob(), false,
					p.customName(), p.text(), p.x(), p.y(), p.z());
		}

		double distanceSq(Snap o) {
			double dx = x - o.x, dy = y - o.y, dz = z - o.z;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	private ModelMobs() {}

	/** Does a cloud carrying this many item_displays carry a model's bones? */
	public static boolean carriesBones(int itemDisplayPassengers) {
		return itemDisplayPassengers >= MIN_BONES;
	}

	/**
	 * The named model mobs among {@code snaps}, in the order of their hitboxes. A model is anchored only on an
	 * interaction riding a cloud whose co-located clouds include a bones cloud. Its parts are the interaction, every
	 * co-located cloud and their passengers, and an attached recycled slime with its cloud. Its bounds are the
	 * interaction's box joined with its bones' box (every item_display riding a co-located cloud, inflated by
	 * {@link #BONE_MARGIN} but not below the interaction's bottom), relative to the interaction; its core is the
	 * same without the margin. A model whose tag gives no name (blank, or a "1.3x"
	 * multiplier) is left out.
	 */
	public static List<ModelMob> group(List<Snap> snaps) {
		Map<Integer, Snap> byId = new LinkedHashMap<>();
		for (Snap s : snaps) byId.putIfAbsent(s.id(), s);
		List<Draft> drafts = new ArrayList<>();
		for (Snap s : byId.values()) {
			Draft d = anchor(s, byId);
			if (d != null) drafts.add(d);
		}
		for (Snap s : byId.values()) attachSlime(s, byId, drafts);
		List<ModelMob> mobs = new ArrayList<>();
		for (Draft d : drafts) {
			if (d.name == null) continue;
			Snap h = d.hitbox;
			mobs.add(new ModelMob(d.name, d.tagEntity, h.id(), h.x(), h.y(), h.z(), d.parts, d.bounds, d.core));
		}
		return List.copyOf(mobs);
	}

	/** A model in progress: its hitbox, parts, tag and name (null if none). */
	private static final class Draft {
		final Snap hitbox;
		final Set<Integer> parts = new LinkedHashSet<>();
		Integer tagEntity;
		String name;
		ModelBounds bounds;
		ModelBounds core;

		Draft(Snap hitbox) {
			this.hitbox = hitbox;
		}
	}

	private static Draft anchor(Snap s, Map<Integer, Snap> byId) {
		if (!INTERACTION.equals(s.type()) || s.vehicle() == null) return null;
		Snap cloud = byId.get(s.vehicle());
		if (cloud == null || !CLOUD.equals(cloud.type())) return null;
		List<Snap> clouds = new ArrayList<>();
		for (Snap c : byId.values()) {
			if (CLOUD.equals(c.type()) && Math.abs(c.x() - cloud.x()) < CO_LOCATED
					&& Math.abs(c.z() - cloud.z()) < CO_LOCATED) clouds.add(c);
		}
		if (clouds.stream().noneMatch(c -> carriesBones(itemDisplays(c, byId)))) return null;
		Draft d = new Draft(s);
		d.parts.add(s.id());
		Set<Integer> cloudIds = new LinkedHashSet<>();
		ModelBounds bones = null;
		for (Snap c : clouds) {
			cloudIds.add(c.id());
			d.parts.add(c.id());
			d.parts.addAll(c.passengers());
			for (int p : c.passengers()) {
				Snap b = byId.get(p);
				if (b == null || !ITEM_DISPLAY.equals(b.type())) continue;
				ModelBounds rel = b.local().move(b.x() - s.x(), b.y() - s.y(), b.z() - s.z());
				bones = bones == null ? rel : bones.union(rel);
			}
		}
		ModelBounds hit = s.local();
		d.core = bones == null ? hit : bones.union(hit);
		if (bones == null) {
			d.bounds = hit;
		} else {
			// the margin never reaches below the interaction's bottom: a look at the ground beside a mob is not the mob
			ModelBounds wide = bones.inflate(BONE_MARGIN);
			d.bounds = wide.withMinY(Math.max(wide.minY(), hit.minY())).union(hit);
		}
		Optional<Snap> tag = byId.values().stream()
				.filter(t -> TEXT_DISPLAY.equals(t.type()) && t.vehicle() != null && cloudIds.contains(t.vehicle()))
				.min(Comparator.comparingDouble(t -> t.distanceSq(s)));
		if (tag.isPresent()) {
			d.tagEntity = tag.get().id();
			d.name = name(tag.get().text());
		} else {
			NameResolver.pick(candidates(s, byId)).ifPresent(c -> {
				d.tagEntity = c.entityId();
				d.name = name(c.name());
			});
		}
		return d;
	}

	private static int itemDisplays(Snap cloud, Map<Integer, Snap> byId) {
		int n = 0;
		for (int p : cloud.passengers()) {
			Snap s = byId.get(p);
			if (s != null && ITEM_DISPLAY.equals(s.type())) n++;
		}
		return n;
	}

	/** Named entities near the hitbox, related to it the way {@code NearbyProbe} relates them. */
	private static List<NameResolver.Candidate> candidates(Snap hitbox, Map<Integer, Snap> byId) {
		List<NameResolver.Candidate> out = new ArrayList<>();
		for (Snap e : byId.values()) {
			if (e.id() == hitbox.id() || "minecraft:player".equals(e.type())) continue;
			String name = e.text() != null ? NameResolver.firstLine(e.text())
					: e.customName() != null ? NameResolver.firstLine(e.customName()) : null;
			if (name == null) continue;
			NameResolver.Relation rel = Integer.valueOf(hitbox.id()).equals(e.vehicle()) ? NameResolver.Relation.RIDER
					: Integer.valueOf(e.id()).equals(hitbox.vehicle()) ? NameResolver.Relation.VEHICLE
					: isTag(e.type()) ? NameResolver.Relation.TAG
					: NameResolver.Relation.OTHER;
			out.add(new NameResolver.Candidate(rel, name, e.distanceSq(hitbox), e.type() + " #" + e.id(), e.id()));
		}
		return out;
	}

	private static boolean isTag(String type) {
		return type != null && (type.endsWith("_display") || type.equals("minecraft:armor_stand"));
	}

	/** The bare mob name in a tag's text ("Mana Wolf 60⺛" -> "Mana Wolf"), or null if it gives none. */
	private static String name(String tagText) {
		String name = StackName.parse(NameResolver.firstLine(tagText)).map(StackName.Parsed::name).orElse(null);
		return name == null || name.isBlank() || NameResolver.isMultiplier(name) ? null : name;
	}

	/**
	 * An invisible, unowned Mob riding a cloud that carries nothing else (the recycled slime) joins the nearest model
	 * within {@link NameResolver#RANGE}, with its cloud; it joins none when a second model is within
	 * {@link #SLIME_TIE} of the same distance.
	 */
	private static void attachSlime(Snap s, Map<Integer, Snap> byId, List<Draft> drafts) {
		if (!s.mob() || !s.invisible() || s.owned() || s.vehicle() == null) return;
		Snap cloud = byId.get(s.vehicle());
		if (cloud == null || !CLOUD.equals(cloud.type()) || !cloud.passengers().equals(List.of(s.id()))) return;
		Draft best = null;
		double bestD = Double.MAX_VALUE, nextD = Double.MAX_VALUE;
		for (Draft d : drafts) {
			double dist = Math.sqrt(d.hitbox.distanceSq(s));
			if (dist > NameResolver.RANGE) continue;
			if (dist < bestD) {
				nextD = bestD;
				bestD = dist;
				best = d;
			} else if (dist < nextD) {
				nextD = dist;
			}
		}
		if (best == null || nextD - bestD <= SLIME_TIE) return;
		best.parts.add(s.id());
		best.parts.add(cloud.id());
	}
}
