package net.mage.cubewheel.tracker.local;

import static net.mage.cubewheel.tracker.local.ManaMobFixtures.VIPER;
import static net.mage.cubewheel.tracker.local.ManaMobFixtures.WOLF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Grouping ManaCube custom-model parts (capture 2026-11-12) into named mobs. */
class ModelMobsTest {
	static final int WOLF_HITBOX = 143133269;
	static final int WOLF_HITBOX_CLOUD = 143133268;
	static final int WOLF_EXTRA_DISPLAY = 143133270;
	static final int WOLF_BONES_CLOUD = 143133233;
	static final int WOLF_TAG_CLOUD = 143134269;
	static final int WOLF_TAG = 143134270;
	static final int VIPER_HITBOX = 143289204;
	static final int VIPER_HITBOX_CLOUD = 143289203;
	static final int VIPER_EXTRA_DISPLAY = 143289205;
	static final int VIPER_BONES_CLOUD = 143289185;
	static final int VIPER_TAG = 143293511;
	static final int SLIME = 82124;
	static final int SLIME_CLOUD = 82121;
	static final int MULTIPLIER_STAND = 143229705;
	static final int COPY = 500_000_000;

	static List<Integer> bones(List<ModelMobs.Snap> snaps, int bonesCloud) {
		return ManaMobFixtures.byId(snaps, bonesCloud).passengers();
	}

	@Test void manaWolfFromCaptureIsOneMob() {
		List<List<ModelMobs.Snap>> lines = ManaMobFixtures.load(WOLF);
		assertEquals(2, lines.size());
		for (List<ModelMobs.Snap> snaps : lines) {
			List<ModelMob> mobs = ModelMobs.group(snaps);
			assertEquals(1, mobs.size(), mobs.toString());
			ModelMob wolf = mobs.get(0);
			assertEquals("Mana Wolf", wolf.name());
			assertEquals(WOLF_HITBOX, wolf.hitbox());
			assertEquals(WOLF_TAG, wolf.tagEntity());
			List<Integer> bones = bones(snaps, WOLF_BONES_CLOUD);
			assertEquals(32, bones.size());
			assertTrue(wolf.parts().containsAll(bones), "every bone");
			assertTrue(wolf.parts().containsAll(List.of(WOLF_HITBOX, WOLF_HITBOX_CLOUD, WOLF_EXTRA_DISPLAY,
					WOLF_BONES_CLOUD, WOLF_TAG_CLOUD, WOLF_TAG)), wolf.parts().toString());
			ModelMobs.Snap hitbox = ManaMobFixtures.byId(snaps, WOLF_HITBOX);
			assertEquals(hitbox.x(), wolf.x());
			assertEquals(hitbox.y(), wolf.y());
			assertEquals(hitbox.z(), wolf.z());
		}
		ModelMob afterHits = ModelMobs.group(lines.get(1)).get(0);
		assertTrue(afterHits.parts().containsAll(List.of(SLIME, SLIME_CLOUD)), "the recycled slime attaches");
		assertFalse(afterHits.parts().contains(MULTIPLIER_STAND), "the 1.3x armor stand is not a part");
	}

	@Test void viperFromCaptureIsOneMob() {
		for (List<ModelMobs.Snap> snaps : ManaMobFixtures.load(VIPER)) {
			List<ModelMob> mobs = ModelMobs.group(snaps);
			assertEquals(1, mobs.size(), mobs.toString());
			ModelMob viper = mobs.get(0);
			assertEquals("Viper", viper.name());
			assertEquals(VIPER_HITBOX, viper.hitbox());
			assertEquals(VIPER_TAG, viper.tagEntity());
			List<Integer> bones = bones(snaps, VIPER_BONES_CLOUD);
			assertEquals(15, bones.size());
			assertTrue(viper.parts().containsAll(bones), "every bone");
			assertTrue(viper.parts().containsAll(List.of(VIPER_HITBOX_CLOUD, VIPER_EXTRA_DISPLAY, VIPER_TAG)));
		}
	}

	@Test void recycledSlimeAttachesButNeverAnchors() {
		List<ModelMobs.Snap> snaps = ManaMobFixtures.load(VIPER).get(0);
		ModelMob viper = ModelMobs.group(snaps).get(0);
		assertTrue(viper.parts().contains(SLIME), viper.parts().toString());
		assertTrue(viper.parts().contains(SLIME_CLOUD));
		assertEquals(List.of(), ModelMobs.group(ManaMobFixtures.without(snaps, VIPER_HITBOX, VIPER_HITBOX_CLOUD)));
	}

