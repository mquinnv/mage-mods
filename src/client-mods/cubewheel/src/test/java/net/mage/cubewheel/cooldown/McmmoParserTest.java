package net.mage.cubewheel.cooldown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.mage.cubewheel.cooldown.McmmoParser.Ability;
import net.mage.cubewheel.cooldown.McmmoParser.Tool;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class McmmoParserTest {
	private static McmmoParser.Message parse(String s) {
		Optional<McmmoParser.Message> m = McmmoParser.parse(s);
		assertTrue(m.isPresent(), s);
		return m.get();
	}

	private static void none(String s) {
		assertEquals(Optional.empty(), McmmoParser.parse(s), s);
	}

	@Test void activatedFromTheActionBar() {
		assertEquals(new McmmoParser.Activated(Ability.SUPER_BREAKER), parse("§a●● §a§lSUPER BREAKER ACTIVATED §a●●"));
		assertEquals(new McmmoParser.Activated(Ability.GIGA_DRILL_BREAKER), parse("§a●● §a§lGIGA DRILL BREAKER ACTIVATED §a●●"));
		assertEquals(new McmmoParser.Activated(Ability.TREE_FELLER), parse("**TREE FELLER ACTIVATED**"));
		assertEquals(new McmmoParser.Activated(Ability.BERSERK), parse("§a●● §a§lBERSERK ACTIVATED §a●●"));
	}

	@Test void refreshedWithAnyColourDigit() {
		for (char n : "0123456789".toCharArray()) {
			assertEquals(new McmmoParser.Refreshed(Ability.SUPER_BREAKER),
					parse("§b§lMINING §" + n + "§l» §" + n + "Your §eSuper Breaker §" + n + "ability is §arefreshed!"));
		}
		assertEquals(new McmmoParser.Refreshed(Ability.SKULL_SPLITTER), parse("Your Skull Splitter ability is refreshed!"));
	}

	@Test void tooTiredCarriesTheSeconds() {
		assertEquals(new McmmoParser.TooTired(12), parse("§amcMMO§7 ➡ §7You are too tired to use that ability again. §e§n(12s)"));
		assertEquals(new McmmoParser.TooTired(187), parse("§amcMMO§8 ➡ §8You are too tired to use that ability again. §e§n(187s)"));
		assertEquals(new McmmoParser.TooTired(5), parse("You are too tired to use that ability again. (5s)"));
	}

	@Test void readiedNamesTheTool() {
		assertEquals(new McmmoParser.Readied(Tool.PICKAXE), parse("§b§lMINING §7§l» §7You §a§nready§7 your pickaxe."));
		assertEquals(new McmmoParser.Readied(Tool.SHOVEL), parse("§6§lEXCAVATION §6§l» §6You §a§nready§6 your Shovel."));
		assertEquals(new McmmoParser.Readied(Tool.FISTS), parse("**YOU READY YOUR FISTS**"));
		assertEquals(new McmmoParser.Readied(Tool.AXE), parse("§2§lWOODCUTTING §2§l» §2You §a§nready§2 your Axe."));
	}

	@Test void playerChatAndOtherLinesAreIgnored() {
		none("§rSteve: SUPER BREAKER ACTIVATED");
		none("§r [VIP] Steve: §amcMMO§7 ➡ §7You are too tired to use that ability again. §e§n(12s)");
		none("§rSteve: Your Super Breaker ability is refreshed!");
		none("Steve: You ready your pickaxe.");
		none("§a●● §a§lSUPER SMASHER ACTIVATED §a●●");
		none("§b§lMINING §7§l» §7You §a§nready§7 your banana.");
		none("§a+1 Mining XP");
		none("");
		none(null);
	}

	@Test void abilityNamesMatchLoosely() {
		assertEquals(Optional.of(Ability.SERRATED_STRIKES), Ability.byName("SERRATED  STRIKES"));
		assertEquals(Optional.of(Ability.GREEN_TERRA), Ability.byName("green terra"));
		assertEquals(Optional.empty(), Ability.byName("Fly"));
	}
}
