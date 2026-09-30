package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SourceTagTest {
	@Test void eachKnownSourceHasItsOwnMarker() {
		List<SourceTag> tags = List.of("jobs", "prestige", "pquests", "challenges").stream().map(SourceTag::of).toList();
		assertEquals(4, tags.stream().map(SourceTag::glyph).collect(Collectors.toSet()).size());
		assertEquals(4, tags.stream().map(SourceTag::argb).collect(Collectors.toSet()).size());
	}

	@Test void unknownAndMissingSourcesShareTheNeutralMarker() {
		assertEquals(SourceTag.of("menu"), SourceTag.of(null));
		assertNotEquals(SourceTag.of("jobs"), SourceTag.of("menu"));
	}
}
