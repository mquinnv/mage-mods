package com.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Lore captured on ManaCube Survival 2026-09-30 (blank lines kept as in the menus). */
class ObjectiveExtractorTest {
	static final List<String> RANK_6 = List.of("", "OBJECTIVE", "Slay 1,000 Monsters [4,377/1,000]", "",
			"Slay monsters in one of the", "special worlds (/worlds)", "", "➟ Click to rankup");
	static final List<String> HAVEN_HARVESTER = List.of("Intermediate Quest", "", "→ Harvest or Mine  10,000 Wolfhaven Resources",
			"", "Progress: 64%", "", "Rewards:", "● 16 Adamant Ingots");
	static final List<String> DISCOVERER = List.of("Beginner Quest", "", "100% → Discover Wolfhaven", "100% → Discover Huey",
			"100% → Discover Kilton", "100% → Discover The Tutorial Area", "", "Progress: 100%", "", "Rewards:", "● $10,000",
			"", "QUEST COMPLETED");

	@Test void prestigeObjectiveLineWithoutCounterAndSpecialFlag() {
		ObjectiveInfo info = ObjectiveExtractor.extract("Rank [✪6]", RANK_6);
		assertEquals(List.of(new ObjectiveInfo.Sub("Slay 1,000 Monsters", null)), info.subs());
		assertTrue(info.special());
		assertFalse(info.handIn());
	}

	@Test void questArrowLine() {
		ObjectiveInfo info = ObjectiveExtractor.extract("Haven Harvester", HAVEN_HARVESTER);
		assertEquals(List.of(new ObjectiveInfo.Sub("Harvest or Mine  10,000 Wolfhaven Resources", null)), info.subs());
		assertFalse(info.special());
	}

	@Test void multiObjectiveQuestKeepsPerSubPercentages() {
		ObjectiveInfo info = ObjectiveExtractor.extract("Discoverer", DISCOVERER);
		assertEquals(4, info.subs().size());
		assertTrue(info.subs().stream().allMatch(s -> s.percent() != null && s.percent() == 100.0));
		assertEquals("Discover Wolfhaven", info.subs().get(0).text());
	}

	@Test void prestigeActionArrowIsNotAnObjective() {
		ObjectiveInfo info = ObjectiveExtractor.extract("Rank [✪4]",
				List.of("", "➟ Click to rankup", "➟ Read the next rankup objective"));
		assertTrue(info.subs().isEmpty());
	}

	@Test void unknownMenuUsesVerbLinesAndFlagsHandIn() {
		ObjectiveInfo info = ObjectiveExtractor.extract("Wheat Farmer", List.of("Harvest and hand in 400 Wheat", "Progress: 40/400"));
		assertEquals(List.of(new ObjectiveInfo.Sub("Harvest and hand in 400 Wheat", null)), info.subs());
		assertTrue(info.handIn());
		ObjectiveInfo byName = ObjectiveExtractor.extract("Kill 350 Mobs", List.of("Progress: 12/350"));
		assertEquals(List.of(new ObjectiveInfo.Sub("Kill 350 Mobs", null)), byName.subs());
	}

	@Test void formattingCodesAndNullsAreTolerated() {
		ObjectiveInfo info = ObjectiveExtractor.extract(null, java.util.Arrays.asList(null, "§a→ §fMine 300 Stone"));
		assertEquals(List.of(new ObjectiveInfo.Sub("Mine 300 Stone", null)), info.subs());
		assertTrue(ObjectiveExtractor.extract("x", null).subs().isEmpty());
	}
}
