package net.mage.cubewheel.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.config.ConfigStore;
import java.io.IOException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Move CubeWheel's HUD panels with the mouse: every panel gets an outline, dragging one moves it and makes its
 * position "custom" (exact coordinates, saved to cubewheel.json on release). R puts every panel back on its default
 * corner; Esc closes. The panels themselves are drawn by {@link PanelsHud} behind this screen, live.
 */
public final class ArrangeScreen extends Screen {
	private static final int OUTLINE = 0xC0FFFFFF;
	private static final int OUTLINE_HOT = 0xFFFFD75E;
	private static final int HINT = 0xFFDDDDDD;

	/** The source being dragged, or -1; and where in its box it was grabbed. */
	private int dragging = -1;
	private int grabX, grabY;

	public ArrangeScreen() {
		super(Component.literal("Move panels"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		// No dimming: the panels behind must be seen as they really look.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractRenderState(g, mouseX, mouseY, partial);
		for (PanelsHud.Placed p : PanelsHud.lastPlaced()) {
			HudLayout.Box b = p.box();
			boolean hot = p.source() == dragging || (dragging < 0 && contains(b, mouseX, mouseY));
			g.outline(b.x() - 1, b.y() - 1, b.w() + 2, b.h() + 2, hot ? OUTLINE_HOT : OUTLINE);
		}
		g.centeredText(font, "Drag a panel to move it  ·  R: all back to their corners  ·  Esc: done", width / 2, 4, HINT);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
		// The last drawn panel is on top: search from the end.
		var placed = PanelsHud.lastPlaced();
		for (int k = placed.size() - 1; k >= 0; k--) {
			HudLayout.Box b = placed.get(k).box();
			if (contains(b, event.x(), event.y())) {
				dragging = placed.get(k).source();
				grabX = (int) event.x() - b.x();
				grabY = (int) event.y() - b.y();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging < 0) return super.mouseDragged(event, dx, dy);
		CubeWheelConfig.Position p = PanelsHud.position(dragging);
		p.corner = HudLayout.Corner.CUSTOM.id();
		p.x = Math.max(0, (int) event.x() - grabX);
		p.y = Math.max(0, (int) event.y() - grabY);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging >= 0) {
			dragging = -1;
			save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_R) {
			for (int i = 0; i < PanelsHud.sourceCount(); i++) {
				CubeWheelConfig.Position p = PanelsHud.position(i), d = PanelsHud.defaultPosition(i);
				p.corner = d.corner;
				p.x = d.x;
				p.y = d.y;
			}
			save();
			return true;
		}
		return super.keyPressed(event);
	}

	private static boolean contains(HudLayout.Box b, double x, double y) {
		return x >= b.x() && x < b.x() + b.w() && y >= b.y() && y < b.y() + b.h();
	}

	/** Writes cubewheel.json, unless the file failed to load (then the change stays in memory only). */
	private static void save() {
		ConfigStore store = CubeWheelClient.config();
		if (!store.lastLoadOk()) return;
		try {
			store.save();
		} catch (IOException e) {
			CubeWheelClient.LOG.warn("[cubewheel] could not save panel positions: {}", e.toString());
		}
	}
}
