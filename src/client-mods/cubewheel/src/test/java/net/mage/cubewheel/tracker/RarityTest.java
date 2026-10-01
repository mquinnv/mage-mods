package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.mage.cubewheel.tracker.JobsPanelModel.Line;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;
import net.mage.cubewheel.tracker.local.WorldScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fish targets in the Jobs panel take the server's rarity colour (real job objectives, 2026-09-30). */
class RarityTest {
	@TempDir Path dir;

	@Test void glyphsMarkRarities() {
		assertEquals(Optional.of(Rarity.COMMON), Rarity.inText("Catch 1/3  \uF895 Goldfish while fishing"));
		assertEquals(Optional.of(Rarity.UNCOMMON), Rarity.inText("Catch 0/9 \uF894 YellowSeaShroom while fishing"));
		assertEquals(Optional.of(Rarity.RARE), Rarity.inText("Catch 3/22 \uF893 Octopus while fishing"));
		assertEquals(Optional.of(Rarity.LEGENDARY), Rarity.inText("Catch 0/1 \uF892 Kraken while fishing"));
		assertEquals(Optional.empty(), Rarity.inText("Slay 16/64 Tigers in Tangleroots"));
	}

	@Test void jobsPanelCarriesTheRarity() {
		TrackerStore s = new TrackerStore(dir.resolve("t.json"));
		String id = "jobs:Fishing Heavy · Catch  Octopus while fishing";
		s.update("jobs", "Fishing Heavy · Catch  Octopus while fishing", new ProgressExtractor.Progress(3, 22), 0);
		s.setObjective(id, new ObjectiveInfo(List.of(new ObjectiveInfo.Sub("Catch 3/22 \uF893 Octopus while fishing", null)),
				false, false));
		s.update("jobs", "Farming Heavy · Harvest Cherry Logs", new ProgressExtractor.Progress(1, 4), 0);
		List<Line> lines = JobsPanelModel.build(s.rows(true), s::isHidden, x -> s.objective(x).orElse(null),
				t -> WorldScope.Relevance.NEUTRAL, List.of(), 60_000).lines();
		Line octopus = lines.stream().filter(l -> l.text().equals("Octopus")).findFirst().orElseThrow();
		assertEquals(Rarity.RARE, octopus.rarity());
		Line logs = lines.stream().filter(l -> l.text().equals("Cherry Logs")).findFirst().orElseThrow();
		assertEquals(null, logs.rarity());
	}
}
