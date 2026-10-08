package net.mage.cubewheel.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.mage.cubewheel.hud.Panel;
import org.junit.jupiter.api.Test;

class StatusTest {
	private static final List<String> DRAGON_LORE = List.of("§6Dragon Armor Set:", "§7➤ Drops 2x more heads",
			"§7➤ Permanent Speed II Effect", "§e(Requires 4/4 pieces)");

	private static List<List<String>> lores(int pieces, List<String> lore) {
		List<List<String>> out = new ArrayList<>();
		for (int i = 0; i < 4; i++) out.add(i < pieces ? lore : List.of());
		return out;
	}

	@Test void setBonusMetWhenAllFourWorn() {
		ArmorSet s = ArmorSet.of(List.of("DRAGON HELMET", "DRAGON CHESTPLATE", "DRAGON LEGGINGS", "DRAGON BOOTS"),
				lores(4, DRAGON_LORE));
		assertEquals(4, s.required());
		assertFalse(s.bonusUnmet());
	}

	@Test void setBonusUnmetAtThreeOfFour() {
		ArmorSet s = ArmorSet.of(Arrays.asList("DRAGON HELMET", "DRAGON CHESTPLATE", "DRAGON LEGGINGS", null),
				lores(3, DRAGON_LORE));
		assertTrue(s.bonusUnmet());
	}

	@Test void requirementOnAnyPieceOfTheShownSetCounts() {
		ArmorSet s = ArmorSet.of(Arrays.asList("DRAGON HELMET", "DRAGON CHESTPLATE", "DRAGON LEGGINGS", null),
				Arrays.asList(List.of(), List.of(), DRAGON_LORE, List.of()));
		assertTrue(s.bonusUnmet());
	}

	@Test void noRequirementLineMeansNoBonus() {
		List<String> plain = List.of("§7Just armor", "§7Protection IV");
		ArmorSet s = ArmorSet.of(Arrays.asList("DRAGON HELMET", "DRAGON CHESTPLATE", null, null), lores(2, plain));
		assertEquals(0, s.required());
		assertFalse(s.bonusUnmet());
		assertFalse(ArmorSet.of(List.of("DRAGON HELMET")).bonusUnmet());
	}

	@Test void twoOfFourRequirementMetWithTwoWorn() {
		List<String> lore = List.of("Wolf Armor Set:", "(Requires 2/4 pieces)");
		ArmorSet s = ArmorSet.of(Arrays.asList("WOLF HELMET", "WOLF BOOTS", null, null), lores(2, lore));
		assertEquals(2, s.required());
		assertFalse(s.bonusUnmet());
	}

	@Test void requirementOfAnotherSetIsIgnored() {
		// Shown set is Dragon (2 pieces); the lone Wolf piece's requirement must not apply to it.
		ArmorSet s = ArmorSet.of(Arrays.asList("DRAGON HELMET", "DRAGON BOOTS", "WOLF LEGGINGS", null),
				Arrays.asList(List.of(), List.of(), List.of("(Requires 4/4 pieces)"), List.of()));
		assertEquals("Dragon", s.name());
		assertFalse(s.bonusUnmet());
	}

	@Test void fullCustomSet() {
		ArmorSet s = ArmorSet.of(List.of("PHOENIX HELMET", "PHOENIX CHESTPLATE", "PHOENIX LEGGINGS", "PHOENIX BOOTS"));
		assertEquals("Phoenix", s.name());
		assertEquals("4/4", s.count());
	}

	@Test void mixedAndPartialSets() {
		ArmorSet s = ArmorSet.of(Arrays.asList("Diamond Helmet", "Netherite Chestplate", "Diamond Leggings", null));
		assertEquals("Diamond", s.name());
		assertEquals(2, s.matching());
		assertEquals(3, s.worn());
		assertEquals(ArmorSet.NONE, ArmorSet.of(Arrays.asList(null, null, "", null)));
		assertEquals("Mana", ArmorSet.base("§bMana Boots of Speed"));
		assertEquals("Golden", ArmorSet.base("✦ GOLDEN HELMET ✦"));
	}

