package com.mage.cubewheel.wheel;

import com.mage.cubewheel.CommandSender;
import com.mage.cubewheel.CubeWheelClient;
import com.mage.cubewheel.ServerGate;
import com.mage.cubewheel.config.CubeWheelConfig;
import com.mage.cubewheel.config.WheelNode;
import com.mage.cubewheel.homes.HomesCache;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Hold-to-open radial command wheel. Release or left-click commits; right-click goes back. */
public final class RadialScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFAAAAAA;

	/** Resolves a ring/dynamic node's children. Replaceable so the homes feature can hook fetching in. */
	public static Function<WheelNode, List<WheelNode>> childrenProvider = RadialScreen::defaultChildren;

	private final Deque<WheelNode> path = new ArrayDeque<>(); // root first, current ring last
	private final KeyMapping holdKey;
	private List<WheelNode> entries;
	private int hovered = -1;
	private boolean keyStillHeld;

	public RadialScreen(WheelNode root, KeyMapping holdKey) {
		super(Component.literal(root.label == null ? "" : root.label));
		this.holdKey = holdKey;
		this.keyStillHeld = holdKey != null && canPoll(KeyMappingHelper.getBoundKeyOf(holdKey));
		path.addLast(root);
		entries = resolve(root);
	}

	/** Default resolver: vault count from config, homes from the per-server cache (none if not wired). */
	public static List<WheelNode> defaultChildren(WheelNode node) {
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		HomesCache homes = CubeWheelClient.homes();
		List<String> names = homes == null ? List.of() : ServerGate.currentHost().map(homes::get).orElse(List.of());
		return WheelResolver.children(node, cfg.vaultCount, names);
	}

	/** childrenProvider with failures contained: a broken provider yields an empty ring, not a crash. */
	static List<WheelNode> resolve(WheelNode node) {
		try {
			List<WheelNode> out = childrenProvider.apply(node);
			return out == null ? List.of() : out;
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] resolving children of '{}' failed", node.label, e);
			return List.of();
		}
	}

	/**
	 * Re-resolves the current ring (e.g. when homes arrive). A sub-ring that has grown past
	 * listThreshold is handed over to a ListScreen, exactly as activate() would have done.
	 */
	public void refresh() {
		WheelNode top = path.peekLast();
		List<WheelNode> children = resolve(top);
		hovered = -1;
		if (path.size() > 1 && children.size() > listThreshold() && minecraft.gui.screen() == this) {
			path.removeLast();
			entries = resolve(path.peekLast());
			openList(top, children);
			return;
		}
		entries = children;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		g.fill(0, 0, width, height, 0x66000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractRenderState(g, mouseX, mouseY, partial);
		int cx = width / 2;
		int cy = height / 2;
		double r = radius();
		hovered = RadialMath.sliceAt(mouseX - cx, mouseY - cy, entries.size(), r * 0.25);
		for (int i = 0; i < entries.size(); i++) {
			WheelNode node = entries.get(i);
			double[] o = RadialMath.offset(RadialMath.sliceCenterDegrees(i, entries.size()), r);
			int x = cx + (int) Math.round(o[0]);
			int y = cy + (int) Math.round(o[1]);
			boolean hot = i == hovered;
			if (hot) g.fill(x - 11, y - 11, x + 11, y + 11, 0x80FFFFFF);
			ItemStack icon = Icons.stack(node.icon);
			if (!icon.isEmpty()) g.item(icon, x - 8, y - 8);
			g.centeredText(font, node.label == null ? "" : node.label, x, y + 12, hot ? WHITE : GREY);
		}
		WheelNode current = path.peekLast();
		g.centeredText(font, current.label == null ? "" : current.label, cx, cy - font.lineHeight / 2, WHITE);
		int line = cy + font.lineHeight;
		if (path.size() > 1) {
			g.centeredText(font, "◀ back", cx, line, GREY);
			line += font.lineHeight + 2;
		}
		if (entries.isEmpty()) g.centeredText(font, "(empty)", cx, line, GREY);
	}

	@Override
	public void tick() {
		try {
			if (!keyStillHeld) return;
			if (!minecraft.isWindowActive()) {
				keyStillHeld = false; // focus loss is not a release: stay open in click mode
				return;
			}
			if (isHoldKeyDown()) return;
			keyStillHeld = false;
			if (hovered >= 0 && hovered < entries.size()) activate(entries.get(hovered));
			else onClose();
		} catch (RuntimeException e) {
			fail("tick", e);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		try {
			keyStillHeld = false; // any mouse-driven action ends hold mode; a later release must not commit
			int cx = width / 2;
			int cy = height / 2;
			double dx = event.x() - cx;
			double dy = event.y() - cy;
			double dead = radius() * 0.25;
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				int slice = RadialMath.sliceAt(dx, dy, entries.size(), dead);
				if (slice >= 0) activate(entries.get(slice));
				else if (Math.hypot(dx, dy) < dead) back();
				return true;
			}
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
				back();
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		} catch (RuntimeException e) {
			fail("mouseClicked", e);
			return true;
		}
	}

	private void activate(WheelNode node) {
		try {
			if (node.isLeaf()) {
				onClose();
				CommandSender.send(node.command);
				return;
			}
			if (!node.isRing() && !node.isDynamic()) return; // placeholder (e.g. "Loading…"): not actionable
			List<WheelNode> children = resolve(node);
			if (children.size() > listThreshold()) {
				openList(node, children);
				return;
			}
			path.addLast(node);
			entries = children;
			hovered = -1;
		} catch (RuntimeException e) {
			fail("activate", e);
		}
	}

	private void openList(WheelNode node, List<WheelNode> children) {
		keyStillHeld = false; // returning from the list must not look like a key release
		minecraft.gui.setScreen(new ListScreen(this, node.label, children));
	}

	/** Up one level (re-resolving that ring, which is cheap and rate-limited for homes); at the root, closes. */
	private void back() {
		if (path.size() <= 1) {
			onClose();
			return;
		}
		path.removeLast();
		refresh();
	}

	private void fail(String where, RuntimeException e) {
		CubeWheelClient.LOG.error("[cubewheel] radial wheel {} failed; closing", where, e);
		minecraft.gui.setScreen(null);
	}

	private static int listThreshold() {
		return CubeWheelClient.config().current().listThreshold;
	}

	private double radius() {
		return Math.max(60, Math.min(140, Math.min(width, height) * 0.30));
	}

	/** Keyboard keys and mouse buttons can be polled; scancode/unbound keys fall back to click mode. */
	private static boolean canPoll(InputConstants.Key key) {
		if (key.getValue() < 0) return false;
		return key.getType() == InputConstants.Type.KEYSYM || key.getType() == InputConstants.Type.MOUSE;
	}

	private boolean isHoldKeyDown() {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(holdKey);
		Window window = minecraft.getWindow();
		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
		}
		return InputConstants.isKeyDown(window, key.getValue());
	}
}