	@Test void slimeEquidistantFromTwoModelsAttachesToNeither() {
		List<ModelMobs.Snap> snaps = ManaMobFixtures.load(VIPER).get(0);
		ModelMobs.Snap hitbox = ManaMobFixtures.byId(snaps, VIPER_HITBOX);
		ModelMobs.Snap slime = ManaMobFixtures.byId(snaps, SLIME);
		// The model mirrored through the slime in x/z: the copy's hitbox is exactly as far from the slime.
		List<ModelMobs.Snap> model = ManaMobFixtures.without(snaps, SLIME, SLIME_CLOUD);
		List<ModelMobs.Snap> mirror = ManaMobFixtures.copy(model, COPY, 2 * (slime.x() - hitbox.x()),
				2 * (slime.z() - hitbox.z()));
		List<ModelMob> mobs = ModelMobs.group(Stream.concat(snaps.stream(), mirror.stream()).toList());
		assertEquals(2, mobs.size(), mobs.toString());
		for (ModelMob m : mobs) {
			assertEquals("Viper", m.name());
			assertFalse(m.parts().contains(SLIME), m.hitbox() + " took the slime");
			assertFalse(m.parts().contains(SLIME_CLOUD));
		}
	}

	@Test void loaderMapsCaptureKeys() {
		List<ModelMobs.Snap> snaps = ManaMobFixtures.load(VIPER).get(0);
		ModelMobs.Snap hitbox = ManaMobFixtures.byId(snaps, VIPER_HITBOX);
		assertEquals("minecraft:interaction", hitbox.type());
		assertEquals(VIPER_HITBOX_CLOUD, hitbox.vehicle());
		assertEquals(List.of(), hitbox.passengers());
		assertNull(hitbox.customName(), "missing customName key is null");
		assertNull(hitbox.text(), "missing text key is null");
		assertFalse(hitbox.owned());
		assertEquals(-233.16452026367188, hitbox.x());
		assertEquals(56.0, hitbox.y());
		assertEquals(8.835443496704102, hitbox.z());
		ModelMobs.Snap cloud = ManaMobFixtures.byId(snaps, VIPER_HITBOX_CLOUD);
		assertNull(cloud.vehicle(), "missing vehicle key is null");
		assertEquals(List.of(VIPER_HITBOX, VIPER_EXTRA_DISPLAY), cloud.passengers());
		assertEquals("Viper 75⺛", ManaMobFixtures.byId(snaps, VIPER_TAG).text());
		ModelMobs.Snap slime = ManaMobFixtures.byId(snaps, SLIME);
		assertTrue(slime.invisible());
		assertTrue(slime.mob());
		assertFalse(slime.owned());
	}

	@Test void blankOrMultiplierTagIsNotAName() {
		List<ModelMobs.Snap> snaps = ManaMobFixtures.load(WOLF).get(0);
		for (String text : List.of("", "2.5x")) {
			List<ModelMobs.Snap> edited = snaps.stream()
					.map(s -> s.id() == WOLF_TAG ? new ModelMobs.Snap(s.id(), s.type(), s.vehicle(), s.passengers(),
							s.invisible(), s.mob(), s.owned(), s.customName(), text, s.x(), s.y(), s.z()) : s)
					.toList();
			assertEquals(List.of(), ModelMobs.group(edited), "tag \"" + text + "\"");
		}
	}

	@Test void visibleVanillaMobNearbyIsNotAPart() {
		List<ModelMobs.Snap> snaps = new ArrayList<>(ManaMobFixtures.load(WOLF).get(0));
		ModelMobs.Snap hitbox = ManaMobFixtures.byId(snaps, WOLF_HITBOX);
		int wolfId = 999_999;
		snaps.add(new ModelMobs.Snap(wolfId, "minecraft:wolf", null, List.of(), false, true, false, null, null,
				hitbox.x() + 1, hitbox.y(), hitbox.z()));
		List<ModelMob> mobs = ModelMobs.group(snaps);
		assertEquals(1, mobs.size());
		for (ModelMob m : mobs) assertFalse(m.parts().contains(wolfId));
	}

