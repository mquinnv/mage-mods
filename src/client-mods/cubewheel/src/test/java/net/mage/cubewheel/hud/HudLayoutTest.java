package net.mage.cubewheel.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HudLayoutTest {
	@Test void cornersParseLeniently() {
		assertEquals(HudLayout.Corner.TOP_LEFT, HudLayout.Corner.parse(null));
		assertEquals(HudLayout.Corner.TOP_LEFT, HudLayout.Corner.parse("nonsense"));
		assertEquals(HudLayout.Corner.BOTTOM_RIGHT, HudLayout.Corner.parse(" Bottom-Right "));
		assertEquals(HudLayout.Corner.TOP_RIGHT, HudLayout.Corner.parse("top_right"));
		assertEquals(HudLayout.Corner.BOTTOM_LEFT, HudLayout.Corner.parse("bottom left"));
	}

	@Test void bottomCentreSitsCentredAboveTheHotbarAndStacksUp() {
		assertEquals(HudLayout.Corner.BOTTOM_CENTER, HudLayout.Corner.parse("bottom_center"));
		HudLayout l = new HudLayout(400, 300);
		HudLayout.Box a = l.place(HudLayout.Corner.BOTTOM_CENTER, 0, 52, 100, 20);
		assertEquals(new HudLayout.Box(150, 300 - 52 - 20, 100, 20), a);
		HudLayout.Box b = l.place(HudLayout.Corner.BOTTOM_CENTER, 10, 52, 60, 10); // offX nudges right of centre
		assertEquals(170 + 10, b.x());
		assertEquals(a.y() - HudLayout.GAP - 10, b.y());
	}

	@Test void customIsExactlyWhereItWasDroppedAndStacksWithNothing() {
		HudLayout l = new HudLayout(400, 300);
		assertEquals(new HudLayout.Box(120, 80, 50, 20), l.place(HudLayout.Corner.CUSTOM, 120, 80, 50, 20));
		assertEquals(new HudLayout.Box(120, 80, 50, 20), l.place(HudLayout.Corner.CUSTOM, 120, 80, 50, 20)); // no stacking
		assertEquals(new HudLayout.Box(4, 4, 50, 20), l.place(HudLayout.Corner.TOP_LEFT, 4, 4, 50, 20)); // corners unaffected
		assertEquals(new HudLayout.Box(350, 280, 50, 20), l.place(HudLayout.Corner.CUSTOM, 999, 999, 50, 20)); // kept on screen
	}

	@Test void topLeftPanelsStackDownwards() {
		HudLayout l = new HudLayout(400, 300);
		HudLayout.Box a = l.place(HudLayout.Corner.TOP_LEFT, 4, 4, 50, 20);
		HudLayout.Box b = l.place(HudLayout.Corner.TOP_LEFT, 4, 4, 80, 30);
		assertEquals(new HudLayout.Box(4, 4, 50, 20), a);
		assertEquals(4 + 20 + HudLayout.GAP, b.y());
		assertEquals(4, b.x());
	}

	@Test void rightAndBottomCornersMeasureFromTheirEdges() {
		HudLayout l = new HudLayout(400, 300);
		HudLayout.Box a = l.place(HudLayout.Corner.BOTTOM_RIGHT, 4, 10, 50, 20);
		assertEquals(new HudLayout.Box(400 - 4 - 50, 300 - 10 - 20, 50, 20), a);
		HudLayout.Box b = l.place(HudLayout.Corner.BOTTOM_RIGHT, 4, 10, 60, 10);
		assertEquals(a.y() - HudLayout.GAP - 10, b.y()); // stacks upwards
	}

	@Test void reservedSpaceKeepsPanelsOffTheTrackerHud() {
		HudLayout l = new HudLayout(400, 300);
		l.reserve(HudLayout.Corner.TOP_RIGHT, 120); // tracker HUD ends at y=120
		HudLayout.Box a = l.place(HudLayout.Corner.TOP_RIGHT, 4, 4, 50, 20);
		assertEquals(120 + HudLayout.GAP, a.y());
		HudLayout.Box b = l.place(HudLayout.Corner.TOP_LEFT, 4, 4, 50, 20);
		assertEquals(4, b.y()); // other corners unaffected
	}

	@Test void largerOffsetWins() {
		HudLayout l = new HudLayout(400, 300);
		l.place(HudLayout.Corner.TOP_LEFT, 4, 4, 50, 10);
		HudLayout.Box b = l.place(HudLayout.Corner.TOP_LEFT, 4, 100, 50, 10);
		assertEquals(100, b.y());
	}

	@Test void boxesStayOnScreen() {
		HudLayout l = new HudLayout(100, 50);
		HudLayout.Box a = l.place(HudLayout.Corner.TOP_LEFT, 500, 500, 40, 20);
		assertEquals(60, a.x());
		assertEquals(30, a.y());
	}
}
