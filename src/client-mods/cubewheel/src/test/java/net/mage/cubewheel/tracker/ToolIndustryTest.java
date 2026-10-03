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
}
