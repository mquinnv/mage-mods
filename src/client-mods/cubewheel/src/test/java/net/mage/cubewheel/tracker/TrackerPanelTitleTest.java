package net.mage.cubewheel.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The quests panel names itself and the key that opens its picker (Michael 2026-10-08). */
class TrackerPanelTitleTest {
	@Test void titleCarriesThePickerKeyWhenBound() {
		assertEquals("Quests", TrackerPanelModel.TITLE);
		assertEquals("Quests [X]", TrackerPanelModel.panelTitle("X"));
		assertEquals("Quests [Left Shift]", TrackerPanelModel.panelTitle(" Left Shift "));
		assertEquals("Quests", TrackerPanelModel.panelTitle(null));
		assertEquals("Quests", TrackerPanelModel.panelTitle(" "));
	}
}
