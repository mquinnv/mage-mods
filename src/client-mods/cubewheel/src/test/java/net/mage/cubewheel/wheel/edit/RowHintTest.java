package net.mage.cubewheel.wheel.edit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.mage.cubewheel.config.WheelNode;
import org.junit.jupiter.api.Test;

class RowHintTest {
	private static String hintOf(WheelNode top, int row) {
		return RowHint.of(Row.rows(List.of(top)).get(row));
	}

	@Test
	void aLeafShowsItsCommand() {
		assertEquals("/spawn", hintOf(WheelNode.leaf("Spawn", null, "/spawn"), 0));
	}

	@Test
	void anArcEntryIsMarkedAndShowsItsCommand() {
		WheelNode isles = WheelNode.leaf("Isles", null, "/is").withArc(WheelNode.leaf("A", null, "/warp a"));
		assertEquals("/is", hintOf(isles, 0));
		assertEquals("arc entry: /warp a", hintOf(isles, 1));
	}

	@Test
	void ringsAndLiveEntriesSayWhatTheyAre() {
		assertEquals("ring", hintOf(WheelNode.ring("R", null), 0));
		assertEquals("ring, fans out", hintOf(WheelNode.ring("R", null).shownAsArc(), 0));
		assertEquals("live: homes, fans out", hintOf(WheelNode.dynamic("Homes", null, "homes").shownAsArc(), 0));
		assertEquals("live: boss", hintOf(WheelNode.slice("Boss", null, "boss"), 0));
	}

	@Test
	void aMissingCommandAndAnOuterTierAreNoted() {
		assertEquals("(no command)", hintOf(WheelNode.leaf("X", null, " "), 0));
		WheelNode crops = WheelNode.leaf("Crops", null, "/crops").withOuter(WheelNode.leaf("Spawners", null, "/sp"));
		assertEquals("/crops + outer tier", hintOf(crops, 0));
	}
}
