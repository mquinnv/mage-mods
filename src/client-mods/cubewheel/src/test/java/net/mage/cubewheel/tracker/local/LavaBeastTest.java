package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.mage.cubewheel.config.DefaultConfig;
import net.mage.cubewheel.tracker.ProgressExtractor.Progress;
import net.mage.cubewheel.tracker.TrackerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * "Participate in Slaying 5 Lava Beasts" (party quest, capture 2026-10-08) and "Help defeat 1 Golden Knight in
 * Tangleroots" (job): taking part in the kill counts, the last hit is not needed (Michael 2026-10-08).
 */
class LavaBeastTest {
	@TempDir Path dir;
	static final List<String> WORLDS = DefaultConfig.manaWorlds();
	static final WorldInfo BURNINGLANDS = WorldResolver.resolve("minecraft:burninglands", List.of(), WORLDS);
	static final WorldInfo TANGLEROOTS = WorldResolver.resolve("minecraft:tangleroots", List.of(), WORLDS);
	static final String BEASTS = "pquests:Mob Experience#sub1";
	static final String KNIGHT = "jobs:Hunting Beginner · Help defeat 1 Golden Knight in Tangleroots";

	static ObjectiveInfo obj(String line) {
		return new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), false, false);
	}

	@Test void participationObjectivesParseToAssistRules() {
		assertEquals(Optional.of(new CounterRule(CounterRule.Kind.ASSIST, 5, new CounterRule.Named("lava beast"), new CounterRule.AnyWorld())),
				ObjectiveParser.parse(obj("Participate in Slaying 5 Lava Beasts"), WORLDS));
		assertEquals(Optional.of(new CounterRule(CounterRule.Kind.ASSIST, 1, new CounterRule.Named("golden knight"),
				new CounterRule.NamedWorld("tangleroot"))), ObjectiveParser.parse(obj("Help defeat 1 Golden Knight in Tangleroots"), WORLDS));
		// A boss is never counted by the last hit, but taking part in slaying one is.
		assertEquals(Optional.empty(), ObjectiveParser.parse(obj("Slay 3 Jungle Bosses"), WORLDS));
		assertEquals(Optional.of(new CounterRule(CounterRule.Kind.ASSIST, 3, new CounterRule.Named("jungle boss"), new CounterRule.AnyWorld())),
				ObjectiveParser.parse(obj("Participate in Slaying 3 Jungle Bosses"), WORLDS));
	}

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("pquests", "Mob Experience", new Progress(90, 100), 0);
		s.setObjective("pquests:Mob Experience", new ObjectiveInfo(List.of(
				new ObjectiveInfo.Sub("Complete the Volcano Potion Quest", 100.0),
				new ObjectiveInfo.Sub("Participate in Slaying 5 Lava Beasts", 80.0)), false, false));
		s.update("jobs", "Hunting Beginner · Help defeat 1 Golden Knight in Tangleroots", new Progress(0, 1), 0);
		s.setObjective(KNIGHT, obj("Help defeat 1 Golden Knight in Tangleroots"));
		s.update("jobs", "Hunting Heavy · Slay Monsters in Burninglands", new Progress(0, 500), 0);
		s.setObjective("jobs:Hunting Heavy · Slay Monsters in Burninglands", obj("Slay 0/500 Monsters in Burninglands"));
		return s;
	}

	@Test void aBeastOthersFinishedCountsWhenWeHitIt() {
		Signal.MobAssisted assisted = new Signal.MobAssisted("minecraft:interaction", "Lava Beast 2000⺛", Set.of("mob", "monster"), BURNINGLANDS);
		// Only the participation objective: the "Slay Monsters" job needs the last hit.
		assertEquals(List.of(new LocalCounter.Contribution(BEASTS, 1)), LocalCounter.onSignal(assisted, store(), WORLDS, 5));
	}

	@Test void aBeastWeFinishedCountsForBothKinds() {
		Signal.MobKilled killed = new Signal.MobKilled("minecraft:interaction", "Lava Beast 2000⺛", Set.of("mob", "monster"), 1, BURNINGLANDS);
		assertEquals(List.of(new LocalCounter.Contribution(BEASTS, 1),
				new LocalCounter.Contribution("jobs:Hunting Heavy · Slay Monsters in Burninglands", 1)),
				LocalCounter.onSignal(killed, store(), WORLDS, 5));
	}

	@Test void theKnightNeedsItsWorld() {
		Signal.MobAssisted knight = new Signal.MobAssisted("minecraft:interaction", "Golden Knight", Set.of("mob", "monster"), TANGLEROOTS);
		assertEquals(List.of(new LocalCounter.Contribution(KNIGHT, 1)), LocalCounter.onSignal(knight, store(), WORLDS, 5));
		Signal.MobAssisted elsewhere = new Signal.MobAssisted("minecraft:interaction", "Golden Knight", Set.of("mob", "monster"), BURNINGLANDS);
		assertEquals(List.of(), LocalCounter.onSignal(elsewhere, store(), WORLDS, 5));
	}
}