	/** Fish gear gains "MYTHICAL" at level 2 (Michael 2026-10-07): "MYTHICAL HUNTER BOOTS" still belongs to Hunter. */
	@Test void levelledMythicalPieceStaysInItsSet() {
		ArmorSet s = ArmorSet.of(List.of("MYTHICAL HUNTER BOOTS", "HUNTER LEGGINGS", "HUNTER CHESTPLATE", "HUNTER HELMET"));
		assertEquals("Hunter", s.name());
		assertEquals(4, s.matching());
		assertEquals(4, s.worn());
		assertEquals("Hunter", ArmorSet.base("§dMythical Hunter Helmet"));
	}

	/** "Legendary" is a real set name, not a level prefix: it is kept. */
	@Test void legendaryIsASetNameNotALevel() {
		ArmorSet s = ArmorSet.of(List.of("LEGENDARY HELMET"));
		assertEquals("Legendary", s.name());
		assertEquals("Mythicalist", ArmorSet.base("MYTHICALIST BOOTS")); // only the whole word is a level
	}

	@Test void formats() {
		assertEquals("6:00", StatusFormat.gameTime(0));
		assertEquals("12:00", StatusFormat.gameTime(6_000));
		assertEquals("15:00", StatusFormat.gameTime(9_000));
		assertEquals("14:20", StatusFormat.gameTime(8_334));
		assertEquals("0:00", StatusFormat.gameTime(18_000));
		assertEquals("6:00", StatusFormat.gameTime(24_000 * 5));
		assertEquals("4:35p", StatusFormat.clock(16, 35));
		assertEquals("5:09p", StatusFormat.clock(17, 9));
		assertEquals("12:00a", StatusFormat.clock(0, 0));
		assertEquals("12:30p", StatusFormat.clock(12, 30));
		assertEquals("Dark Forest", StatusFormat.biome("minecraft:dark_forest"));
		assertEquals("4.3 b/s", StatusFormat.speed(4.32));
		assertEquals("0.0 b/s", StatusFormat.speed(-1));
	}

	@Test void coordinatesArePlainNumbers() {
		assertEquals("123  64  -456", StatusFormat.coords(123, 64, -456));
		assertEquals("0  -60  0", StatusFormat.coords(0, -60, 0));
	}

	@Test void facingIsALetterAndTheAxisItPointsAlong() {
		assertEquals("N -Z", StatusFormat.facing("north"));
		assertEquals("S +Z", StatusFormat.facing("south"));
		assertEquals("E +X", StatusFormat.facing("EAST"));
		assertEquals("W -X", StatusFormat.facing("west"));
		assertEquals("up", StatusFormat.facing("up"));
		assertEquals("", StatusFormat.facing(null));
	}

	@Test void wearColourByRemainingDurability() {
		assertEquals(StatusFormat.WORN_OK, StatusFormat.wearColor(1));
		assertEquals(StatusFormat.WORN_OK, StatusFormat.wearColor(0.25));
		assertEquals(0xFFFFFF55, StatusFormat.wearColor(0.24));
		assertEquals(0xFFFFFF55, StatusFormat.wearColor(0.10));
		assertEquals(0xFFFF5555, StatusFormat.wearColor(0.09));
		assertEquals(0xFFFF5555, StatusFormat.wearColor(0));
	}

	// Set bonus text, from real ManaCube lore (cubewheel-captures 2026-09-30/2026-10-01 jsonl, kind "container",
	// items[].lore; formatting codes are already stripped there).

	/** Snowy Helmet (2026-10-01): the bonus is the "FULL SET EFFECTS:" block, after its "(Requires 4/4 pieces)". */
	private static final List<String> SNOWY_HELMET = List.of("Unbreakable", "Snowy Helmet", "",
			"ITEM EFFECTS: (When Attacked)", "➟ Spawn Arctic Fox Pack", "", "Duration: 45s", "Cooldown: 120s", "",
			"FULL SET EFFECTS:", "(Requires 4/4 pieces)", "➟ -50% Armor Effect Cooldowns", "➟ Snowy Particles", "",
			"Season Vault Access ✔", "");

