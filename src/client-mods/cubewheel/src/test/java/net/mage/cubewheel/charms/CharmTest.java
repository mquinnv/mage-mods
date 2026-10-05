package net.mage.cubewheel.charms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Lore as captured 2026-09-30 … 10-03. */
class CharmTest {
	private static Charm parse(String name, String... lore) {
		return Charm.parse(name, List.of(lore)).orElseThrow(() -> new AssertionError(name));
	}

	@Test void undeadAmuletIsPlusFiftyWithAZombie() {
		Charm c = parse("Undead Amulet", "Undead Amulet", "", "Having this amulet grants you", "great powers. Deal +50% damage",
				"to all Undead Monsters and Mobs", "", "Equip a Pendant by placing in the", "bottom right slot of your inventory");
		assertEquals(Charm.Kind.AMULET, c.kind());
		assertEquals("minecraft:zombie_head", c.icon());
		assertEquals("+50%", c.text());
	}

	@Test void pendantsAreAmuletsToo() {
		Charm star = parse("STAR PENDANT", "Unbreakable", "Star Pendant", "", "ITEM EFFECTS: (Pendant Slot)",
				"➟ Take -20% Blaster Damage", "", "Equip a Pendant by placing in the", "bottom right slot of your inventory");
		assertEquals(Charm.Kind.AMULET, star.kind());
		assertEquals("minecraft:shield", star.icon());
		assertEquals("-20%", star.text());
		Charm mermaid = parse("MERMAID'S PENDANT", "Unbreakable", "Mermaid's Pendant", "", "ITEM EFFECTS: (Pendant)",
				"➟ Invisible to Monsters", "", "Equip a Pendant by placing in the", "bottom right slot of your inventory");
		assertEquals("minecraft:ender_eye", mermaid.icon());
		assertEquals("invis", mermaid.text());
	}

	/** A mob-drop talisman showed the emerald fallback (Michael 2026-10-04): match its name and small-caps lore too. */
	@Test void mobDropTalismansShowAMobWhateverTheWording() {
		assertEquals("minecraft:zombie_head", parse("Slayer Talisman", "Automatically sells your loot items").icon());
		assertEquals("minecraft:zombie_head", parse("Mob Drop Talisman", "Automatically sells items").icon());
		assertEquals("minecraft:zombie_head", parse("ᴛᴀʟɪꜱᴍᴀɴ", "Automatically sells ᴍᴏʙ ᴅʀᴏᴘꜱ").icon());
		assertEquals("minecraft:zombie_head", parse("Talisman", "Sells what monsters drop when killed").icon());
	}

	@Test void sellingTalismansAreDollarAndWhatTheySell() {
		Charm farm = parse("FARMING TALISMAN", "Farming Talisman", "", "Automatically sells harvested crops",
				"Total items sold: 48,631", "Total earned: $217,633.69", "");
		assertEquals(Charm.Kind.TALISMAN, farm.kind());
		assertEquals("minecraft:wheat", farm.icon());
		assertEquals("$217k", farm.text());
		Charm fish = parse("Fish Talisman", "", "Automatically sells fish to /fish");
		assertEquals("minecraft:cod", fish.icon());
		assertEquals("$", fish.text());
		Charm mobs = parse("Hunting Talisman", "Automatically sells mob drops", "Total earned: $1,234,567.00");
		assertEquals("minecraft:zombie_head", mobs.icon());
		assertEquals("minecraft:soul_lantern", parse("Soul Talisman", "Automatically sells mob drops for souls").icon());
		assertEquals("$1.2M", mobs.text());
	}

	@Test void otherItemsAreNotCharms() {
		assertEquals(Optional.empty(), Charm.parse("Diamond Hoe", List.of()));
		assertEquals(Optional.empty(), Charm.parse("Sell Booster", List.of("Drink this potion to receive a")));
		assertEquals(Optional.empty(), Charm.parse(null, null));
	}

	@Test void inventoryMeterColoursByFill() {
		assertEquals(InvMeter.Level.OK, InvMeter.level(20, 36));
		assertEquals(InvMeter.Level.HIGH, InvMeter.level(29, 36)); // 80%+
		assertEquals(InvMeter.Level.FULL, InvMeter.level(36, 36));
	}
}
