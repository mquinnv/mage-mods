package net.mage.cubewheel.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Every bar name is one captured from ManaCube Survival (see the plan's captured facts). */
class SkillBarParserTest {
	private static void reads(Skill skill, int level, long xp, long needed, String bar) {
		assertEquals(Optional.of(new SkillBarParser.Reading(skill, level, xp, needed)), SkillBarParser.parse(bar), bar);
	}

	private static void none(String bar) {
		assertEquals(Optional.empty(), SkillBarParser.parse(bar), bar);
	}

	@Test void capturedSkillBarsParse() {
		reads(Skill.MINING, 243, 27_596, 62_250, "MINING » ʟᴠʟ. 243 [27,596 / 62,250 xp]");
		reads(Skill.MINING, 617, 65_682, 155_750, "MINING » ʟᴠʟ. 617 [65,682 / 155,750 xp]");
		reads(Skill.ACROBATICS, 126, 18_419, 33_000, "ACROBATICS » ʟᴠʟ. 126 [18,419 / 33,000 xp]");
		reads(Skill.UNARMED, 56, 12_035, 15_500, "UNARMED » ʟᴠʟ. 56 [12,035 / 15,500 xp]");
		reads(Skill.AXES, 268, 11_461, 68_500, "AXES » ʟᴠʟ. 268 [11,461 / 68,500 xp]");
	}

	@Test void swordsOmitsTheSpaceAfterTheLevelDot() {
		reads(Skill.SWORDS, 242, 16_101, 62_000, "SWORDS » ʟᴠʟ.242 [16,101 / 62,000 xp]");
	}

	@Test void colourCodesAreStripped() {
		reads(Skill.MINING, 243, 27_596, 62_250, "§eMINING » ʟᴠʟ. 243 [27,596 / 62,250 xp]");
		reads(Skill.MINING, 243, 27_596, 62_250, "§eMINING §7» §fʟᴠʟ. 243 §7[§f27,596 §7/ §f62,250 xp§7]");
	}

	@Test void nonSkillBarsAreRejected() {
		none("MANA GOLEM [SWORDS] 2⺛ - Damage Dealt: 12 for loot");
		none("GOLDEN KNIGHT 3⺛ - Damage Dealt: 0 for loot");
		none("Anomaly: Mining Fatigue I - 0:42");
		none("Mana Pond (Spawn) 120/256");
		none("/p like sushi");
	}

	@Test void unknownSkillWordIsRejected() {
		none("FLYING » ʟᴠʟ. 10 [1 / 4,000 xp]");
	}

	@Test void nullAndEmptyAreRejected() {
		none(null);
		none("");
	}
}