	@Test void twoModelsTwoMobs() {
		List<ModelMobs.Snap> snaps = ManaMobFixtures.load(WOLF).get(0);
		List<ModelMobs.Snap> other = ManaMobFixtures.copy(snaps, COPY, 10, 0);
		List<ModelMob> mobs = ModelMobs.group(Stream.concat(snaps.stream(), other.stream()).toList());
		assertEquals(2, mobs.size(), mobs.toString());
		assertEquals(List.of(WOLF_HITBOX, WOLF_HITBOX + COPY), mobs.stream().map(ModelMob::hitbox).toList());
		for (ModelMob m : mobs) assertEquals("Mana Wolf", m.name());
		assertFalse(mobs.get(0).parts().stream().anyMatch(mobs.get(1).parts()::contains), "no shared parts");
	}

	@Test void tagFallsBackToNearestWhenNoneIsCoLocated() {
		List<ModelMobs.Snap> snaps = ManaMobFixtures.load(VIPER).get(0).stream()
				.map(s -> s.id() == VIPER_TAG ? new ModelMobs.Snap(s.id(), s.type(), null, s.passengers(), s.invisible(),
						s.mob(), s.owned(), s.customName(), s.text(), s.x() + 1, s.y(), s.z()) : s)
				.toList();
		List<ModelMob> mobs = ModelMobs.group(snaps);
		assertEquals(1, mobs.size(), mobs.toString());
		assertEquals("Viper", mobs.get(0).name());
		assertEquals(VIPER_TAG, mobs.get(0).tagEntity());
	}

	@Test void manaWolfBoundsCoverTheBonesNotJustTheInteraction() {
		for (List<ModelMobs.Snap> snaps : ManaMobFixtures.load(WOLF)) {
			ModelMob wolf = ModelMobs.group(snaps).get(0);
			ModelBounds b = wolf.bounds();
			ModelBounds interaction = ModelBounds.point(0, 0, 0); // capture has no interaction size: a point
			assertTrue(b.contains(interaction), b.toString());
			assertTrue(b.volume() > interaction.volume(), b.toString());
			// bones sit 0.6875 above the interaction, at its x/z; inflated by 0.5
			assertEquals(0.6875 + ModelMobs.BONE_MARGIN, b.maxY(), 1e-9);
			assertEquals(-ModelMobs.BONE_MARGIN, b.minX(), 1e-9);
			assertEquals(ModelMobs.BONE_MARGIN, b.maxZ(), 1e-9);
			assertEquals(0, b.minY(), 1e-9); // never below the interaction's own bottom (its feet)
			assertEquals(new ModelBounds(0, 0, 0, 0, 0.6875, 0), wolf.core()); // interaction point + raw bones
		}
	}

	@Test void boundsUseBoneTranslationsAndTheInteractionBox() {
		List<ModelMobs.Snap> snaps = ManaMobFixtures.load(WOLF).get(0);
		ModelMobs.Snap hitbox = ManaMobFixtures.byId(snaps, WOLF_HITBOX);
		List<Integer> bones = bones(snaps, WOLF_BONES_CLOUD);
		ModelBounds interactionBox = new ModelBounds(-0.4, 0, -0.4, 0.4, 0.9, 0.4);
		List<ModelMobs.Snap> edited = snaps.stream().map(s -> {
			if (s.id() == WOLF_HITBOX) return s.withExtent(interactionBox);
			if (s.id() == bones.get(0)) return s.withExtent(ModelBounds.aroundAxis(1.2, 0.3, 0)); // a head bone
			if (s.id() == bones.get(1)) return s.withExtent(ModelBounds.aroundAxis(0, -0.6, 0)); // a foot bone
			return s;
		}).toList();
		ModelBounds b = ModelMobs.group(edited).get(0).bounds();
		double boneDy = ManaMobFixtures.byId(snaps, bones.get(0)).y() - hitbox.y(); // 0.6875
		double m = ModelMobs.BONE_MARGIN; // the hitbox cloud's extra item_display (dy 0) counts as a bone too
		assertEquals(new ModelBounds(-1.2 - m, 0, -1.2 - m, 1.2 + m, Math.max(0.9, boneDy + 0.3 + m), 1.2 + m), b);
		assertTrue(b.contains(interactionBox));
		assertEquals(interactionBox.minY(), b.minY(), 1e-9); // the inflation stops at the feet
		assertEquals(new ModelBounds(-1.2, 0, -1.2, 1.2, Math.max(0.9, boneDy + 0.3), 1.2), ModelMobs.group(edited).get(0).core());
	}
}
