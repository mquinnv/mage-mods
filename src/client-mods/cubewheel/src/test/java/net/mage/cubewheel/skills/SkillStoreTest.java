package net.mage.cubewheel.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Per-account skill state: bar deltas (captured bars 2026-10), menu caps, level-ups, JSON round trip. */
class SkillStoreTest {
	@TempDir Path dir;

	private SkillStore store() {
		return new SkillStore(dir.resolve("cubewheel-skills.json"));
	}

	private static SkillBarParser.Reading mining(int level, long xp, long needed) {
		return new SkillBarParser.Reading(Skill.MINING, level, xp, needed);
	}

	@Test void firstReadingGainsNothingAndStampsTimes() {
		SkillStore s = store();
		assertEquals(0, s.bar("Qualan", mining(243, 27596, 62250), 1000));
		SkillStore.State st = s.account("qualan").skills().get(Skill.MINING);
		assertEquals(243, st.level());
		assertEquals(27596, st.xp());
		assertEquals(62250, st.needed());
		assertEquals(1000, st.xpAt());
		assertEquals(1000, st.levelAt());
		assertEquals(Skill.MINING.defaultCap, st.cap());
	}

	@Test void sameLevelGainIsTheDifference() {
		SkillStore s = store();
		s.bar("q", mining(243, 27596, 62250), 1000);
		assertEquals(150, s.bar("q", mining(243, 27746, 62250), 2000));
		SkillStore.State st = s.account("q").skills().get(Skill.MINING);
		assertEquals(2000, st.xpAt());
		assertEquals(1000, st.levelAt());
	}

	@Test void rollOverGainCountsTheRestOfTheOldLevel() {
		SkillStore s = store();
		s.bar("q", mining(243, 61000, 62250), 1000);
		assertEquals(2450, s.bar("q", mining(244, 1200, 62500), 2000));
		assertEquals(2000, s.account("q").skills().get(Skill.MINING).levelAt());
	}

	@Test void lowerReadingIsAResetAndGainsNothing() {
		SkillStore s = store();
		s.bar("q", mining(243, 5000, 62250), 1000);
		assertEquals(0, s.bar("q", mining(243, 100, 62250), 2000));
		assertEquals(0, s.bar("q", mining(200, 100, 51500), 3000));
	}

	@Test void gainAfterUnknownXpIsZero() {
		SkillStore s = store();
		s.levelUp("q", Skill.MINING, 244, 1000);
		assertEquals(0, s.bar("q", mining(244, 300, 62500), 2000));
	}

	@Test void levelUpMarksXpUnknownAndUsesCurve() {
		SkillStore s = store();
		s.bar("q", mining(243, 61000, 62250), 1000);
		s.levelUp("q", Skill.MINING, 244, 2000);
		SkillStore.State st = s.account("q").skills().get(Skill.MINING);
		assertEquals(244, st.level());
		assertEquals(-1, st.xp());
		assertEquals(XpCurve.needed(244), st.needed());
		assertEquals(2000, st.levelAt());
	}

	@Test void levelUpNeverLowersALevel() {
		SkillStore s = store();
		s.bar("q", mining(243, 61000, 62250), 1000);
		s.levelUp("q", Skill.MINING, 200, 2000);
		SkillStore.State st = s.account("q").skills().get(Skill.MINING);
		assertEquals(243, st.level());
		assertEquals(61000, st.xp());
		assertEquals(1000, st.levelAt());
	}

	@Test void menuFillsMissingLevelAndAlwaysSetsCap() {
		SkillStore s = store();
		s.bar("q", mining(243, 100, 62250), 1000);
		s.menu("q", List.of(
				new SkillsMenu.Entry(Skill.MINING, 200, 2500, false),
				new SkillsMenu.Entry(Skill.ALCHEMY, 7, 500, false)), 5000);
		Map<Skill, SkillStore.State> m = s.account("q").skills();
		assertEquals(243, m.get(Skill.MINING).level());
		assertEquals(2500, m.get(Skill.MINING).cap());
		assertEquals(7, m.get(Skill.ALCHEMY).level());
		assertEquals(-1, m.get(Skill.ALCHEMY).xp());
		assertEquals(500, m.get(Skill.ALCHEMY).cap());
		assertEquals(5000, s.account("q").menuAt());
	}

