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
import net.mage.cubewheel.tracker.local.CounterRule.NamedWorld;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 2026-10-10: "Dusty Situation" (master party quest, "Find 30 Sandara Artifacts": brushing suspicious sand in
 * Sandara) "isn't dynamically updating or even recognizing it as a focus item". ManaCube announces every find in
 * chat, everyone's: "[Artifacts] miniminou dug up a  Scarab Brooch", "[Artifacts] Zzenns dug up an  Obsidian
 * Pyramid Statue" (capture 2026-10-10).
 */
class ArtifactTest {
	@TempDir Path dir;
	static final WorldInfo SANDARA = new WorldInfo(Set.of("sandara"), true, true);
	static final WorldInfo OVERWORLD = new WorldInfo(Set.of("overworld"), false, true);
	static final String DUSTY = "pquests:Dusty Situation";

	// ---- the chat line ----

	/** Your own find says "You" (capture 2026-10-10 16:01); everyone else's is named, and is theirs. */
	@Test void yourOwnFindsOnly() {
		assertEquals(Optional.of("Pharaoh Staff"), ArtifactParser.parse("[Artifacts] You dug up a  Pharaoh Staff", null));
		assertEquals(Optional.of("Pharaoh Staff"), ArtifactParser.parse("[Artifacts] You dug up a  Pharaoh Staff", "Steve"));
		assertEquals(Optional.of("Obsidian Pyramid Statue"),
				ArtifactParser.parse("§7[§eArtifacts§7] §6You dug up an §f §dObsidian Pyramid Statue", null));
		assertEquals(Optional.of("Scarab Brooch"), // named as yourself: yours too
				ArtifactParser.parse("[Artifacts] miniminou dug up a  Scarab Brooch", "MiniMinou"));
		assertTrue(ArtifactParser.parse("[Artifacts] miniminou dug up a  Scarab Brooch", "Steve").isEmpty());
		assertTrue(ArtifactParser.parse("[Artifacts] miniminou dug up a  Scarab Brooch", null).isEmpty());
		assertTrue(ArtifactParser.parse("[Artifacts] Zzenns dug up an  Obsidian Pyramid Statue", "Steve").isEmpty());
		assertTrue(ArtifactParser.parse("§r[VIP] Zzenns: [Artifacts] You dug up a Scarab Brooch", "Steve").isEmpty());
		assertTrue(ArtifactParser.parse("You dug up a Scarab Brooch", "Steve").isEmpty());
		assertTrue(ArtifactParser.parse(null, "Steve").isEmpty());
	}

	// ---- the objective ----

	@Test void findNArtifactsIsAFindRuleInThatWorld() {
		assertEquals(Optional.of(new CounterRule(Kind.FIND, 30, new Named("artifact"), new NamedWorld("sandara"))),
				ObjectiveParserTest.parse("Find 30 Sandara Artifacts"));
		assertEquals(Optional.of(new CounterRule(Kind.FIND, 5, new Named("artifact"), new AnyWorld())),
				ObjectiveParserTest.parse("Dig up 5 Artifacts"));
		assertEquals(Optional.of(new CounterRule(Kind.BREAK, 814, new Named("dirt"), new AnyWorld())),
				ObjectiveParserTest.parse("Dig 0/814 Dirt")); // "dig" alone is still a break
	}

	// ---- matching ----

	@Test void aFindCountsInItsWorldOnly() {
		CounterRule sandara = ObjectiveParserTest.parse("Find 30 Sandara Artifacts").orElseThrow();
		assertTrue(RuleMatcher.matches(sandara, new Signal.ArtifactDug("Scarab Brooch", SANDARA)));
		assertTrue(RuleMatcher.matches(sandara, new Signal.ArtifactDug("Obsidian Pyramid Statue", SANDARA)));
		assertFalse(RuleMatcher.matches(sandara, new Signal.ArtifactDug("Scarab Brooch", OVERWORLD)));
		assertFalse(RuleMatcher.matches(sandara, new Signal.ArtifactDug("Scarab Brooch", new WorldInfo(Set.of(), false, false))));
		// A rule naming one artifact counts only that one; other kinds never count a find.
		CounterRule brooch = new CounterRule(Kind.FIND, 3, new Named("scarab brooch"), new AnyWorld());
		assertTrue(RuleMatcher.matches(brooch, new Signal.ArtifactDug("Scarab Brooch", SANDARA)));
		assertFalse(RuleMatcher.matches(brooch, new Signal.ArtifactDug("Golden Cat", SANDARA)));
		assertTrue(RuleMatcher.matches(new CounterRule(Kind.FIND, 3, new Any(), new AnyWorld()), new Signal.ArtifactDug("Golden Cat", SANDARA)));
		assertFalse(RuleMatcher.matches(new CounterRule(Kind.BREAK, 3, new Named("artifact"), new AnyWorld()), new Signal.ArtifactDug("Golden Cat", SANDARA)));
	}

	// ---- counting ----

	@Test void eachFindCreditsDustySituation() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		s.update("pquests", "Dusty Situation", new Progress(63, 100), 0);
		s.setObjective(DUSTY, new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Find 30 Sandara Artifacts", null)), false, false));
		String name = ArtifactParser.parse("[Artifacts] You dug up a  Pharaoh Staff", null).orElseThrow();
		List<LocalCounter.Contribution> added = LocalCounter.onSignal(new Signal.ArtifactDug(name, SANDARA), s, ObjectiveParserTest.WORLDS,5);
		assertEquals(List.of(DUSTY), added.stream().map(LocalCounter.Contribution::id).toList());
		assertEquals(1, s.estimate(DUSTY).orElseThrow().count());
		assertTrue(LocalCounter.onSignal(new Signal.ArtifactDug("Golden Cat", OVERWORLD), s, ObjectiveParserTest.WORLDS,6).isEmpty());
	}
}
