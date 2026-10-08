package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.mage.cubewheel.hud.HudLayout.Box;
import net.mage.cubewheel.hud.HudLayout.Corner;
import org.junit.jupiter.api.Test;

class SnapTest {
	static final int W = 400, H = 300;
	static final Snap.Offsets OFFSETS = c -> c == Corner.BOTTOM_CENTER ? new int[] {0, 62} : new int[] {4, 4};
	/** Status (0) and Jobs (1) stacked top left, Quests (2) top right. */
	static final List<Snap.Other> OTHERS = List.of(
			new Snap.Other(0, Corner.TOP_LEFT, new Box(4, 4, 100, 40)),
			new Snap.Other(1, Corner.TOP_LEFT, new Box(4, 4 + 40 + HudLayout.GAP, 100, 30)),
			new Snap.Other(2, Corner.TOP_RIGHT, new Box(W - 4 - 120, 4, 120, 80)));

	@Test void droppedJustUnderTheTopLeftStackRejoinsIt() {
		int slotY = 4 + 40 + HudLayout.GAP + 30 + HudLayout.GAP;
		Optional<Snap.Target> t = Snap.find(3, new Box(9, slotY + 7, 90, 50), OTHERS, Map.of(), W, H, OFFSETS);
		assertTrue(t.isPresent());
		assertEquals(Corner.TOP_LEFT, t.get().corner());
		assertEquals(4, t.get().offX());
		assertEquals(4, t.get().offY());
		assertEquals(new Box(4, slotY, 90, 50), t.get().box());
	}

	@Test void droppedWellAwayFromEveryStackStaysCustom() {
		assertTrue(Snap.find(3, new Box(150, 120, 90, 50), OTHERS, Map.of(), W, H, OFFSETS).isEmpty());
		// Just outside the range below the top-left stack.
		int slotY = 4 + 40 + HudLayout.GAP + 30 + HudLayout.GAP;
		assertTrue(Snap.find(3, new Box(4 + Snap.RANGE, slotY + 1, 90, 50), OTHERS, Map.of(), W, H, OFFSETS).isEmpty());
	}

	@Test void theSlotIsBelowOnlyThePanelsRegisteredBeforeIt() {
		// Source 1 (Jobs) dropped at the top left goes between Status (0) and where it would push Focus (3): its slot
		// is right under Status, not under the whole stack.
		List<Snap.Other> others = List.of(OTHERS.get(0), OTHERS.get(2), new Snap.Other(3, Corner.TOP_LEFT, new Box(4, 77, 100, 50)));
		Optional<Snap.Target> t = Snap.find(1, new Box(6, 4 + 40 + HudLayout.GAP + 2, 100, 30), others, Map.of(), W, H, OFFSETS);
		assertTrue(t.isPresent());
		assertEquals(new Box(4, 4 + 40 + HudLayout.GAP, 100, 30), t.get().box());
	}

	@Test void bottomCentreStacksUpFromTheHotbarAndTopRightClearsTheEffectIcons() {
		Optional<Snap.Target> b = Snap.find(5, new Box(150 - 3, H - 62 - 20 + 4, 100, 20), OTHERS, Map.of(), W, H, OFFSETS);
		assertTrue(b.isPresent());
		assertEquals(Corner.BOTTOM_CENTER, b.get().corner());
		assertEquals(new Box(150, H - 62 - 20, 100, 20), b.get().box());
		// With Quests (2) already top right, source 5 slots under it; the effect icons reserve the top 30 px too.
		int underQuests = 4 + 80 + HudLayout.GAP;
		Optional<Snap.Target> r = Snap.find(5, new Box(W - 4 - 60 + 2, underQuests - 3, 60, 20), OTHERS,
				Map.of(Corner.TOP_RIGHT, 30), W, H, OFFSETS);
		assertTrue(r.isPresent());
		assertEquals(Corner.TOP_RIGHT, r.get().corner());
		assertEquals(new Box(W - 4 - 60, underQuests, 60, 20), r.get().box());
	}

	@Test void customPanelsAreNotPartOfAnyStack() {
		List<Snap.Other> others = List.of(new Snap.Other(0, Corner.CUSTOM, new Box(4, 4, 100, 40)));
		Optional<Snap.Target> t = Snap.find(1, new Box(6, 6, 100, 30), others, Map.of(), W, H, OFFSETS);
		assertTrue(t.isPresent());
		assertEquals(new Box(4, 4, 100, 30), t.get().box()); // the corner itself: nothing stacked there
	}
}
