package net.mage.cubewheel.cooldown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.cooldown.McmmoParser.Ability;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McmmoCooldownsTest {
	private static final String ACTIVATED = "§a●● §a§lSUPER BREAKER ACTIVATED §a●●";
	private static final String REFRESHED = "§b§lMINING §7§l» §7Your §eSuper Breaker §7ability is §arefreshed!";
	private static final String TIRED_12 = "§amcMMO§7 ➡ §7You are too tired to use that ability again. §e§n(12s)";
	private static final String READY_PICK = "§b§lMINING §7§l» §7You §a§nready§7 your pickaxe.";
	private static final String READY_SHOVEL = "§6§lEXCAVATION §6§l» §6You §a§nready§6 your Shovel.";

	private static boolean feed(McmmoCooldowns c, String line, long now) {
		return c.apply(McmmoParser.parse(line).orElseThrow(), now);
	}

	@Test void activationStartsTheDefaultCooldown() {
		McmmoCooldowns c = new McmmoCooldowns(null);
		feed(c, ACTIVATED, 1_000);
		List<McmmoCooldowns.Row> rows = c.rows(1_000);
		assertEquals(List.of(new McmmoCooldowns.Row(Ability.SUPER_BREAKER, 241_000, false)), rows);
		assertTrue(c.rows(241_000).isEmpty());
	}

	@Test void refreshClearsShowsReadyAndLearns() {
		McmmoCooldowns c = new McmmoCooldowns(null);
		feed(c, ACTIVATED, 0);
		assertTrue(feed(c, REFRESHED, 180_400)); // 180.4 s -> 180
		assertEquals(180, c.cooldownS(Ability.SUPER_BREAKER));
		assertEquals(List.of(new McmmoCooldowns.Row(Ability.SUPER_BREAKER, 185_400, true)), c.rows(180_400));
		assertTrue(c.rows(185_400).isEmpty());
		feed(c, ACTIVATED, 200_000);
		assertEquals(380_000, c.rows(200_000).get(0).endsAt());
	}

	@Test void implausibleCooldownsAreNotLearned() {
		McmmoCooldowns c = new McmmoCooldowns(null);
		feed(c, ACTIVATED, 0);
		assertFalse(feed(c, REFRESHED, 5_000));
		feed(c, ACTIVATED, 10_000);
		assertFalse(feed(c, REFRESHED, 10_000 + 3_700_000));
		assertFalse(feed(c, REFRESHED, 4_000_000)); // refresh without a seen activation
		assertEquals(240, c.cooldownS(Ability.SUPER_BREAKER));
	}

	@Test void tooTiredSetsTheReadiedToolsAbility() {
		McmmoCooldowns c = new McmmoCooldowns(null);
		feed(c, READY_SHOVEL, 0);
		feed(c, TIRED_12, 1_000);
		assertEquals(List.of(new McmmoCooldowns.Row(Ability.GIGA_DRILL_BREAKER, 13_000, false)), c.rows(1_000));
		feed(c, ACTIVATED, 2_000);
		feed(c, READY_PICK, 60_000);
		feed(c, TIRED_12, 60_000);
		assertEquals(72_000, c.rows(60_000).stream().filter(r -> r.ability() == Ability.SUPER_BREAKER).findFirst().orElseThrow().endsAt());
	}

	@Test void tooTiredWithoutAReadyUsesTheLastActivation() {
		McmmoCooldowns c = new McmmoCooldowns(null);
		feed(c, TIRED_12, 0); // nothing known: ignored
		assertTrue(c.rows(0).isEmpty());
		feed(c, "§a●● §a§lTREE FELLER ACTIVATED §a●●", 0);
		feed(c, TIRED_12, 100_000);
		assertEquals(112_000, c.rows(100_000).get(0).endsAt());
	}

	@Test void axeMeansSkullSplitterAfterASkullSplitterActivation() {
		McmmoCooldowns c = new McmmoCooldowns(null);
		feed(c, "§a●● §a§lSKULL SPLITTER ACTIVATED §a●●", 0);
		feed(c, "§2§lAXES §2§l» §2You §a§nready§2 your Axe.", 50_000);
		feed(c, TIRED_12, 50_000);
		assertEquals(Ability.SKULL_SPLITTER, c.rows(50_000).get(0).ability());
		assertEquals(62_000, c.rows(50_000).get(0).endsAt());
	}

	@Test void learnedCooldownsPersist(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("cubewheel-mcmmo.json");
		McmmoCooldowns c = new McmmoCooldowns(f);
		feed(c, ACTIVATED, 0);
		feed(c, REFRESHED, 120_000);
		c.save();
		McmmoCooldowns d = new McmmoCooldowns(f);
		d.load();
		assertEquals(120, d.cooldownS(Ability.SUPER_BREAKER));
		assertEquals(60, d.cooldownS(Ability.BLAST_MINING));
		Files.writeString(f, "{\"Super Breaker\": 5, \"Nope\": 100, \"Tree Feller\": 90}");
		McmmoCooldowns e = new McmmoCooldowns(f);
		e.load();
		assertEquals(240, e.cooldownS(Ability.SUPER_BREAKER));
		assertEquals(90, e.cooldownS(Ability.TREE_FELLER));
		Files.writeString(f, "not json");
		new McmmoCooldowns(f).load(); // logged, no throw
	}
}
