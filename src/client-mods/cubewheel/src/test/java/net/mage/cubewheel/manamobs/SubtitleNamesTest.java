package net.mage.cubewheel.manamobs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubtitleNamesTest {
	@Test
	void replacesTheVanillaNameWhenItAppearsAsAWord() {
		assertEquals("Mana Wolf growls", SubtitleNames.rename("Wolf growls", "Wolf", "Mana Wolf"));
		assertEquals("Viper squishes", SubtitleNames.rename("Slime squishes", "Slime", "Viper"));
	}

	@Test
	void prefixesWhenTheVanillaNameIsAbsent() {
		assertEquals("Mana Wolf: Footsteps", SubtitleNames.rename("Footsteps", "Wolf", "Mana Wolf"));
	}

	@Test
	void replacesWholeWordsOnly() {
		assertEquals("Mana Wolf: Wolfish wolf", SubtitleNames.rename("Wolfish wolf", "Wolf", "Mana Wolf"));
	}

	@Test
	void replacesOnlyTheFirstOccurrence() {
		assertEquals("Mana Wolf and Wolf", SubtitleNames.rename("Wolf and Wolf", "Wolf", "Mana Wolf"));
	}

	@Test
	void capturedVoices() {
		assertEquals("Mana Wolf hurts", SubtitleNames.rename("Polar Bear hurts", "Polar Bear", "Mana Wolf"));
		assertEquals("Viper hisses", SubtitleNames.rename("Silverfish hisses", "Silverfish", "Viper"));
	}

	@Test
	void namesWithRegexAndReplacementSpecialsAreLiteral() {
		assertEquals("$1 (x) growls", SubtitleNames.rename("Wolf growls", "Wolf", "$1 (x)"));
	}

	@Test
	void entityTypeFromKey() {
		assertEquals(Optional.of("minecraft:wolf"), SubtitleNames.entityType("subtitles.entity.wolf.growl"));
		assertEquals(Optional.of("minecraft:cave_spider"), SubtitleNames.entityType("subtitles.entity.cave_spider.hurt"));
		assertEquals(Optional.of("minecraft:polar_bear"), SubtitleNames.entityType("subtitles.entity.polar_bear.warning"));
	}

	@Test
	void nonEntityKeysAreEmpty() {
		assertEquals(Optional.empty(), SubtitleNames.entityType("subtitles.block.chest.open"));
		assertEquals(Optional.empty(), SubtitleNames.entityType("subtitles.entity."));
		assertEquals(Optional.empty(), SubtitleNames.entityType("subtitles.entity.wolf"));
		assertEquals(Optional.empty(), SubtitleNames.entityType(null));
	}

	@Test
	void nonMobTypesAreExcluded() {
		for (String k : new String[] {"subtitles.entity.player.hurt", "subtitles.entity.experience_orb.pickup",
				"subtitles.entity.item.pickup", "subtitles.entity.generic.big_fall", "subtitles.entity.generic.hurt",
				"subtitles.entity.armor_stand.hit"}) {
			assertEquals(Optional.empty(), SubtitleNames.entityType(k), k);
		}
	}
}
