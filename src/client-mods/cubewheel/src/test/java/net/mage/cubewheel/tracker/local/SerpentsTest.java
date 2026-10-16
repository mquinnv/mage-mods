package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.ProgressExtractor.Progress;
import net.mage.cubewheel.tracker.TrackerStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * "Slay 78 Serpents in Sandara" (job, 2026-10-15): no mob is named "Serpent"; Sandara's rattlesnakes and vipers
 * both count.
 */
class SerpentsTest {
	@TempDir Path dir;
	static final List<String> WORLDS = KillRuleTest.WORLDS;
	static final WorldInfo SANDARA = SandaraKillTest.SANDARA;
	static final String SERPENTS = "jobs:Hunting Experienced · Slay Serpents in Sandara";

	static CounterRule rule() {
		return ObjectiveParser.parse(SandaraKillTest.obj("Slay 3/78 Serpents in Sandara", false), WORLDS).orElseThrow();
	}

	static Signal.MobKilled kill(String name, WorldInfo at) {
		return new Signal.MobKilled("", name, Set.of("mob", "monster"), 1, at);
	}

	@Test void parsesToTheSerpentFamilyInSandara() {
		assertEquals(new CounterRule(CounterRule.Kind.KILL, 78, new CounterRule.Named("serpent"),
				new CounterRule.NamedWorld("sandara")), rule());
	}

	@Test void vipersAndRattlesnakesCountOtherSandaraMobsDoNot() {
		CounterRule r = rule();
		assertTrue(RuleMatcher.matches(r, kill("Viper 75⺛", SANDARA)));
		assertTrue(RuleMatcher.matches(r, kill("Rattle Snake 30⺛", SANDARA)));
		assertTrue(RuleMatcher.matches(r, kill("Rattlesnake", SANDARA)));
		assertFalse(RuleMatcher.matches(r, kill("Scorpion 40⺛", SANDARA)));
		assertFalse(RuleMatcher.matches(r, kill("Viper", new WorldInfo(Set.of("tangleroot"), true, true))));
	}

	@Test void rattlesnakeLootCreditsBothTheSerpentAndTheRattleSnakeJob() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Hunting Experienced · Slay Serpents in Sandara", new Progress(3, 78), 0);
		s.setObjective(SERPENTS, SandaraKillTest.obj("Slay 3/78 Serpents in Sandara", false));
		s.update("jobs", "Hunting Experienced · Slay Rattle Snakes in Sandara", new Progress(29, 58), 0);
		s.setObjective(SandaraKillTest.SNAKES, SandaraKillTest.obj("Slay 29/58 Rattle Snakes in Sandara", false));
		LootMatch.Credit credit = LootMatch.match(List.of("+5 Mana", "Rattlesnake Meat"), s.activeRules(WORLDS), SANDARA)
				.orElseThrow();
		assertEquals(List.of(SandaraKillTest.SNAKES, SERPENTS), credit.ruleIds()); // sorted
	}

	@Test void aNamedKillCreditsTheSerpentJob() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", "Hunting Experienced · Slay Serpents in Sandara", new Progress(3, 78), 0);
		s.setObjective(SERPENTS, SandaraKillTest.obj("Slay 3/78 Serpents in Sandara", false));
		LocalCounter.onSignal(kill("Viper 75⺛", SANDARA), s, WORLDS, 5);
		assertEquals(1, s.counted(SERPENTS));
	}
}