	@Test void menuRaisesALowerLevel() {
		SkillStore s = store();
		s.bar("q", mining(243, 100, 62250), 1000);
		s.menu("q", List.of(new SkillsMenu.Entry(Skill.MINING, 250, 2500, false)), 5000);
		assertEquals(250, s.account("q").skills().get(Skill.MINING).level());
	}

	@Test void powerIsRecorded() {
		SkillStore s = store();
		s.power("q", 3239, 1234);
		assertEquals(3239, s.account("q").power());
		assertEquals(1234, s.account("q").powerAt());
	}

	@Test void accountKeysAreCaseInsensitive() {
		SkillStore s = store();
		assertSame(s.account("Qualan"), s.account("qualan"));
	}

	@Test void recentGainsHonoursTheWindow() {
		SkillStore s = store();
		s.bar("q", mining(243, 1000, 62250), 1000);
		s.bar("q", mining(243, 1100, 62250), 2000);
		s.bar("q", mining(243, 1400, 62250), 9000);
		assertEquals(Map.of(Skill.MINING, 300L), s.recentGains("q", 10000, 5000));
		assertEquals(Map.of(Skill.MINING, 400L), s.recentGains("q", 10000, 20000));
		assertTrue(s.recentGains("q", 100000, 5000).isEmpty());
	}

	@Test void recentGainsKeepAtMost64Entries() {
		SkillStore s = store();
		long xp = 0;
		s.bar("q", mining(243, xp, 62250), 0);
		for (int i = 1; i <= 100; i++) s.bar("q", mining(243, xp += 10, 62250), i);
		assertEquals(640L, s.recentGains("q", 100, 1000).get(Skill.MINING));
	}

	@Test void dirtyTracksMutationsAndClears() {
		SkillStore s = store();
		assertFalse(s.dirty());
		s.power("q", 1, 1);
		assertTrue(s.dirty());
		s.clearDirty();
		assertFalse(s.dirty());
	}

	@Test void roundTripReproducesEveryField() throws Exception {
		SkillStore a = store();
		a.bar("Qualan", mining(617, 65682, 155750), 1791938466000L);
		a.levelUp("Qualan", Skill.ALCHEMY, 7, 1790863185204L);
		a.menu("Qualan", List.of(new SkillsMenu.Entry(Skill.MINING, 600, 2500, false),
				new SkillsMenu.Entry(Skill.ALCHEMY, 7, 500, false)), 1790863185204L);
		a.power("Qualan", 3239, 1791938500000L);
		a.save();
		String json = Files.readString(dir.resolve("cubewheel-skills.json"));
		assertTrue(json.contains("\"qualan\""));
		assertTrue(json.contains("\"mining\""));

		SkillStore b = store();
		assertTrue(b.load());
		SkillStore.Account x = a.account("qualan");
		SkillStore.Account y = b.account("qualan");
		assertEquals(x.power(), y.power());
		assertEquals(x.powerAt(), y.powerAt());
		assertEquals(x.menuAt(), y.menuAt());
		assertEquals(x.skills(), y.skills());
		assertEquals(2, y.skills().size());
		assertFalse(b.dirty());
	}

	@Test void unknownSkillKeysAreSkipped() throws Exception {
		Files.writeString(dir.resolve("cubewheel-skills.json"),
				"{\"q\":{\"power\":5,\"skills\":{\"nonsense\":{\"level\":3},\"mining\":{\"level\":9}}}}");
		SkillStore s = store();
		assertTrue(s.load());
		assertEquals(Map.of(Skill.MINING, new SkillStore.State(9, 0, 0, 0, 0, 0)), s.account("q").skills());
		assertEquals(5, s.account("q").power());
	}

	@Test void missingFileLoadsAsNoOp() {
		SkillStore s = store();
		assertTrue(s.load());
		assertTrue(s.account("q").skills().isEmpty());
	}

	@Test void corruptFileLoadsEmptyAndReportsFalse() throws Exception {
		Files.writeString(dir.resolve("cubewheel-skills.json"), "{not json");
		SkillStore s = store();
		assertFalse(s.load());
		assertTrue(s.account("q").skills().isEmpty());
	}
}
