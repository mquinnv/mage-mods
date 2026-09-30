package net.mage.cubewheel.hud;

import java.util.List;

/**
 * One small HUD panel: a gold title and coloured lines, drawn at a corner + offset (see {@link HudLayout}).
 * Pure: no Minecraft/Fabric imports.
 */
public record Panel(String title, List<Line> lines, HudLayout.Corner corner, int x, int y) {
	public static final int WHITE = 0xFFFFFFFF;
	public static final int YELLOW = 0xFFFFFF55;
	public static final int GRAY = 0xFFAAAAAA;
	public static final int GREEN = 0xFF55FF55;

	public record Line(String text, int color) {}
}
