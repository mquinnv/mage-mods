package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.mage.cubewheel.tracker.ProgressExtractor.Progress;
import net.mage.cubewheel.tracker.Trackable;
import net.mage.cubewheel.tracker.TrackerPanelModel;
import net.mage.cubewheel.tracker.TrackerRow;
import net.mage.cubewheel.tracker.TrackerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Finishing a quest prints (real game log, 2026-10-03): "Congratulations! You have completed the Volcano Potion
 * Quest!" to you, then the broadcast "㘓 Qualan Finished the Volcano Potion Quest!" to everyone. The first marks the
 * "Complete the Volcano Potion Quest" objective of a party quest done until the next menu read.
 */
class QuestCompletedTest {
	@TempDir Path dir;

	static final String MOB_EXPERIENCE = Trackable.idOf("pquests", "Mob Experience");

	// ---- the chat line ----

	@Test void theVerbatimLineNamesTheQuest() {
		assertEquals(Optional.of("Volcano Potion Quest"),
				QuestCompleted.parse("Congratulations! You have completed the Volcano Potion Quest!"));
	}

	@Test void formattingGlyphsCaseAndAMissingBangAreTolerated() {
		assertEquals(Optional.of("Volcano Potion Quest"),
				QuestCompleted.parse("§a§lCongratulations! §r§eYou have completed the §6Volcano Potion Quest§e!"));
		assertEquals(Optional.of("Volcano Potion Quest"), QuestCompleted.parse("㘓 you have completed the Volcano Potion Quest"));
		assertEquals(Optional.of("Volcano Potion Quest"), QuestCompleted.parse("  You have completed the  Volcano Potion Quest !  "));
	}

	@Test void theBroadcastAndOtherChatAreIgnored() {
		assertEquals(Optional.empty(), QuestCompleted.parse("㘓 Qualan Finished the Volcano Potion Quest!"));
		assertEquals(Optional.empty(), QuestCompleted.parse("You caught a 52.2cm Common Flounder"));
		assertEquals(Optional.empty(), QuestCompleted.parse("§r[VIP] Bob: You have completed the Volcano Potion Quest!"));
		assertEquals(Optional.empty(), QuestCompleted.parse("You have completed the !"));
		assertEquals(Optional.empty(), QuestCompleted.parse(""));
		assertEquals(Optional.empty(), QuestCompleted.parse(null));
	}

	// ---- matching an objective ----

	@Test void onlyTheCompleteTheObjectiveOfThatQuestMatches() {
		assertTrue(QuestCompleted.matches("Complete the Volcano Potion Quest", "Volcano Potion Quest"));
		assertTrue(QuestCompleted.matches("  complete the volcano potion quest! ", "Volcano Potion Quest"));
		assertTrue(QuestCompleted.matches(" Complete the Volcano Potion Quest.", "Volcano Potion Quest"));
		assertTrue(QuestCompleted.matches("§eComplete the §6Volcano Potion Quest", "Volcano Potion Quest"));
		assertFalse(QuestCompleted.matches("Complete the Volcano Potion", "Volcano Potion Quest"));
		assertFalse(QuestCompleted.matches("Participate in Slaying 5 Lava Beasts", "Volcano Potion Quest"));
		assertFalse(QuestCompleted.matches("Complete the Volcano Potion Quest", "Volcano Potion"));
		assertFalse(QuestCompleted.matches(null, "Volcano Potion Quest"));
		assertFalse(QuestCompleted.matches("Complete the Volcano Potion Quest", null));
	}

	// ---- applying it to the store ----

	private TrackerStore mobExperience(double volcanoPercent) {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("pquests", "Mob Experience", new Progress(20, 100), 0);
		s.setObjective(MOB_EXPERIENCE, new ObjectiveInfo(List.of(
				new ObjectiveInfo.Sub("Complete the Volcano Potion Quest", volcanoPercent),
				new ObjectiveInfo.Sub("Participate in Slaying 5 Lava Beasts", 40.0)), false, false));
		return s;
	}

	private static List<TrackerPanelModel.Line> hud(TrackerStore s) {
		return TrackerPanelModel.build(
				List.of(new TrackerStore.HudSection(TrackerStore.HudSection.Kind.ANYWHERE, s.rows(true))),
				k -> s.objective(k).orElse(null), 0.9, List.of(), 2_000, null, s::counted);
	}

	@Test void completingTheQuestMarksItsObjectiveDoneOnTheHud() {
		TrackerStore s = mobExperience(0.0);
		assertEquals("0/1", hud(s).get(1).right());
		List<LocalCounter.Contribution> added = LocalCounter.questCompleted("Volcano Potion Quest", s, 1_000);
		assertEquals(List.of(new LocalCounter.Contribution(TrackerStore.subKey(MOB_EXPERIENCE, 0), 1)), added);
		List<TrackerPanelModel.Line> lines = hud(s);
		assertEquals("~1/1", lines.get(1).right());
		assertEquals(1.0, lines.get(1).progress());
		assertEquals("2/5", lines.get(2).right()); // the other objective is untouched
		// Seen twice (both chat lines, or a repeat): still one step.
		assertEquals(List.of(), LocalCounter.questCompleted("Volcano Potion Quest", s, 1_500));
		assertEquals(1, s.counted(TrackerStore.subKey(MOB_EXPERIENCE, 0)));
		// The next /pquests read is the truth again.
		s.setObjective(MOB_EXPERIENCE, s.objective(MOB_EXPERIENCE).orElseThrow());
		assertEquals(0, s.counted(TrackerStore.subKey(MOB_EXPERIENCE, 0)));
	}

	@Test void anObjectiveTheMenuAlreadyShowsDoneIsLeftAlone() {
		TrackerStore s = mobExperience(100.0);
		assertEquals(List.of(), LocalCounter.questCompleted("Volcano Potion Quest", s, 1_000));
	}

	@Test void aQuestMatchingNothingChangesNothing() {
		TrackerStore s = mobExperience(0.0);
		assertEquals(List.of(), LocalCounter.questCompleted("Frozen Potion Quest", s, 1_000));
		assertEquals(0, s.counted(TrackerStore.subKey(MOB_EXPERIENCE, 0)));
		assertEquals(0, s.counted(TrackerStore.subKey(MOB_EXPERIENCE, 1)));
		assertEquals(List.of(), LocalCounter.questCompleted(null, s, 1_000));
		assertEquals("0/1", hud(s).get(1).right());
	}

	@Test void aSingleObjectiveEntryIsEstimatedAtItsTarget() {
		TrackerStore s = new TrackerStore(dir.resolve("single.json"));
		String id = Trackable.idOf("quests", "Brewer");
		s.update("quests", "Brewer", new Progress(0, 100), 0);
		s.setObjective(id, new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Complete the Volcano Potion Quest", null)), false, false));
		assertEquals(List.of(new LocalCounter.Contribution(id, 100)), LocalCounter.questCompleted("Volcano Potion Quest", s, 1_000));
		TrackerRow row = s.rows(true).get(0);
		assertTrue(row.atCap(), "shown as done (✓?) until a menu read confirms it");
		assertFalse(row.complete());
		assertEquals(List.of(), LocalCounter.questCompleted("Volcano Potion Quest", s, 1_500));
	}
}
