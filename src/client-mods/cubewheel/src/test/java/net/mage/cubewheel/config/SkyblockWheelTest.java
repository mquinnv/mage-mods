package net.mage.cubewheel.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.mage.cubewheel.sidebar.Gamemode;
import org.junit.jupiter.api.Test;

class SkyblockWheelTest {
	private static final Gson GSON = new Gson();

	private static List<String> commands(List<WheelNode> wheel) {
		return WheelUpgrade.walk(wheel).stream().map(n -> n.command).filter(c -> c != null).toList();
	}

	@Test void theSkyblockWheelHoldsTheIslandWorldsAndProgressCommands() {
		List<String> cmds = commands(DefaultConfig.skyblockWheel());
		for (String c : List.of("/is go", "/is home", "/is homes", "/is vault", "/is upgrades", "/is quests", "/is team",
				"/worlds", "/warp wolfhaven", "/warp tangleroots", "/warp sandara", "/warp morend", "/warp icehaven",
				"/spawn", "/warp boss", "/warp pond", "/warp quests", "/warp enchanter", "/warp crates",
				"/jobs", "/taskshop", "/prestige", "/challenges", "/challengeshop", "/collection", "/lb",
				"/pv", "/vault", "/ec", "/ah", "/shop", "/cubitshop", "/trade", "cubewheel:settings")) {
			assertTrue(cmds.contains(c), "missing " + c);
		}
	}

	@Test void everyLeafCommandIsAServerCommandOrAClientAction() {
		for (WheelNode n : WheelUpgrade.walk(DefaultConfig.skyblockWheel())) {
			if (n.command != null) {
				assertTrue(n.command.startsWith("/") || n.command.equals("cubewheel:settings"), n.command);
			}
		}
	}

	@Test void theSkyblockWheelKeepsTheVaultsNodeAndSettingsLeaf() {
		List<WheelNode> all = WheelUpgrade.walk(DefaultConfig.skyblockWheel());
		assertEquals(1, all.stream().filter(n -> "vaults".equals(n.dynamic)).count());
		assertEquals(1, all.stream().filter(n -> "cubewheel:settings".equals(n.command)).count());
	}

	@Test void theSkyblockWheelHasNoSurvivalOnlyCommands() {
		Set<String> cmds = Set.copyOf(commands(DefaultConfig.skyblockWheel()));
		for (String c : List.of("/pquests", "/cow", "/isles", "/warp crops")) assertFalse(cmds.contains(c), c);
	}

	@Test void aFreshConfigCarriesBothWheels() {
		CubeWheelConfig c = DefaultConfig.create();
		assertNotNull(c.wheelSkyblock);
		assertEquals(GSON.toJson(DefaultConfig.skyblockWheel()), GSON.toJson(c.wheelSkyblock));
	}

	@Test void anOldConfigGetsASkyblockWheelAndKeepsItsOwn() {
		CubeWheelConfig c = DefaultConfig.create();
		c.wheel = new ArrayList<>(List.of(WheelNode.leaf("Mine", null, "/mine")));
		c.wheelSkyblock = null;
		ConfigNormalizer.normalize(c, new ArrayList<>());
		assertEquals(GSON.toJson(DefaultConfig.skyblockWheel()), GSON.toJson(c.wheelSkyblock));
		assertEquals(1, c.wheel.size());
		assertEquals("/mine", c.wheel.get(0).command);
	}

	@Test void wheelForPicksSkyblockOnlyInSkyblock() {
		CubeWheelConfig c = DefaultConfig.create();
		assertSame(c.wheelSkyblock, c.wheelFor(Gamemode.SKYBLOCK));
		assertSame(c.wheel, c.wheelFor(Gamemode.OTHER));
		assertSame(c.wheel, c.wheelFor(Gamemode.SURVIVAL));
	}

	@Test void setWheelForWritesTheMatchingList() {
		CubeWheelConfig c = DefaultConfig.create();
		List<WheelNode> a = new ArrayList<>(), b = new ArrayList<>();
		c.setWheelFor(Gamemode.SKYBLOCK, a);
		c.setWheelFor(Gamemode.OTHER, b);
		assertSame(a, c.wheelSkyblock);
		assertSame(b, c.wheel);
		c.setWheelFor(Gamemode.SURVIVAL, a);
		assertSame(a, c.wheel);
	}
}
