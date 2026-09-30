package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.mage.cubewheel.tracker.local.AreaBreaks.Change;
import net.mage.cubewheel.tracker.local.AreaBreaks.Result;
import org.junit.jupiter.api.Test;

class AreaBreaksTest {
	private static final int WART = 101, LOG = 202, STONE = 303, CANE = 404;

	private static Change broken(int x, int y, int z, String id, int stateId) {
		return new Change(new Pos(x, y, z), id, stateId, false, false, true, false, false);
	}

	private static Change log(int x, int y, int z) {
		return new Change(new Pos(x, y, z), "minecraft:oak_log", LOG, false, false, true, true, false);
	}

	@Test void harvester3x3WartCountsNine() {
		AreaBreaks a = new AreaBreaks();
		PlacedBlocks placed = new PlacedBlocks(64);
		a.attack(new Pos(10, 64, 10), false, 100); // no own break ever arrives: the server breaks all nine
		int counted = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (a.onChange(broken(10 + dx, 64, 10 + dz, "minecraft:warped_wart_block", WART), 102, placed) == Result.COUNT) counted++;
			}
		}
		assertEquals(9, counted);
		// the same updates again (resent section) never count twice
		assertEquals(Result.DUPLICATE, a.onChange(broken(10, 64, 10, "minecraft:warped_wart_block", WART), 103, placed));
	}

	@Test void ownBrokenBlockIsNotCountedAgain() {
		AreaBreaks a = new AreaBreaks();
		a.ownBreak(new Pos(0, 64, 0), false, 50);
		assertEquals(Result.DUPLICATE, a.onChange(broken(0, 64, 0, "minecraft:stone", STONE), 51, null));
		assertEquals(Result.COUNT, a.onChange(broken(1, 64, 0, "minecraft:stone", STONE), 51, null));
	}

	@Test void treeFellerLogColumnCountedOnceEach() {
		AreaBreaks a = new AreaBreaks();
		a.ability(true, new Pos(0, 64, 0), 10);
		a.ownBreak(new Pos(0, 64, 0), true, 12); // the log you broke: already counted by the own-break path
		int counted = 0;
		for (int y = 65; y < 90; y++) {
			if (a.onChange(log(0, y, 0), 14, null) == Result.COUNT) counted++;
		}
		for (int y = 65; y < 90; y++) { // the server resends the column
			assertEquals(Result.DUPLICATE, a.onChange(log(0, y, 0), 15, null));
		}
		assertEquals(25, counted);
		// Tree Feller windows count logs only: leaves around the column do not
		assertEquals(Result.NOT_LOG, a.onChange(broken(1, 80, 0, "minecraft:oak_leaves", 7), 15, null));
		// beyond the column's height
		assertEquals(Result.OUT_OF_RANGE, a.onChange(log(0, 64 + AreaBreaks.TREE_UP + 1, 0), 15, null));
		// the Tree Feller window lasts longer than an ordinary one
		assertEquals(Result.COUNT, a.onChange(log(1, 70, 1), 12 + AreaBreaks.TREE_TICKS, null));
	}

	@Test void treeFellerWornOffOpensOrdinaryWindows() {
		AreaBreaks a = new AreaBreaks();
		a.ability(true, null, 0);
		assertTrue(a.treeFeller(5));
		a.abilityEnded(true);
		assertFalse(a.treeFeller(5));
		a.ownBreak(new Pos(0, 64, 0), true, 5);
		assertEquals(Result.OUT_OF_RANGE, a.onChange(log(0, 64 + 10, 0), 6, null)); // radius 4, not a column
		assertEquals(Optional.of(true), AreaBreaks.wornOff("§a**Tree Feller has worn off**"));
		assertEquals(Optional.of(false), AreaBreaks.wornOff("Super Breaker has worn off"));
		assertEquals(Optional.empty(), AreaBreaks.wornOff("TREE FELLER ACTIVATED"));
	}

	@Test void otherPlayersBreaksOutsideTheWindowAreNotCounted() {
		AreaBreaks a = new AreaBreaks();
		// nothing armed: someone else's break next to you
		assertEquals(Result.NOT_ARMED, a.onChange(broken(1, 64, 0, "minecraft:stone", STONE), 10, null));
		a.ownBreak(new Pos(0, 64, 0), false, 10);
		// too far away while armed
		assertEquals(Result.OUT_OF_RANGE, a.onChange(broken(AreaBreaks.RADIUS + 1, 64, 0, "minecraft:stone", STONE), 11, null));
		// after the window closed
		assertFalse(a.armed(10 + AreaBreaks.WINDOW_TICKS + 1));
		assertEquals(Result.NOT_ARMED, a.onChange(broken(1, 64, 0, "minecraft:stone", STONE), 10 + AreaBreaks.WINDOW_TICKS + 1, null));
	}

	@Test void furtherBreaksRefreshTheWindow() {
		AreaBreaks a = new AreaBreaks();
		a.attack(new Pos(0, 64, 0), false, 0);
		a.attack(new Pos(0, 64, 0), false, 15);
		assertEquals(Result.COUNT, a.onChange(broken(1, 64, 0, "minecraft:stone", STONE), 30, null));
	}

	@Test void blockToBlockAndFluidChangesAreIgnored() {
		AreaBreaks a = new AreaBreaks();
		a.ownBreak(new Pos(0, 64, 0), false, 0);
		// a crop replanted (mature -> age 0), stone -> cobblestone, and so on
		assertEquals(Result.NOT_BREAK, a.onChange(new Change(new Pos(1, 64, 0), "minecraft:wheat", 1, false, false, false, false, true), 1, null));
		// water flowing away
		assertEquals(Result.NOT_BREAK, a.onChange(new Change(new Pos(1, 64, 1), "minecraft:water", 2, false, true, true, false, false), 1, null));
		// air -> air (a resent update)
		assertEquals(Result.NOT_BREAK, a.onChange(new Change(new Pos(1, 64, 2), "minecraft:air", 0, true, false, true, false, false), 1, null));
		// a block broken into water (waterlogged): new state is not air
		assertEquals(Result.NOT_BREAK, a.onChange(new Change(new Pos(2, 64, 0), "minecraft:stone", STONE, false, false, false, false, false), 1, null));
	}

	@Test void placedBlocksAreIgnored() {
		AreaBreaks a = new AreaBreaks();
		PlacedBlocks placed = new PlacedBlocks(64);
		placed.placed(new Pos(1, 64, 0), STONE);
		a.ownBreak(new Pos(0, 64, 0), false, 0);
		assertEquals(Result.PLACED, a.onChange(broken(1, 64, 0, "minecraft:stone", STONE), 1, placed));
		assertEquals(Result.COUNT, a.onChange(broken(2, 64, 0, "minecraft:stone", STONE), 1, placed));
	}

	@Test void capIsRespected() {
		AreaBreaks a = new AreaBreaks();
		a.attack(new Pos(0, 64, 0), false, 0);
		int counted = 0, capped = 0;
		for (int x = -4; x <= 4; x++) {
			for (int y = 60; y <= 68; y++) {
				for (int z = -4; z <= 4; z++) {
					Result r = a.onChange(broken(x, y, z, "minecraft:stone", STONE), 1, null);
					if (r == Result.COUNT) counted++;
					if (r == Result.CAP) capped++;
				}
			}
		}
		assertEquals(AreaBreaks.CAP, counted);
		assertEquals(9 * 9 * 9 - AreaBreaks.CAP, capped);
	}

	@Test void treeFellerCapIsRespected() {
		AreaBreaks a = new AreaBreaks();
		a.ability(true, new Pos(0, 64, 0), 0);
		int counted = 0;
		for (int x = -8; x <= 8; x++) {
			for (int z = -8; z <= 8; z++) {
				if (a.onChange(log(x, 70, z), 1, null) == Result.COUNT) counted++;
			}
		}
		assertEquals(AreaBreaks.TREE_CAP, counted);
	}

	@Test void plantsPoppingOffABrokenBlockAreNotCounted() {
		AreaBreaks a = new AreaBreaks();
		a.ownBreak(new Pos(0, 64, 0), false, 0); // the bottom sugar cane
		Change cane1 = new Change(new Pos(0, 65, 0), "minecraft:sugar_cane", CANE, false, false, true, false, true);
		Change cane2 = new Change(new Pos(0, 66, 0), "minecraft:sugar_cane", CANE, false, false, true, false, true);
		assertEquals(Result.CASCADE, a.onChange(cane1, 1, null));
		assertEquals(Result.CASCADE, a.onChange(cane2, 2, null)); // chains up the column
		// a plant next to (not on) the broken block, broken by the tool, still counts
		assertEquals(Result.COUNT, a.onChange(new Change(new Pos(1, 64, 0), "minecraft:wheat", 9, false, false, true, false, true), 2, null));
	}

	@Test void windowsExpireAndClear() {
		AreaBreaks a = new AreaBreaks();
		a.attack(new Pos(0, 64, 0), false, 0);
		assertTrue(a.armed(AreaBreaks.WINDOW_TICKS));
		a.expire(AreaBreaks.WINDOW_TICKS + 1);
		assertFalse(a.armed(AreaBreaks.WINDOW_TICKS + 1));
		a.attack(new Pos(0, 64, 0), false, 100);
		a.clear();
		assertFalse(a.armed(100));
	}

	@Test void summaryIsRateLimitedAndNamesBlockIds() {
		AreaBreaks a = new AreaBreaks();
		assertEquals(Optional.empty(), a.summary(0)); // nothing happened
		a.attack(new Pos(0, 64, 0), false, 0);
		a.onChange(broken(1, 64, 0, "minecraft:warped_wart_block", WART), 1, null);
		a.onChange(broken(9, 64, 0, "minecraft:stone", STONE), 1, null);
		a.unmatched("minecraft:warped_wart_block");
		Optional<String> s = a.summary(1_000);
		assertTrue(s.isPresent());
		assertEquals("triggers attack=1; counted minecraft:warped_wart_block=1; no rule minecraft:warped_wart_block=1;"
				+ " skipped out_of_range=1", s.get());
		a.onChange(broken(2, 64, 0, "minecraft:warped_wart_block", WART), 2, null);
		assertEquals(Optional.empty(), a.summary(1_000 + AreaBreaks.SUMMARY_INTERVAL_MS - 1));
		assertTrue(a.summary(1_000 + AreaBreaks.SUMMARY_INTERVAL_MS).isPresent());
	}
}