	/** Obsidian Chestplate (2026-09-30): older style, the effects listed right above "(Requires 4/4 pieces)". */
	private static final List<String> OBSIDIAN_CHESTPLATE = List.of("Obsidian Armor Chestplate", "",
			"ITEM EFFECTS: (When Worn)", "➤ + Strength II", "➤ + Fire Resistance", "➤ Mining Fatigue", "➤ Slowness",
			"(Requires 4/4 pieces)");

	/** Morend Leggings (2026-10-01): a full-set block with no requirement line, one effect wrapped onto two lines. */
	private static final List<String> MOREND_LEGGINGS = List.of("Unbreakable", "Morend Leggings", "",
			"FULL SET EFFECTS: (While Worn)", "➟ 5% Chance to get 3x drops from", "   World Monsters & Resources",
			"➟ Speed V in Worlds", "➟ Strength II", "");

	/** Dragonscale Leggings (2026-10-01): the piece's own ITEM EFFECTS are not the set bonus. */
	private static final List<String> DRAGONSCALE_LEGGINGS = List.of("Unbreakable", "Dragonscale Leggings Red", "",
			"ITEM EFFECTS: (When Worn)", "➟ +20% Extra Damage to Tangleroot", "   & Sandara Monsters", "",
			"FULL SET EFFECTS: (When Worn)", "➟ +20% MCMMO Boost", "", "Season Vault Access ✔", "HOLY PROTECTION",
			"Signed by Goreguttss", "");

	/** Warden Helmet (2026-10-01): "(Full Set Required)" instead of a piece count. */
	private static final List<String> WARDEN_HELMET = List.of("Unbreakable", "Warden Helmet", "", "FULL SET EFFECTS:",
			"(Full Set Required)", "➟ Absorption II", "➟ Speed IV in Resource World",
			"➟ 30% MCMMO Boost in Resource World", "");

	/** Dragon Boots (2026-10-01): "Dragon Armor Set:" then the effects, then the requirement. */
	private static final List<String> DRAGON_BOOTS = List.of("○ Speed III", "o <9 Available>", "", "Dragon Armor Set:",
			"➤ Drops 2x more heads", "➤ Permanent Speed II Effect", "(Requires 4/4 pieces)", "",
			"Exclusive Chinese New Year Crate", "Season Vault Access ✔");

	/** Pharaoh's Helmet (2026-10-01): an item ability, then the set bonus. */
	private static final List<String> PHARAOH_HELMET = List.of("Unbreakable", "Pharaoh's Helmet", "",
			"ITEM EFFECTS: (Shift While Wearing)", "➟ Heal Players Around You", "Duration 15s", "Radius: 5",
			"Cooldown 60s", "", "FULL SET EFFECTS: (When Worn)", "➟ 15% Bonus Damage to Sandara Monsters",
			"➟ +2 Hearts", "");

	@Test void bonusFromAFullSetEffectsBlock() {
		assertEquals("-50% Armor Effect Cooldowns · Snowy Particles", ArmorSet.bonus(SNOWY_HELMET));
		assertEquals("+20% MCMMO Boost", ArmorSet.bonus(DRAGONSCALE_LEGGINGS));
		assertEquals("Absorption II · Speed IV in Resource World · 30% MCMMO Boost in Resource World",
				ArmorSet.bonus(WARDEN_HELMET));
		assertEquals("15% Bonus Damage to Sandara Monsters · +2 Hearts", ArmorSet.bonus(PHARAOH_HELMET));
	}

	@Test void aWrappedEffectIsJoinedIntoOne() {
		assertEquals("5% Chance to get 3x drops from World Monsters & Resources · Speed V in Worlds · Strength II",
				ArmorSet.bonus(MOREND_LEGGINGS));
	}

