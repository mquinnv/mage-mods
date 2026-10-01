package net.mage.cubewheel.tracker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.tracker.ProgressExtractor.Progress;
import net.mage.cubewheel.tracker.TrackerStore;
import net.mage.cubewheel.tracker.local.CounterRule.Any;
import net.mage.cubewheel.tracker.local.CounterRule.AnyWorld;
import net.mage.cubewheel.tracker.local.CounterRule.Kind;
import net.mage.cubewheel.tracker.local.CounterRule.Named;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ManaCube Survival fishing (real capture 2026-09-30/10-01): the vanilla bobber never bites; every catch
 * prints "You caught a 52.2cm Common Flounder" in chat. Job listings name a species with an icon glyph
 * and double spaces ("Catch 1/3   Goldfish while fishing").
 */
class FishCatchTest {
	@TempDir Path dir;
	static final WorldInfo OVERWORLD = new WorldInfo(Set.of("overworld"), false, true);

	static final String GOLDFISH = "jobs:Fishing Beginner · Catch  Goldfish while fishing";
	static final String SHROOM = "jobs:Fishing Experienced · Catch  YellowSeaShroom while fishing";
	static final String OCTOPUS = "jobs:Fishing Heavy · Catch  Octopus while fishing";
	static final String RANK_8 = "prestige:Rank [✪8] · Catch 1,000 Fish";
	static final String FISHERMAN = "pquests:Fisherman";

	static Optional<CounterRule> parse(String line) {
		return ObjectiveParserTest.parse(line);
	}

	// ---- the chat line ----

	@Test void catchLines() {
		assertEquals(Optional.of(new FishCatchParser.Catch("Flounder", "Common", 52.2)),
				FishCatchParser.parse("You caught a 52.2cm Common Flounder"));
		assertEquals(Optional.of(new FishCatchParser.Catch("Salmon", "Common", 38.5)),
				FishCatchParser.parse("You caught a 38.5cm Common Salmon"));
		assertEquals(Optional.of(new FishCatchParser.Catch("Cod", "Common", 227.5)),
				FishCatchParser.parse("You caught a 227.5cm Common Cod"));
		assertEquals(Optional.of(new FishCatchParser.Catch("Nemo", "Common", 29.6)),
				FishCatchParser.parse("You caught a 29.6cm Common Nemo"));
		assertEquals(Optional.of(new FishCatchParser.Catch("BlueSeashroom", "Uncommon", 150.5)),
				FishCatchParser.parse("You caught a 150.5cm Uncommon BlueSeashroom"));
		assertEquals(Optional.of(new FishCatchParser.Catch("Pufferfish", "Common", 238.7)),
				FishCatchParser.parse("You caught a 238.7cm Common Pufferfish"));
		assertEquals(Optional.of(new FishCatchParser.Catch("DiamondAngler", "Rare", 299.1)),
				FishCatchParser.parse("You caught a 299.1cm Rare DiamondAngler"));
	}

	@Test void formattingCodesAndExtraSpacesAreTolerated() {
		assertEquals(Optional.of(new FishCatchParser.Catch("Octopus", "Rare", 12.0)),
				FishCatchParser.parse("§aYou caught a §e12cm  §9Rare §fOctopus "));
		assertEquals("Goldfish", FishCatchParser.parse("You caught an 8.1cm Epic Goldfish").orElseThrow().species());
	}

	@Test void notACatch() {
		for (String s : List.of("§r[VIP] Steve: You caught a 52.2cm Common Flounder",
				"Steve: You caught a 52.2cm Common Flounder", "<Steve> You caught a 52.2cm Common Flounder",
				"------------------\nYour task has expired.\nCatch a Crab while fishing\n------------------",
				"Your task has expired.", "Catch a Crab while fishing", "You caught a Common Flounder",
				"You caught a 52.2cm Flounder", "")) {
			assertTrue(FishCatchParser.parse(s).isEmpty(), s);
		}
		assertTrue(FishCatchParser.parse(null).isEmpty());
	}

	@Test void speciesKeysIgnoreCaseSpacingAndPlural() {
		assertEquals(FishCatchParser.speciesKey("YellowSeaShroom"), FishCatchParser.speciesKey("Yellow Sea Shroom"));
		assertEquals(FishCatchParser.speciesKey("YellowSeaShroom"), FishCatchParser.speciesKey("yellow sea shrooms"));
		assertEquals(FishCatchParser.speciesKey("Goldfish"), FishCatchParser.speciesKey("goldfish"));
		assertEquals(FishCatchParser.speciesKey("Crab"), FishCatchParser.speciesKey("Crabs"));
		assertEquals(FishCatchParser.speciesKey("Octopus"), FishCatchParser.speciesKey("octopus"));
	}

	// ---- objectives ----

	@Test void jobListingsWithGlyphsBecomeSpeciesRules() {
		assertEquals(Optional.of(new CounterRule(Kind.FISH, 3, new Named("goldfish"), new AnyWorld())),
				parse("Catch 1/3   Goldfish while fishing"));
		assertEquals(Optional.of(new CounterRule(Kind.FISH, 9, new Named("yellowseashroom"), new AnyWorld())),
				parse("Catch 0/9  YellowSeaShroom while fishing"));
		assertEquals(Kind.FISH, parse("Catch 3/22  Octopus while fishing").orElseThrow().kind());
		// The stored text as the issue quoted it (glyph already gone, spaces left).
		assertEquals(9, parse("Catch 0/9  YellowSeaShroom while fishing").orElseThrow().target());
		// "while fishing" keeps a non-fish word on the fishing path: crabs are caught on a rod.
		assertEquals(Optional.of(new CounterRule(Kind.FISH, 5, new Named("crab"), new AnyWorld())),
				parse("Catch 5 Crabs while fishing"));
	}

