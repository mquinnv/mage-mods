package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegistryStateTest {
	private static final String BEAR = "minecraft:polar_bear";

	private static ModelMob mob(String name, int hitbox, double x, double y, double z) {
		return new ModelMob(name, hitbox + 1, hitbox, x, y, z, Set.of(hitbox, hitbox + 1, hitbox + 2));
	}

	private static RegistryState with(ModelMob... mobs) {
		RegistryState s = new RegistryState();
		s.replace(List.of(mobs));
		return s;
	}

	@Test void byEntityFindsMobFromAnyPart() {
		ModelMob wolf = mob("Mana Wolf", 10, 0, 64, 0);
		RegistryState s = with(wolf);
		assertEquals(Optional.of(wolf), s.byEntity(10));
		assertEquals(Optional.of(wolf), s.byEntity(11));
		assertEquals(Optional.of(wolf), s.byEntity(12));
		assertEquals(Optional.empty(), s.byEntity(99));
	}

	@Test void firstSoundLearnsVoiceAndStaysVanilla() {
		ModelMob wolf = mob("Mana Wolf", 10, 0, 64, 0);
		RegistryState s = with(wolf);
		assertEquals(Optional.empty(), s.nearSound(BEAR, 0.2, 64, 0.2));
		assertEquals(Optional.of(wolf), s.nearSound(BEAR, 0.2, 64, 0.2));
	}

	@Test void otherVoiceAfterLearningIsEmpty() {
		RegistryState s = with(mob("Mana Wolf", 10, 0, 64, 0));
		s.nearSound(BEAR, 0, 64, 0);
		assertTrue(s.nearSound(BEAR, 0, 64, 0).isPresent());
		assertEquals(Optional.empty(), s.nearSound("minecraft:wolf", 0, 64, 0));
	}

	@Test void voiceIsPerNameNotPerEntity() {
		ModelMob a = mob("Mana Wolf", 10, 0, 64, 0);
		ModelMob b = mob("Mana Wolf", 20, 50, 64, 50);
		RegistryState s = with(a, b);
		assertEquals(Optional.empty(), s.nearSound(BEAR, 0, 64, 0)); // the first sound at a learns the name's voice
		assertEquals(Optional.of(b), s.nearSound(BEAR, 50, 64, 50)); // b is another entity of the same name: matches at once
	}

	@Test void aDifferentNameLearnsItsOwnVoice() {
		ModelMob wolf = mob("Mana Wolf", 10, 0, 64, 0);
		ModelMob viper = mob("Viper", 30, 90, 64, 90);
		RegistryState s = with(wolf, viper);
		s.nearSound(BEAR, 0, 64, 0);
		assertEquals(Optional.empty(), s.nearSound(BEAR, 90, 64, 90));
		assertEquals(Optional.of(viper), s.nearSound(BEAR, 90, 64, 90));
	}

	@Test void windowEdges() {
		RegistryState s = with(mob("Mana Wolf", 10, 0, 64, 0));
		s.nearSound(BEAR, 0, 64, 0); // learn
		assertTrue(s.nearSound(BEAR, 0.99, 64, 0).isPresent());
		assertTrue(s.nearSound(BEAR, 0.7, 64, 0.7).isPresent()); // sqrt(dx^2+dz^2) = 0.99
		assertEquals(Optional.empty(), s.nearSound(BEAR, 1.01, 64, 0));
		assertTrue(s.nearSound(BEAR, 0, 63.5, 0).isPresent());
		assertTrue(s.nearSound(BEAR, 0, 65.5, 0).isPresent());
		assertEquals(Optional.empty(), s.nearSound(BEAR, 0, 63.49, 0));
		assertEquals(Optional.empty(), s.nearSound(BEAR, 0, 65.51, 0));
		assertTrue(s.nearSound(BEAR, 0.56, 64, 0).isPresent()); // the capture's later sample
	}

	@Test void ambiguousSoundStaysVanilla() {
		RegistryState s = with(mob("Mana Wolf", 10, 0.5, 64, 0), mob("Mana Wolf", 20, 0, 64, 0.6));
		s.nearSound(BEAR, 0, 64, 0); // learns
		assertEquals(Optional.empty(), s.nearSound(BEAR, 0, 64, 0)); // 0.5 and 0.6: too close to call
		RegistryState t = with(mob("Mana Wolf", 10, 0.2, 64, 0), mob("Mana Wolf", 20, -0.9, 64, 0));
		t.nearSound(BEAR, 0, 64, 0);
		assertTrue(t.nearSound(BEAR, 0, 64, 0).isPresent()); // clearly the nearer one
	}

	@Test void clearsWhenLeavingManaWorld() {
		ModelMob wolf = mob("Mana Wolf", 10, 0, 64, 0);
		RegistryState s = with(wolf);
		s.nearSound(BEAR, 0, 64, 0);
		s.clear();
		assertEquals(Optional.empty(), s.byEntity(10));
		assertEquals(Optional.empty(), s.nearSound(BEAR, 0, 64, 0));
		s.replace(List.of(wolf));
		assertEquals(Optional.of(wolf), s.nearSound(BEAR, 0, 64, 0)); // voice kept across the warp
		s.clearAll();
		s.replace(List.of(wolf));
		assertEquals(Optional.empty(), s.nearSound(BEAR, 0, 64, 0)); // learning again
	}
}
