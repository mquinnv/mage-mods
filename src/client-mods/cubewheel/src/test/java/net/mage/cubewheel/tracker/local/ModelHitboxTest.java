package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModelHitboxTest {
	static final String CLOUD = "minecraft:area_effect_cloud";

	static ModelHitbox.Facts facts(String type, String vehicle, boolean invisible, boolean mob, boolean owned, boolean parts) {
		return new ModelHitbox.Facts(type, vehicle, invisible, mob, owned, parts);
	}

	@Test void anInteractionRidingACloudIsAModelHitbox() {
		assertTrue(ModelHitbox.isModelHitbox(facts("minecraft:interaction", CLOUD, false, false, false, true)));
		assertTrue(ModelHitbox.isModelHitbox(facts("minecraft:interaction", CLOUD, false, false, false, false)));
	}

	@Test void anInvisibleSlimeByModelPartsIsAModelHitbox() {
		assertTrue(ModelHitbox.isModelHitbox(facts("minecraft:slime", CLOUD, true, true, false, true)));
		assertTrue(ModelHitbox.isModelHitbox(facts("minecraft:slime", null, true, true, false, true)));
	}

	@Test void hitboxShapesWithoutTheirModelAreNot() {
		assertFalse(ModelHitbox.isModelHitbox(facts("minecraft:interaction", null, false, false, false, true)));
		assertFalse(ModelHitbox.isModelHitbox(facts("minecraft:slime", CLOUD, true, true, false, false)), "no parts near");
		assertFalse(ModelHitbox.isModelHitbox(facts("minecraft:slime", CLOUD, false, true, false, true)), "visible");
		assertFalse(ModelHitbox.isModelHitbox(facts("minecraft:villager", null, true, false, false, true)), "not a mob");
	}

	@Test void itemsDisplaysArmorStandsProjectilesVehiclesPlayersAndPetsNeverAre() {
		for (String type : new String[] { "minecraft:item", "minecraft:experience_orb", "minecraft:item_display",
				"minecraft:text_display", "minecraft:block_display", "minecraft:armor_stand", "minecraft:arrow",
				"minecraft:spectral_arrow", "minecraft:breeze_wind_charge", "minecraft:wind_charge", "minecraft:trident",
				"minecraft:snowball", "minecraft:egg", "minecraft:fireball", "minecraft:small_fireball",
				"minecraft:oak_boat", "minecraft:bamboo_raft", "minecraft:minecart", "minecraft:chest_minecart",
				"minecraft:player" }) {
			assertFalse(ModelHitbox.isModelHitbox(facts(type, CLOUD, true, true, false, true)), type);
		}
		assertFalse(ModelHitbox.isModelHitbox(facts("minecraft:wolf", CLOUD, true, true, true, true)), "owned");
		assertFalse(ModelHitbox.isModelHitbox(null));
	}

	@Test void projectilesAndVehiclesAreNeverAKill() {
		assertTrue(ModelHitbox.neverAKill("minecraft:breeze_wind_charge"));
		assertTrue(ModelHitbox.neverAKill("minecraft:arrow"));
		assertTrue(ModelHitbox.neverAKill("minecraft:trident"));
		assertTrue(ModelHitbox.neverAKill("minecraft:snowball"));
		assertTrue(ModelHitbox.neverAKill("minecraft:oak_boat"));
		assertTrue(ModelHitbox.neverAKill("minecraft:tnt_minecart"));
		assertFalse(ModelHitbox.neverAKill("minecraft:slime"));
		assertFalse(ModelHitbox.neverAKill("minecraft:interaction"));
		assertFalse(ModelHitbox.neverAKill("minecraft:blaze"));
		assertFalse(ModelHitbox.neverAKill("minecraft:ghast"));
		assertFalse(ModelHitbox.neverAKill(null));
	}
}
