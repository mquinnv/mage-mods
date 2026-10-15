package net.mage.cubewheel.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkillTextParserTest {
	private static Optional<SkillTextParser.Message> parse(String text) {
		return SkillTextParser.parse(text, "Qualan");
	}

	@Test
	void levelUpLineWithColourCodes() {
		assertEquals(Optional.of(new SkillTextParser.LevelUp(Skill.MINING, 245)),
				parse("§a✚ §7| §aMining is now level §a§l245"));
	}

	@Test
	void levelUpLineWithoutCodesAndWithSeparators() {
		assertEquals(Optional.of(new SkillTextParser.LevelUp(Skill.WOODCUTTING, 1000)),
				parse("✚ | Woodcutting is now level 1,000"));
	}

	@Test
	void levelUpWorksWithoutOwnName() {
		assertEquals(Optional.of(new SkillTextParser.LevelUp(Skill.MINING, 245)),
				SkillTextParser.parse("§a✚ §7| §aMining is now level §a§l245", null));
	}

	@Test
	void unknownSkillWordIsNotALevelUp() {
		assertTrue(parse("§a✚ §7| §aCooking is now level §a§l5").isEmpty());
	}

	@Test
	void abilityUnlockLineIsIgnored() {
		assertTrue(parse("§amcMMO§8 ➡ §7You have the unlocked the §a§nRupture§7 ability!").isEmpty());
	}

	@Test
	void playerChatIsIgnored() {
		assertTrue(parse("Qualan: Mining is now level 5").isEmpty());
		assertTrue(parse("<Qualan> Mining is now level 5").isEmpty());
	}

	@Test
	void ownPowerLevelBroadcast() {
		assertEquals(Optional.of(new SkillTextParser.PowerLevel(1900)),
				parse("Qualan has reached a total Power Level of 1,900"));
	}

	@Test
	void ownMilestoneBroadcastAnyCase() {
		assertEquals(Optional.of(new SkillTextParser.SkillMilestone(Skill.WOODCUTTING, 200)),
				parse("Qualan has reached level 200 in WOODCUTTING"));
		assertEquals(Optional.of(new SkillTextParser.SkillMilestone(Skill.MINING, 1000)),
				parse("Qualan has reached level 1,000 in Mining"));
	}

	@Test
	void otherPlayersBroadcastsAreIgnored() {
		assertTrue(parse("*Buttle has reached level 1,000 in Mining").isEmpty());
		assertTrue(parse("Buttle has reached a total Power Level of 1,900").isEmpty());
	}

	@Test
	void nullOwnNameDisablesBroadcasts() {
		assertTrue(SkillTextParser.parse("Qualan has reached a total Power Level of 1,900", null).isEmpty());
		assertTrue(SkillTextParser.parse("Qualan has reached level 200 in WOODCUTTING", null).isEmpty());
	}

	@Test
	void ownNameIsMatchedLiterally() {
		assertTrue(SkillTextParser.parse("Qualan has reached level 200 in Mining", "Q.*").isEmpty());
	}

	@Test
	void nullAndBlankAreEmpty() {
		assertTrue(parse(null).isEmpty());
		assertTrue(parse("").isEmpty());
	}
}
