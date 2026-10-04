package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolIndustryTest {
	@Test void eachToolPicksItsIndustry() {
		assertEquals("Mining", ToolIndustry.of("minecraft:diamond_pickaxe"));      // "Silky"
		assertEquals("Mining", ToolIndustry.of("minecraft:diamond_shovel"));       // Trench Shovel
		assertEquals("Farming", ToolIndustry.of("minecraft:netherite_hoe"));       // Relic Hoe
		assertEquals("Farming", ToolIndustry.of("minecraft:iron_axe"));            // logs are Farming jobs (Michael 2026-10-03)
		assertEquals("Hunting", ToolIndustry.of("minecraft:netherite_sword"));
		assertEquals("Hunting", ToolIndustry.of("minecraft:bow"));
		assertEquals("Hunting", ToolIndustry.of("minecraft:crossbow"));
		assertEquals("Hunting", ToolIndustry.of("minecraft:trident"));
		assertEquals("Hunting", ToolIndustry.of("minecraft:mace"));
		assertEquals("Fishing", ToolIndustry.of("minecraft:fishing_rod"));
	}

	/** The Phoenix Staff is a netherite hoe underneath but a weapon (Michael 2026-10-03): magic weapons are Hunting. */
	@Test void magicWeaponsBuiltOnToolsAreHunting() {
		assertEquals("Hunting", ToolIndustry.of("minecraft:netherite_hoe", "PHOENIX STAFF OF THE SUN"));
		assertEquals("Hunting", ToolIndustry.of("minecraft:diamond_hoe", "§6Frost Wand"));
		assertEquals("Hunting", ToolIndustry.of("minecraft:golden_shovel", "Storm Scepter"));
		assertEquals("Hunting", ToolIndustry.of("minecraft:iron_pickaxe", "Plasma Blaster"));
		assertEquals("Farming", ToolIndustry.of("minecraft:netherite_hoe", "RELIC HOE"));
		assertEquals("Mining", ToolIndustry.of("minecraft:diamond_pickaxe", "Silky"));
		assertEquals("Farming", ToolIndustry.of("minecraft:netherite_hoe", null));
		assertNull(ToolIndustry.of("minecraft:torch", "Staff of Light")); // only tools and weapons change the panel
	}

	@Test void anythingElseLeavesThePanelAlone() {
		assertNull(ToolIndustry.of("minecraft:torch"));
		assertNull(ToolIndustry.of("minecraft:air"));
		assertNull(ToolIndustry.of("minecraft:carrot_on_a_stick"));
		assertNull(ToolIndustry.of(null));
	}

	@Test void focusKeepsOnlyThatIndustry() {
		JobsPanelModel.Model all = new JobsPanelModel.Model("Jobs", List.of(
				new JobsPanelModel.Line("", "⚒ Farming", "", JobsPanelModel.Tone.INDUSTRY),
				new JobsPanelModel.Line("", "Acacia Logs", "0/630", JobsPanelModel.Tone.CURRENT),
				new JobsPanelModel.Line("", "⚒ Mining", "", JobsPanelModel.Tone.INDUSTRY),
				new JobsPanelModel.Line("", "Gold", "23/152", JobsPanelModel.Tone.CURRENT),
				new JobsPanelModel.Line("IH", "Ice Crystals", "0/85", JobsPanelModel.Tone.OTHER_WORLD)));
		JobsPanelModel.Model mining = JobsPanelModel.focus(all, "Mining");
		assertEquals(List.of("⚒ Mining", "Gold", "Ice Crystals"), mining.lines().stream().map(JobsPanelModel.Line::text).toList());
		assertEquals("Jobs", mining.title());
		assertSame(all, JobsPanelModel.focus(all, null));      // nothing held yet: everything
		assertSame(all, JobsPanelModel.focus(all, "Fishing")); // no Fishing jobs: everything rather than nothing
	}

	/** Entries with recent progress stay whatever is held, under their own heading (Michael 2026-10-04). */
	@Test void focusKeepsRecentProgressFromOtherIndustries() {
		JobsPanelModel.Model all = new JobsPanelModel.Model("Jobs", List.of(
				new JobsPanelModel.Line("", "⚒ Farming", "", JobsPanelModel.Tone.INDUSTRY),
				new JobsPanelModel.Line("", "Acacia Logs", "0/630", JobsPanelModel.Tone.CURRENT),
				new JobsPanelModel.Line("", "Wheat", "40/200", JobsPanelModel.Tone.CURRENT, null, Activity.RECENT),
				new JobsPanelModel.Line("", "⚒ Hunting", "", JobsPanelModel.Tone.INDUSTRY),
				new JobsPanelModel.Line("IH", "Zombie Moose", "58/70", JobsPanelModel.Tone.CURRENT, null, Activity.ACTIVE),
				new JobsPanelModel.Line("", "⚒ Mining", "", JobsPanelModel.Tone.INDUSTRY),
				new JobsPanelModel.Line("", "Gold", "23/152", JobsPanelModel.Tone.CURRENT)));
		assertEquals(List.of("⚒ Farming", "Wheat", "⚒ Hunting", "Zombie Moose", "⚒ Mining", "Gold"),
				JobsPanelModel.focus(all, "Mining").lines().stream().map(JobsPanelModel.Line::text).toList());
	}
}
