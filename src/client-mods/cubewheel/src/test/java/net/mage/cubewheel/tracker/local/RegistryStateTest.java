package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

class RegistryStateTest {
	private static ModelMob mob(String name, int hitbox, double x, double y, double z) {
		return new ModelMob(name, hitbox + 1, hitbox, x, y, z, Set.of(hitbox, hitbox + 1, hitbox + 2),
				ModelBounds.point(0, 0, 0), ModelBounds.point(0, 0, 0));
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

	@Test void theFirstSoundIsRenamedAtOnce() {
		ModelMob wolf = mob("Mana Wolf", 10, 0, 64, 0);
		RegistryState s = with(wolf);
		assertEquals(Optional.of(wolf), s.nearSound(0.2, 64, 0.2));
	}

	@Test void everySoundAtTheMobIsItsOwn() {
		// a Mana Slime dies with slime.death and strider.death at once (capture 2026-11-13): both are the slime's
		ModelMob slime = mob("Mana Slime", 10, 0, 64, 0);
		RegistryState s = with(slime);
		assertEquals(Optional.of(slime), s.nearSound(0, 64, 0));
		assertEquals(Optional.of(slime), s.nearSound(0, 64, 0));
	}

	@Test void windowEdges() {
		RegistryState s = with(mob("Mana Wolf", 10, 0, 64, 0));
		assertTrue(s.nearSound(0.99, 64, 0).isPresent());
		assertTrue(s.nearSound(0.7, 64, 0.7).isPresent()); // sqrt(dx^2+dz^2) = 0.99
		assertEquals(Optional.empty(), s.nearSound(1.01, 64, 0));
		assertTrue(s.nearSound(0, 63.5, 0).isPresent());
		assertTrue(s.nearSound(0, 65.5, 0).isPresent());
		assertEquals(Optional.empty(), s.nearSound(0, 63.49, 0));
		assertEquals(Optional.empty(), s.nearSound(0, 65.51, 0));
		assertTrue(s.nearSound(0.56, 64, 0).isPresent()); // the capture's later sample
	}

	@Test void ambiguousSoundStaysVanilla() {
		RegistryState s = with(mob("Mana Wolf", 10, 0.5, 64, 0), mob("Mana Wolf", 20, 0, 64, 0.6));
		assertEquals(Optional.empty(), s.nearSound(0, 64, 0)); // 0.5 and 0.6: too close to call
		RegistryState t = with(mob("Mana Wolf", 10, 0.2, 64, 0), mob("Mana Wolf", 20, -0.9, 64, 0));
		assertTrue(t.nearSound(0, 64, 0).isPresent()); // clearly the nearer one
	}

	@Test void clearsWhenLeavingManaWorld() {
		ModelMob wolf = mob("Mana Wolf", 10, 0, 64, 0);
		RegistryState s = with(wolf);
		s.clear();
		assertEquals(Optional.empty(), s.byEntity(10));
		assertEquals(Optional.empty(), s.nearSound(0, 64, 0));
		s.replace(List.of(wolf));
		assertEquals(Optional.of(wolf), s.nearSound(0, 64, 0));
	}

	private static IntFunction<double[]> live(Map<Integer, double[]> positions) {
		return positions::get;
	}

	@Test void liveHitboxPositionBeatsTheStaleSnapshot() {
		// the snapshot (once a second) left the wolf 3 blocks back; it has since run to where the sound is
		ModelMob wolf = mob("Mana Wolf", 10, -3, 64, 0);
		RegistryState s = with(wolf);
		IntFunction<double[]> now = live(Map.of(10, new double[] {0, 64, 0}));
		assertEquals(Optional.of(wolf), s.nearSound(0.1, 64, 0, now));
		assertEquals(Optional.empty(), s.nearSound(0.1, 64, 0)); // the snapshot alone is 3.1 away
	}

	@Test void absentLivePositionFallsBackToTheSnapshot() {
		ModelMob wolf = mob("Mana Wolf", 10, 0, 64, 0);
		RegistryState s = with(wolf);
		assertEquals(Optional.of(wolf), s.nearSound(0.2, 64, 0, live(Map.of())));
	}

	@Test void matchSoundExplainsEachDecision() {
		ModelMob wolf = mob("Mana Wolf", 10, 0, 64, 0);
		RegistryState s = with(wolf, mob("Mana Wolf", 20, 0.1, 64, 0.05));
		IntFunction<double[]> none = live(Map.of());
		RegistryState.SoundMatch far = s.matchSound(5, 64, 0, none);
		assertEquals(RegistryState.SoundDecision.NO_CANDIDATE, far.decision());
		assertEquals(20, far.mob().hitbox()); // the nearest mob anyway, for the capture line
		assertEquals(4.9, far.dx(), 1e-9);
		assertEquals(RegistryState.SoundDecision.AMBIGUOUS, s.matchSound(0, 64, 0, none).decision());
		RegistryState.SoundMatch hit = with(wolf).matchSound(0.2, 64.5, 0, none);
		assertEquals(RegistryState.SoundDecision.RENAMED, hit.decision());
		assertEquals(0.5, hit.dy(), 1e-9);
		assertEquals(RegistryState.SoundDecision.NO_CANDIDATE, new RegistryState().matchSound(0, 0, 0, none).decision());
		assertEquals(null, new RegistryState().matchSound(0, 0, 0, none).mob());
	}

	@Test void mobsListsTheCurrentMobs() {
		ModelMob wolf = mob("Mana Wolf", 10, 0, 64, 0);
		RegistryState s = with(wolf);
		assertEquals(List.of(wolf), s.mobs());
		s.clear();
		assertEquals(List.of(), s.mobs());
	}
}