	@Test void catchNFishStaysAnyFish() {
		assertEquals(Optional.of(new CounterRule(Kind.FISH, 1000, new Any(), new AnyWorld())), parse("Catch 1,000 Fish"));
		assertEquals(Optional.of(new CounterRule(Kind.FISH, 25, new Any(), new AnyWorld())), parse("Catch 25 Fish"));
	}

	// ---- matching ----

	static Signal.FishCaught caught(String species) {
		return new Signal.FishCaught(species, "Common", OVERWORLD);
	}

	@Test void anyFishCountsEveryCatchNamedOnlyItsSpecies() {
		CounterRule any = parse("Catch 25 Fish").orElseThrow();
		CounterRule shroom = parse("Catch 0/9  YellowSeaShroom while fishing").orElseThrow();
		CounterRule spaced = new CounterRule(Kind.FISH, 9, new Named("yellow sea shroom"), new AnyWorld());
		assertTrue(RuleMatcher.matches(any, caught("Flounder")));
		assertTrue(RuleMatcher.matches(any, new Signal.FishCaught(OVERWORLD))); // bobber reel-in
		assertTrue(RuleMatcher.matches(shroom, caught("YellowSeaShroom")));
		assertTrue(RuleMatcher.matches(spaced, caught("YellowSeaShroom")));
		assertFalse(RuleMatcher.matches(shroom, caught("BlueSeashroom")));
		assertFalse(RuleMatcher.matches(shroom, new Signal.FishCaught(OVERWORLD)));
		assertFalse(RuleMatcher.matches(new CounterRule(Kind.KILL, 9, new Named("yellowseashroom"), new AnyWorld()),
				caught("YellowSeaShroom")));
	}

	TrackerStore store() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("jobs", GOLDFISH.substring(5), new Progress(1, 3), 0);
		s.setObjective(GOLDFISH, obj("Catch 1/3   Goldfish while fishing"));
		s.update("jobs", SHROOM.substring(5), new Progress(0, 9), 0);
		s.setObjective(SHROOM, obj("Catch 0/9  YellowSeaShroom while fishing"));
		s.update("jobs", OCTOPUS.substring(5), new Progress(3, 22), 0);
		s.setObjective(OCTOPUS, obj("Catch 3/22  Octopus while fishing"));
		s.update("pquests", "Fisherman", new Progress(3, 25), 0);
		s.setObjective(FISHERMAN, obj("Catch 25 Fish"));
		// No stored objective (read before objectives were stored): the rank's name carries it.
		s.update("prestige", "Rank [✪8] · Catch 1,000 Fish", new Progress(488, 1000), 0);
		return s;
	}

	static ObjectiveInfo obj(String line) {
		return new ObjectiveInfo(List.of(new ObjectiveInfo.Sub(line, null)), false, false);
	}

	static Set<String> ids(List<LocalCounter.Contribution> c) {
		return Set.copyOf(c.stream().map(LocalCounter.Contribution::id).toList());
	}

	@Test void eachCatchCountsForAnyFishAndItsOwnSpecies() {
		TrackerStore s = store();
		Optional<FishCatchParser.Catch> octopus = FishCatchParser.parse("You caught a 61.0cm Common Octopus");
		Signal.FishCaught signal = new Signal.FishCaught(octopus.orElseThrow().species(), "Common", OVERWORLD);
		assertEquals(Set.of(OCTOPUS, FISHERMAN, RANK_8), ids(LocalCounter.onSignal(signal, s, List.of(), 5)));
		assertEquals(Set.of(FISHERMAN, RANK_8), ids(LocalCounter.onSignal(caught("Flounder"), s, List.of(), 6)));
		assertEquals(Set.of(SHROOM, FISHERMAN, RANK_8), ids(LocalCounter.onSignal(caught("YellowSeaShroom"), s, List.of(), 7)));
		assertEquals(3, s.estimate(FISHERMAN).orElseThrow().count());
		assertEquals(3, s.estimate(RANK_8).orElseThrow().count());
	}

	// ---- one catch, two signals ----

	@Test void chatAfterAReelInReplacesIt() {
		FishDedup d = new FishDedup();
		List<LocalCounter.Contribution> hook = List.of(new LocalCounter.Contribution(FISHERMAN, 1));
		assertTrue(d.hookAllowed(1_000));
		d.hookCounted(1_000, hook);
		assertEquals(hook, d.chatCatch(1_800)); // reverse the reel-in, then count the chat catch
		assertEquals(List.of(), d.chatCatch(2_500)); // a second catch: nothing left to take back
	}

	@Test void reelInAfterAChatCatchIsDropped() {
		FishDedup d = new FishDedup();
		assertEquals(List.of(), d.chatCatch(1_000));
		assertFalse(d.hookAllowed(1_500));
		assertFalse(d.hookAllowed(3_000));
		assertTrue(d.hookAllowed(3_001));
	}

	@Test void signalsFarApartAreTwoCatches() {
		FishDedup d = new FishDedup();
		d.hookCounted(1_000, List.of(new LocalCounter.Contribution(FISHERMAN, 1)));
		assertEquals(List.of(), d.chatCatch(5_000));
	}
}
