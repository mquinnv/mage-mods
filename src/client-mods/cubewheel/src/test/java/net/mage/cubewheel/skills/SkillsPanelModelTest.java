package net.mage.cubewheel.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The skills panel's rows: ordering, caps, tones and number formatting (captured bar values 2026-10). */
class SkillsPanelModelTest {
	private static final long NOW = 1_000_000;
	private static final SkillsPanelModel.Options RECENT =
			new SkillsPanelModel.Options(SkillsPanelModel.Mode.RECENT, 60_000, 3, false);
	private static final SkillsPanelModel.Options ALL =
			new SkillsPanelModel.Options(SkillsPanelModel.Mode.ALL, 60_000, 3, false);

	@TempDir Path dir;

	private SkillStore store() {
		return new SkillStore(dir.resolve("skills.json"));
	}

	private static SkillBarParser.Reading bar(Skill s, int level, long xp, long needed) {
		return new SkillBarParser.Reading(s, level, xp, needed);
	}

	private static List<Skill> skills(SkillsPanelModel.View v) {
		return v.rows().stream().map(SkillsPanelModel.Row::skill).toList();
	}

	@Test void recentOrdersNewestFirstAndCapsAtMax() {
		SkillStore s = store();
		s.bar("q", bar(Skill.MINING, 617, 65682, 155750), NOW - 40_000);
		s.bar("q", bar(Skill.SWORDS, 242, 16101, 62000), NOW - 10_000);
		s.bar("q", bar(Skill.AXES, 268, 11461, 68500), NOW - 20_000);
		s.bar("q", bar(Skill.UNARMED, 56, 12035, 15500), NOW - 30_000);
		SkillsPanelModel.View v = SkillsPanelModel.build(s.account("q"), Map.of(), RECENT, NOW);
		assertEquals(List.of(Skill.SWORDS, Skill.AXES, Skill.UNARMED), skills(v));
	}

	@Test void recentIsEmptyWhenNothingIsRecent() {
		SkillStore s = store();
		s.bar("q", bar(Skill.MINING, 617, 65682, 155750), NOW - 600_000);
		s.menu("q", List.of(new SkillsMenu.Entry(Skill.TAMING, 500, 500, false)), NOW - 1);
		assertTrue(SkillsPanelModel.build(s.account("q"), Map.of(), RECENT, NOW).rows().isEmpty());
	}

	@Test void allSortsByLevelThenLabelAndOmitsChildUnlessAsked() {
		SkillStore s = store();
		s.menu("q", List.of(
				new SkillsMenu.Entry(Skill.MINING, 293, 2500, false),
				new SkillsMenu.Entry(Skill.AXES, 131, 1000, false),
				new SkillsMenu.Entry(Skill.ARCHERY, 131, 1000, false),
				new SkillsMenu.Entry(Skill.SMELTING, 150, 3000, true)), NOW);
		SkillStore.Account a = s.account("q");
		assertEquals(List.of(Skill.MINING, Skill.ARCHERY, Skill.AXES),
				skills(SkillsPanelModel.build(a, Map.of(), ALL, NOW)));
		SkillsPanelModel.Options withChild = new SkillsPanelModel.Options(SkillsPanelModel.Mode.ALL, 60_000, 3, true);
		assertEquals(List.of(Skill.MINING, Skill.SMELTING, Skill.ARCHERY, Skill.AXES),
				skills(SkillsPanelModel.build(a, Map.of(), withChild, NOW)));
	}

	@Test void cappedSkillShowsMax() {
		SkillStore s = store();
		s.menu("q", List.of(new SkillsMenu.Entry(Skill.TAMING, 500, 500, false)), NOW);
		SkillsPanelModel.Row r = SkillsPanelModel.build(s.account("q"), Map.of(), ALL, NOW).rows().get(0);
		assertEquals("Taming 500", r.label());
		assertEquals("MAX", r.right());
		assertEquals(1.0, r.fraction());
		assertTrue(r.capped());
		assertFalse(r.unknown());
	}

	@Test void menuOnlySkillIsUnknownAndStale() {
		SkillStore s = store();
		s.menu("q", List.of(new SkillsMenu.Entry(Skill.ALCHEMY, 7, 500, false)), NOW);
		SkillsPanelModel.Row r = SkillsPanelModel.build(s.account("q"), Map.of(), ALL, NOW).rows().get(0);
		assertEquals("?", r.right());
		assertEquals(0.0, r.fraction());
		assertTrue(r.unknown());
		assertFalse(r.capped());
		assertEquals(SkillsPanelModel.Tone.STALE, r.tone());
	}

	@Test void toneFollowsXpAt() {
		SkillStore s = store();
		s.bar("q", bar(Skill.MINING, 617, 65682, 155750), NOW - 59_000);
		s.bar("q", bar(Skill.AXES, 268, 11461, 68500), NOW - 61_000);
		SkillsPanelModel.View v = SkillsPanelModel.build(s.account("q"), Map.of(), ALL, NOW);
		assertEquals(SkillsPanelModel.Tone.FRESH, v.rows().get(0).tone());
		assertEquals(SkillsPanelModel.Tone.STALE, v.rows().get(1).tone());
	}

	@Test void accentComesFromRecentGains() {
		SkillStore s = store();
		s.bar("q", bar(Skill.MINING, 617, 65682, 155750), NOW - 1000);
		s.bar("q", bar(Skill.AXES, 268, 11461, 68500), NOW - 1000);
		SkillsPanelModel.View v = SkillsPanelModel.build(s.account("q"), Map.of(Skill.AXES, 50L), ALL, NOW);
		assertFalse(v.rows().get(0).accent());
		assertTrue(v.rows().get(1).accent());
	}

	@Test void numbersCarryThousandsSeparators() {
		SkillStore s = store();
		s.bar("q", bar(Skill.MINING, 617, 65682, 155750), NOW);
		s.power("q", 3148, NOW);
		SkillsPanelModel.View v = SkillsPanelModel.build(s.account("q"), Map.of(), ALL, NOW);
		assertEquals("Mining 617", v.rows().get(0).label());
		assertEquals("65,682 / 155,750", v.rows().get(0).right());
		assertEquals(3148, v.header().power());
		assertEquals("3,148", v.header().powerText());
	}

	@Test void unknownPowerHasNoText() {
		SkillsPanelModel.View v = SkillsPanelModel.build(store().account("q"), Map.of(), ALL, NOW);
		assertEquals("", v.header().powerText());
	}

	@Test void fractionIsClamped() {
		SkillStore s = store();
		s.bar("q", bar(Skill.MINING, 10, 31000, 4000), NOW);
		s.bar("q", bar(Skill.AXES, 10, 2000, 4000), NOW - 1);
		SkillsPanelModel.View v = SkillsPanelModel.build(s.account("q"), Map.of(), ALL, NOW);
		assertEquals(0.5, v.rows().get(0).fraction());
		assertEquals(1.0, v.rows().get(1).fraction());
	}
}
