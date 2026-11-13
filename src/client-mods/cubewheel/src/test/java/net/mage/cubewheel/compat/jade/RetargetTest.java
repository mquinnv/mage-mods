package net.mage.cubewheel.compat.jade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.List;
import net.mage.cubewheel.tracker.local.ModelBounds;
import net.mage.cubewheel.tracker.local.ModelMob;
import org.junit.jupiter.api.Test;

class RetargetTest {
	// Mana Wolf from the 2026-11-12 capture: interaction #143133269, recycled slime #82124, a bone #143133300.
	private static final ModelMob WOLF =
			new ModelMob("Mana Wolf", 143133280, 143133269, 0, 0, 0, Set.of(143133269, 82124, 143133300),
					ModelBounds.point(0, 0, 0));
	private static final Map<Integer, ModelMob> BY_PART = Map.of(143133269, WOLF, 82124, WOLF, 143133300, WOLF);
	private static final IntFunction<Optional<ModelMob>> MOBS = id -> Optional.ofNullable(BY_PART.get(id));

	@Test
	void jadeBlockHitButVanillaHitsTheInteractionRedirectsViaVanilla() {
		// the default case: Jade hides interactions, so its ray lands on the block behind
		assertEquals(Optional.of(new Retarget.Redirect(143133269, true)), Retarget.decide(null, 143133269, MOBS));
	}

	@Test
	void vanillaHitOnTheSlimeRedirectsToTheHitbox() {
		assertEquals(Optional.of(new Retarget.Redirect(143133269, true)), Retarget.decide(null, 82124, MOBS));
	}

	@Test
	void jadeAlreadyOnTheHitboxIsLeftAlone() {
		assertEquals(Optional.empty(), Retarget.decide(143133269, 143133269, MOBS));
	}

	@Test
	void jadeOnAnotherPartRedirectsWithoutVanilla() {
		assertEquals(Optional.of(new Retarget.Redirect(143133269, false)), Retarget.decide(143133300, null, MOBS));
	}

	@Test
	void jadeOnAnUnknownEntityDefersToVanillaMob() {
		// Jade skipped the hidden interaction and hit something behind it; vanilla's closer pick wins
		assertEquals(Optional.of(new Retarget.Redirect(143133269, true)), Retarget.decide(555, 143133269, MOBS));
	}

	@Test
	void nothingKnownLeavesJadeAlone() {
		assertEquals(Optional.empty(), Retarget.decide(555, 777, MOBS));
		assertEquals(Optional.empty(), Retarget.decide(null, null, MOBS));
		assertEquals(Optional.empty(), Retarget.decide(555, null, MOBS));
	}

	@Test
	void toggleOffOrNoManaWorldMeansEmptyLookupAndNoRedirect() {
		assertEquals(Optional.empty(), Retarget.decide(82124, 143133269, id -> Optional.empty()));
	}

	// A Mana Wolf model box around x = 3 (live interaction position + its relative bounds), eye at the origin looking +x.
	private static final ModelBounds NEAR_BOX = new ModelBounds(2.5, -0.5, -0.7, 3.5, 0.6, 0.7);
	private static final ModelBounds FAR_BOX = new ModelBounds(4.5, -0.5, -0.7, 5.5, 0.6, 0.7);
	private static final double INF = Double.POSITIVE_INFINITY;

	@Test
	void rayThroughAModelBoxPicksItsHitbox() {
		Optional<Retarget.Pick> pick = Retarget.rayPick(0, 0, 0, 1, 0, 0, 6, INF, List.of(new Retarget.Candidate(7, NEAR_BOX)));
		assertEquals(7, pick.orElseThrow().hitbox());
		assertEquals(2.5, pick.orElseThrow().distance(), 1e-9);
	}

	@Test
	void rayMissingEveryBoxPicksNothing() {
		assertEquals(Optional.empty(), Retarget.rayPick(0, 0, 0, 0, 0, 1, 6, INF, List.of(new Retarget.Candidate(7, NEAR_BOX))));
	}

	@Test
	void blockNearerThanTheBoxPicksNothing() {
		// a wall at 2.0: never name a mob through it
		assertEquals(Optional.empty(), Retarget.rayPick(0, 0, 0, 1, 0, 0, 6, 2.0, List.of(new Retarget.Candidate(7, NEAR_BOX))));
		assertEquals(7, Retarget.rayPick(0, 0, 0, 1, 0, 0, 6, 3.0, List.of(new Retarget.Candidate(7, NEAR_BOX))).orElseThrow().hitbox());
	}

	@Test
	void twoBoxesOnTheRayPickTheNearest() {
		List<Retarget.Candidate> both = List.of(new Retarget.Candidate(9, FAR_BOX), new Retarget.Candidate(7, NEAR_BOX));
		assertEquals(7, Retarget.rayPick(0, 0, 0, 1, 0, 0, 6, INF, both).orElseThrow().hitbox());
	}

	@Test
	void boxBeyondTheEntityRangePicksNothing() {
		assertEquals(Optional.empty(), Retarget.rayPick(0, 0, 0, 1, 0, 0, 3, INF, List.of(new Retarget.Candidate(9, FAR_BOX))));
	}
}
