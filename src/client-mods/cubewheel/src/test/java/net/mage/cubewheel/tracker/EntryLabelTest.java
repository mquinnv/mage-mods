package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.mage.cubewheel.tracker.local.ObjectiveInfo;
import net.mage.cubewheel.tracker.local.ObjectiveInfo.Sub;
import org.junit.jupiter.api.Test;

/** HUD entries say what to do, not just the quest's name. Real ManaCube party quest lore, 2026-09-30. */
class EntryLabelTest {
	static ObjectiveInfo info(Sub... subs) {
		return new ObjectiveInfo(List.of(subs), false, false);
	}

	@Test void singleObjectiveIsAppendedToTheName() {
		EntryLabel l = EntryLabel.of("Jungle Pursuit", info(new Sub("Mine 15,000 Tangleroots Resources", null)));
		assertEquals("Jungle Pursuit · Mine 15,000 Tangleroots Resources", l.title());
		assertEquals(List.of(), l.details());
	}

	@Test void severalObjectivesBecomeDetailLinesWithTheirPercent() {
		EntryLabel l = EntryLabel.of("King of the Jungle",
				info(new Sub("Slay 2,500 Tangleroot Monsters", 0.0), new Sub("Slay 10 Golden Knights", 10.0)));
		assertEquals("King of the Jungle", l.title());
		assertEquals(List.of("0% Slay 2,500 Tangleroot Monsters", "10% Slay 10 Golden Knights"), l.details());
	}

	@Test void namesThatAlreadyCarryTheObjectiveStayAsTheyAre() {
		// Job listings and prestige ranks are already named after their objective.
		assertEquals("Hunting Beginner · Slay Tigers in Tangleroots", EntryLabel.of(
				"Hunting Beginner · Slay Tigers in Tangleroots", info(new Sub("Slay 16/64 Tigers in Tangleroots", null))).title());
		assertEquals("Rank [✪4] · Reach 2,500 Skill Level", EntryLabel.of(
				"Rank [✪4] · Reach 2,500 Skill Level", info(new Sub("Reach 2,500 Skill Level [Lvl 1902/2,500]", null))).title());
	}

	@Test void noObjectiveKeepsTheName() {
		assertEquals("GOLDEN CRATE", EntryLabel.of("GOLDEN CRATE", null).title());
		assertEquals("GOLDEN CRATE", EntryLabel.of("GOLDEN CRATE", info()).title());
	}

	@Test void subsWithoutPercentHaveNoPercentPrefix() {
		EntryLabel l = EntryLabel.of("Q", info(new Sub("Do A", null), new Sub("Do B", 50.0)));
		assertEquals(List.of("Do A", "50% Do B"), l.details());
	}
}