	@Test void bonusFromTheEffectsRightAboveTheRequirement() {
		assertEquals("+Strength II · +Fire Resistance · Mining Fatigue · Slowness", ArmorSet.bonus(OBSIDIAN_CHESTPLATE));
		assertEquals("Drops 2x more heads · Permanent Speed II Effect", ArmorSet.bonus(DRAGON_BOOTS));
		assertEquals(4, ArmorSet.requirement(DRAGON_BOOTS));
	}

	@Test void noStatedBonusGivesNothing() {
		assertEquals("", ArmorSet.bonus(List.of("Unbreakable", "Giant Boots", "", "Only obtainable by killing Bosses")));
		assertEquals("", ArmorSet.bonus(List.of("ITEM EFFECTS: (When Worn)", "➟ Speed IV", "")));
		assertEquals("", ArmorSet.bonus(null));
		assertEquals("", ArmorSet.bonus(List.of()));
	}

	@Test void theShownSetCarriesItsBonus() {
		ArmorSet s = ArmorSet.of(Arrays.asList("SNOWY HELMET", "SNOWY CHESTPLATE", "SNOWY LEGGINGS", null),
				Arrays.asList(List.of(), SNOWY_HELMET, List.of(), List.of()));
		assertEquals("Snowy", s.name());
		assertEquals("-50% Armor Effect Cooldowns · Snowy Particles", s.bonus());
		assertTrue(s.bonusUnmet());
		// Another set's bonus does not label the shown one.
		ArmorSet t = ArmorSet.of(Arrays.asList("DRAGON HELMET", "DRAGON BOOTS", "SNOWY LEGGINGS", null),
				Arrays.asList(List.of(), List.of(), SNOWY_HELMET, List.of()));
		assertEquals("", t.bonus());
		assertEquals("", ArmorSet.NONE.bonus());
	}

	@Test void aFullSetHeadingWithoutACountNeedsAllFourPieces() {
		assertEquals(4, ArmorSet.requirement(WARDEN_HELMET));     // "(Full Set Required)"
		assertEquals(4, ArmorSet.requirement(MOREND_LEGGINGS));   // "FULL SET EFFECTS: (While Worn)"
		assertEquals(4, ArmorSet.requirement(PHARAOH_HELMET));    // "FULL SET EFFECTS: (When Worn)"
		// Hunter Chestplate (2026-10-01): no colon on the heading.
		assertEquals(4, ArmorSet.requirement(List.of("Unbreakable", "Hunter Chestplate", "",
				"FULL SET EFFECTS (While Worn)", "➟ Strength II", "➟ Take -10% less Damage", "➟ Invisible to Monsters", "")));
		// Velociraptor Boots (2026-10-01) names its two pieces: the count is not guessed.
		assertEquals(0, ArmorSet.requirement(List.of("Velociraptor Boots", "", "FULL SET EFFECTS: (Helmet + Boots)",
				"➟ Strength II", "")));
		assertEquals(4, ArmorSet.requirement(SNOWY_HELMET));      // a stated count still wins
		ArmorSet w = ArmorSet.of(Arrays.asList("WARDEN HELMET", "WARDEN CHESTPLATE", "WARDEN LEGGINGS", null),
				Arrays.asList(WARDEN_HELMET, List.of(), List.of(), List.of()));
		assertEquals(4, w.required());
		assertTrue(w.bonusUnmet());
		ArmorSet full = ArmorSet.of(List.of("MOREND HELMET", "MOREND CHESTPLATE", "MOREND LEGGINGS", "MOREND BOOTS"),
				Arrays.asList(List.of(), List.of(), MOREND_LEGGINGS, List.of()));
		assertFalse(full.bonusUnmet());
	}

	@Test void aWearBarOnlyWhileWearingOutAndNeverWhenUnbreakable() {
		assertEquals(-1, StatusFormat.wear(1.0, false));
		assertEquals(-1, StatusFormat.wear(StatusFormat.WEAR_LOW, false));
		assertEquals(0.2, StatusFormat.wear(0.2, false), 1e-9);
		assertEquals(0.0, StatusFormat.wear(0.0, false), 1e-9);
		assertEquals(-1, StatusFormat.wear(0.05, true));
		assertEquals(-1, StatusFormat.wear(-1, false)); // takes no damage
	}

