package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import net.mage.cubewheel.tracker.TrackerStore.HudSection;
import net.mage.cubewheel.tracker.TrackerStore.HudSection.Kind;
import net.mage.cubewheel.tracker.local.WorldInfo;
import net.mage.cubewheel.tracker.local.WorldScope.Mode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The HUD is split into labelled groups: pinned, this world, anywhere, other worlds. */
class TrackerStoreSectionsTest {
	@TempDir Path dir;
	final TrackerStoreWorldFilterTest fixtures = new TrackerStoreWorldFilterTest();

	TrackerStore store() {
		fixtures.dir = dir;
		return fixtures.store();
	}

	static List<Kind> kinds(List<HudSection> sections) {
		return sections.stream().map(HudSection::kind).toList();
	}

	static List<String> ids(HudSection s) {
		return s.rows().stream().map(r -> r.item().id()).toList();
	}

	@Test void sortSplitsIntoThisWorldAnywhereAndOtherWorlds() {
		TrackerStore s = store();
		s.togglePin(TrackerStoreWorldFilterTest.HAVEN);
		List<HudSection> out = s.hudSections(10, false, TrackerStoreWorldFilterTest.TANGLEROOT,
				TrackerStoreWorldFilterTest.WORLDS, Mode.SORT);
		assertEquals(List.of(Kind.PINNED, Kind.THIS_WORLD, Kind.ANYWHERE, Kind.OTHER_WORLDS), kinds(out));
		assertEquals(List.of(TrackerStoreWorldFilterTest.HAVEN), ids(out.get(0)));
		assertEquals(List.of(TrackerStoreWorldFilterTest.TIGERS), ids(out.get(1)));
		assertEquals(List.of(TrackerStoreWorldFilterTest.STONE), ids(out.get(2)));
		assertEquals(List.of(TrackerStoreWorldFilterTest.SNAKES), ids(out.get(3)));
	}

	@Test void emptyGroupsAreLeftOutAndTheLineCapCountsOnlyEntries() {
		TrackerStore s = store();
		List<HudSection> out = s.hudSections(2, false, TrackerStoreWorldFilterTest.TANGLEROOT,
				TrackerStoreWorldFilterTest.WORLDS, Mode.SORT);
		assertEquals(List.of(Kind.THIS_WORLD, Kind.ANYWHERE), kinds(out));
	}

	@Test void unknownWorldOrOffIsPinnedPlusOneGroup() {
		TrackerStore s = store();
		s.togglePin(TrackerStoreWorldFilterTest.TIGERS);
		assertEquals(List.of(Kind.PINNED, Kind.ANYWHERE), kinds(s.hudSections(10, false, WorldInfo.UNKNOWN,
				TrackerStoreWorldFilterTest.WORLDS, Mode.SORT)));
		assertEquals(List.of(Kind.PINNED, Kind.ANYWHERE), kinds(s.hudSections(10, false,
				TrackerStoreWorldFilterTest.TANGLEROOT, TrackerStoreWorldFilterTest.WORLDS, Mode.OFF)));
	}

	@Test void flattenedSectionsMatchHudRows() {
		TrackerStore s = store();
		List<TrackerRow> flat = s.hudSections(10, false, TrackerStoreWorldFilterTest.TANGLEROOT,
				TrackerStoreWorldFilterTest.WORLDS, Mode.SORT).stream().flatMap(h -> h.rows().stream()).toList();
		assertEquals(s.hudRows(10, false, TrackerStoreWorldFilterTest.TANGLEROOT, TrackerStoreWorldFilterTest.WORLDS,
				Mode.SORT), flat);
	}
}
