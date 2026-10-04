package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoticesTest {
	private final List<String> fired = new ArrayList<>();
	private final List<Integer> colours = new ArrayList<>();
	private final Notices n = new Notices((text, color, now) -> {
		fired.add(text);
		colours.add(color);
	});
	/** Past the settle time after the reset at 0. */
	private static final long T = Notices.SETTLE_MS + 1_000;

	@Test void inventoryFullFiresOnceWhenTheLastSlotFillsAndReArmsWhenOneFrees() {
		n.reset(0);
		n.inventory(34, 36, T);
		n.inventory(35, 36, T + 50);
		n.inventory(36, 36, T + 100);
		n.inventory(36, 36, T + 150);
		assertEquals(List.of("Inventory full"), fired);
		assertEquals(Notices.RED, colours.get(0));
		n.inventory(35, 36, T + 200);
		n.inventory(36, 36, T + 250);
		assertEquals(List.of("Inventory full", "Inventory full"), fired);
	}

	@Test void inventoryAlreadyFullOnJoinIsSilent() {
		n.reset(0);
		n.inventory(36, 36, 0);
		n.inventory(36, 36, T);
		assertEquals(List.of(), fired);
	}

	@Test void changesWhileSettlingAfterAJoinAreSilent() {
		n.reset(0);
		n.inventory(0, 36, 0);      // the inventory arrives empty, then fills in
		n.inventory(36, 36, 500);
		n.armor("No armor", 0, 0, 0);
		n.armor("Phoenix", 4, 4, 600);
		n.vault(2, 45, 0);
		n.vault(2, 0, 700);
		assertEquals(List.of(), fired);
		n.inventory(35, 36, T);
		n.inventory(36, 36, T + 50);
		assertEquals(List.of("Inventory full"), fired);
	}

	@Test void aResetStartsAFreshBaseline() {
		n.reset(0);
		n.inventory(10, 36, T);
		n.reset(T + 100);           // another world: the inventory reads full there at first
		n.inventory(36, 36, T + 200);
		assertEquals(List.of(), fired);
	}

	@Test void setBonusLostAndRegained() {
		n.reset(0);
		n.armor("Phoenix", 4, 4, T);
		n.armor("Phoenix", 3, 4, T + 50);
		n.armor("Phoenix", 2, 4, T + 100);
		assertEquals(List.of("Phoenix set bonus lost (3/4)"), fired);
		assertEquals(Panel.YELLOW, colours.get(0));
		n.armor("Phoenix", 3, 4, T + 150);
		n.armor("Phoenix", 4, 4, T + 200);
		n.armor("Phoenix", 4, 4, T + 250);
		assertEquals(List.of("Phoenix set bonus lost (3/4)", "Phoenix set bonus active"), fired);
		assertEquals(Panel.GREEN, colours.get(1));
	}

	@Test void armorUnmetOnJoinIsTheBaseline() {
		n.reset(0);
		n.armor("Phoenix", 3, 4, T);
		n.armor("Phoenix", 2, 4, T + 50);
		assertEquals(List.of(), fired);
		n.armor("Phoenix", 4, 4, T + 100);
		assertEquals(List.of("Phoenix set bonus active"), fired);
	}

	@Test void setsWithoutAStatedBonusNeverFire() {
		n.reset(0);
		n.armor("Diamond", 4, 0, T);
		n.armor("Diamond", 3, 0, T + 50);
		n.armor("Diamond", 4, 0, T + 100);
		assertEquals(List.of(), fired);
	}

	@Test void takingTheSetOffOrSwappingIt() {
		n.reset(0);
		n.armor("Phoenix", 4, 4, T);
		n.armor("No armor", 0, 0, T + 50);
		assertEquals(List.of("Phoenix set bonus lost"), fired);
		n.armor("Snowy", 4, 4, T + 100);
		assertEquals(List.of("Phoenix set bonus lost", "Snowy set bonus active"), fired);
		n.armor("Warden", 4, 2, T + 150);
		assertEquals(List.of("Phoenix set bonus lost", "Snowy set bonus active", "Snowy set bonus lost",
				"Warden set bonus active"), fired);
	}

	@Test void vaultNearlyFullThenFull() {
		n.reset(0);
		n.vault(2, 10, T);                 // first sight: the baseline
		n.vault(2, 3, T + 50);
		n.vault(2, 2, T + 100);
		n.vault(2, 0, T + 150);
		n.vault(2, 0, T + 200);
		assertEquals(List.of("PV 2: 3 slots left", "PV 2 full"), fired);
		assertEquals(Panel.YELLOW, colours.get(0));
		assertEquals(Notices.RED, colours.get(1));
	}

	@Test void vaultReArmsAboveEachThreshold() {
		n.reset(0);
		n.vault(1, 10, T);
		n.vault(1, 0, T + 50);             // straight to full: only "full"
		n.vault(1, 2, T + 100);            // re-arms "full", still low
		n.vault(1, 0, T + 150);
		n.vault(1, 9, T + 200);            // re-arms both
		n.vault(1, 1, T + 250);
		assertEquals(List.of("PV 1 full", "PV 1 full", "PV 1: 1 slot left"), fired);
	}

	@Test void vaultsAreTrackedApart() {
		n.reset(0);
		n.vault(1, 0, T);
		n.vault(2, 20, T);
		n.vault(2, 3, T + 50);
		n.vault(1, 0, T + 100);
		assertEquals(List.of("PV 2: 3 slots left"), fired);
	}
}