	@Test void unbreakableFromTheLore() {
		// Snowy Helmet's and the Season 10 Challenge Sword's lore (cubewheel-captures 2026-10-01).
		assertTrue(StatusFormat.unbreakableLore(SNOWY_HELMET));
		assertTrue(StatusFormat.unbreakableLore(List.of("Mob Kills: 30", "○ Stasis III", "Unbreakable", "Season 10 Challenge Sword")));
		assertFalse(StatusFormat.unbreakableLore(DRAGON_BOOTS));
		assertFalse(StatusFormat.unbreakableLore(null));
	}

	// The set bonus row as pieces (Michael 2026-10-07): the Warden set's "Speed IV in Resource World" and
	// "30% MCMMO Boost in Resource World" only apply in the Resource World (dimension minecraft:resource_world).

	@Test void effectsAreTheBonusSplitOnItsJoin() {
		assertEquals(List.of("Absorption II", "Speed IV in Resource World", "30% MCMMO Boost in Resource World"),
				ArmorSet.effects(ArmorSet.bonus(WARDEN_HELMET)));
		assertEquals(List.of("+20% MCMMO Boost"), ArmorSet.effects("+20% MCMMO Boost"));
		assertEquals(List.of(), ArmorSet.effects(""));
		assertEquals(List.of(), ArmorSet.effects(null));
	}

	@Test void resourceWorldOnlyEffects() {
		assertTrue(ArmorSet.resourceWorldOnly("Speed IV in Resource World"));
		assertTrue(ArmorSet.resourceWorldOnly("30% MCMMO Boost in Resource World"));
		assertTrue(ArmorSet.resourceWorldOnly("Speed IV in the Resource World"));
		assertTrue(ArmorSet.resourceWorldOnly("Speed IV IN RESOURCE WORLD  ")); // case and trailing spaces
		assertFalse(ArmorSet.resourceWorldOnly("Absorption II"));
		assertFalse(ArmorSet.resourceWorldOnly("Speed V in Worlds")); // other qualifiers are left alone
		assertFalse(ArmorSet.resourceWorldOnly("Resource World Speed IV"));
		assertFalse(ArmorSet.resourceWorldOnly(""));
		assertFalse(ArmorSet.resourceWorldOnly(null));
	}

	@Test void bonusPiecesDimResourceWorldEffectsOutsideIt() {
		String bonus = ArmorSet.bonus(WARDEN_HELMET);
		int lit = 1, dim = 2;
		List<Panel.Piece> outside = StatusFormat.bonusPieces(bonus, true, false, lit, dim);
		assertEquals(List.of("Absorption II", "·", "Speed IV in Resource World", "·", "30% MCMMO Boost in Resource World"),
				outside.stream().map(Panel.Piece::text).toList());
		assertEquals(List.of(lit, lit, dim, lit, dim), outside.stream().map(Panel.Piece::color).toList());
		// In the Resource World every effect is lit.
		assertEquals(List.of(lit, lit, lit, lit, lit),
				StatusFormat.bonusPieces(bonus, true, true, lit, dim).stream().map(Panel.Piece::color).toList());
		// A bonus that is not on stays dim throughout, wherever you are.
		assertEquals(List.of(dim, dim, dim, dim, dim),
				StatusFormat.bonusPieces(bonus, false, true, lit, dim).stream().map(Panel.Piece::color).toList());
		// "in Worlds" is not the Resource World qualifier: lit when on.
		assertEquals(List.of(lit, lit, lit, lit, lit),
				StatusFormat.bonusPieces(ArmorSet.bonus(MOREND_LEGGINGS), true, false, lit, dim).stream()
						.map(Panel.Piece::color).toList());
		assertTrue(StatusFormat.bonusPieces("", true, true, lit, dim).isEmpty());
	}
}
