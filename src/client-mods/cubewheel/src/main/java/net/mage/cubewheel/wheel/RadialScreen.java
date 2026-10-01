package net.mage.cubewheel.wheel;

import net.mage.cubewheel.CommandSender;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.config.WheelNode;
import net.mage.cubewheel.homes.HomesCache;
import net.mage.cubewheel.homes.HomesFetcher;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
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
	/** Widest gap between neighbouring entries of a sub-ring, in degrees. */
	private static final double SUB_RING_STEP = 45;
	/** Light dim over the world, so it stays visible behind the wheel. */
	private static final int BACKDROP = 0x33000000;
	/** Translucent charcoal band the slices sit on. */
	private static final int RING = 0xC0141418;
	/** Slightly lighter hub inside the dead-zone, behind the ring label. */
	private static final int HUB = 0xB0202028;
	/** Soft highlight behind the hovered slice (icon + label). */
	private static final int HOVER = 0x50FFFFFF;
	/** A slice's icon (16) + gap (2) + label line (9), centred on the slice point. */
	private static final int BLOCK_HEIGHT = 27;
	/** Space between a slice block and the band's edges. */
	private static final int BAND_PADDING = 5;
	private static final int HOVER_RADIUS = 17;
	private static final double HUB_FRACTION = 0.45;
	private static final int MIN_HUB_RADIUS = 22;
	private static final int HUB_GAP = 4;

	/** Radius used by the last frame, so clicks hit-test against what was drawn. */
	private double renderedRadius;

	/**
	 * Resolves a ring/dynamic node's children. {@code userInitiated} is true only for a direct user
	 * activation (click, Enter, hold-key release on that node); only such a call may have a side
	 * effect such as sending /homes. Refreshes, Back and tick/reply-driven re-resolves pass false.
	 */
	@FunctionalInterface
	public interface ChildrenProvider {
		List<WheelNode> children(WheelNode node, boolean userInitiated);
	}

	/** Replaceable so the homes feature can hook fetching in. */
	public static ChildrenProvider childrenProvider = (node, userInitiated) -> defaultChildren(node);

	/** True while a placeholder (e.g. "Loading…") is still valid; once false, tick() re-resolves the ring. */
	public static BooleanSupplier placeholderPending = () -> false;

	private final Deque<WheelNode> path = new ArrayDeque<>(); // root first, current ring last
	/** Rotation of each ring on {@link #path}: a sub-ring's first entry sits where the slice that opened it was. */
	private final Deque<Double> starts = new ArrayDeque<>();
	private final KeyMapping holdKey;
	private List<WheelNode> entries;
	private int hovered = -1;
	private boolean keyStillHeld;

	public RadialScreen(WheelNode root, KeyMapping holdKey) {
		super(Component.literal(root.label == null ? "" : root.label));
		this.holdKey = holdKey;
		this.keyStillHeld = holdKey != null && canPoll(KeyMappingHelper.getBoundKeyOf(holdKey));
		path.addLast(root);
		starts.addLast(0.0);
		entries = resolve(root, false);
	}

	/**
	 * Where each current entry sits: the top ring evenly spread; a sub-ring fanned out beside its default
	 * (at most {@link #SUB_RING_STEP} degrees apart) so switching options is a short flick.
	 */
	private double[] directions() {
		return RadialMath.fan(entries.size(), start(), path.size() <= 1 ? 360 : SUB_RING_STEP);
	}

	private double start() {
		Double s = starts.peekLast();
		return s == null ? 0 : s;
	}

	/** Default resolver: vault count from config, homes from the per-server cache (none if not wired). */
	public static List<WheelNode> defaultChildren(WheelNode node) {
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		HomesCache homes = CubeWheelClient.homes();
		List<String> names = homes == null ? List.of() : ServerGate.currentHost().map(homes::get).orElse(List.of());
		return WheelResolver.children(node, cfg.vaultCount, names);
	}

	/** childrenProvider with failures contained: a broken provider yields an empty ring, not a crash. */
	static List<WheelNode> resolve(WheelNode node, boolean userInitiated) {
		try {
			List<WheelNode> out = childrenProvider.children(node, userInitiated);
			return out == null ? List.of() : out;
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] resolving children of '{}' failed", node.label, e);
			return List.of();
		}
	}

	/**
	 * Re-resolves the current ring (e.g. when homes arrive). A sub-ring that has grown past
	 * listThreshold is handed over to a ListScreen, exactly as activate() would have done.
	 * Never user-initiated, so it never sends anything (it runs from tick() and from the homes reply).
	 */
	public void refresh() {
		WheelNode top = path.peekLast();
		List<WheelNode> children = resolve(top, false);
		hovered = -1;
		if (path.size() > 1 && children.size() > listThreshold() && minecraft.gui.screen() == this) {
			path.removeLast();
			starts.removeLast();
			entries = resolve(path.peekLast(), false);
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
		g.fill(0, 0, width, height, BACKDROP);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractRenderState(g, mouseX, mouseY, partial);
		int cx = width / 2;
		int cy = height / 2;
		// The band must hold each icon+label block wherever it sits on the ring: at 3 and 9 o'clock a
		// label spans the band's thickness horizontally, so the widest label sets the thickness.
		long now = System.currentTimeMillis();
		SliceViews.View[] views = new SliceViews.View[entries.size()];
		int maxLabel = 0;
		for (int i = 0; i < entries.size(); i++) {
			views[i] = SliceViews.view(entries.get(i), now);
			maxLabel = Math.max(maxLabel, font.width(SliceViews.label(entries.get(i), views[i])));
		}
		int half = Math.max(BLOCK_HEIGHT / 2, maxLabel / 2) + BAND_PADDING;
		double r = Math.max(radius(), half + MIN_HUB_RADIUS + HUB_GAP);
		renderedRadius = r;
		int ri = (int) Math.round(r);
		int hub = Math.min((int) Math.round(r * HUB_FRACTION), ri - half - HUB_GAP);
		double[] dirs = directions();
		hovered = RadialMath.nearest(mouseX - cx, mouseY - cy, dirs, r * 0.25);
		if (!entries.isEmpty()) fillRing(g, cx, cy, ri + half, ri - half, RING);
		fillRing(g, cx, cy, hub, 0, HUB);
		for (int i = 0; i < entries.size(); i++) {
			WheelNode node = entries.get(i);
			double[] o = RadialMath.offset(dirs[i], r);
			int x = cx + (int) Math.round(o[0]);
			int y = cy + (int) Math.round(o[1]);
			boolean hot = i == hovered;
			if (hot) fillRing(g, x, y, HOVER_RADIUS, 0, HOVER);
			// Icon and label are stacked as one block centred on the slice point.
			int top = y - BLOCK_HEIGHT / 2;
			ItemStack icon = Icons.stack(node.icon);
			if (!icon.isEmpty()) g.item(icon, x - 8, top);
			SliceViews.View view = views[i];
			int colour = view != null && view.colour() != null ? view.colour() : hot ? WHITE : GREY;
			g.centeredText(font, SliceViews.label(node, view), x, top + 18, colour);
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
			if (showsPlaceholder() && !placeholderPending.getAsBoolean()) refresh();
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
			double dead = (renderedRadius > 0 ? renderedRadius : radius()) * 0.25;
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				int slice = RadialMath.nearest(dx, dy, directions(), dead);
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
			String command = SliceViews.command(node, SliceViews.view(node, System.currentTimeMillis()));
			if (command != null) {
				onClose();
				CommandSender.send(command); // one command per activation
				return;
			}
			if (!SliceViews.opens(node)) return; // e.g. "Loading…", "No boss event": not actionable
			if (HomesFetcher.isRefreshEntry(node)) {
				resolve(node, true); // user click: may force one /homes (rate-limited)
				refresh(); // re-resolve the current ring in place, without sending
				return;
			}
			List<WheelNode> children = resolve(node, true);
			if (children.size() > listThreshold()) {
				openList(node, children);
				return;
			}
			// Rotate the sub-ring so its first (default) entry sits under the slice just chosen:
			// clicking the same spot again takes the default (Vaults → Vault 1).
			int index = entries.indexOf(node);
			double[] dirs = directions();
			double childStart = index < 0 || index >= dirs.length ? start() : dirs[index];
			path.addLast(node);
			starts.addLast(childStart);
			entries = children;
			hovered = -1;
		} catch (RuntimeException e) {
			fail("activate", e);
		}
	}

	/** A non-actionable entry: no command, no children to open (e.g. "Loading…"). */
	private static boolean isPlaceholder(WheelNode node) {
		return !node.isLeaf() && !node.isRing() && !node.isDynamic();
	}

	private boolean showsPlaceholder() {
		for (WheelNode n : entries) if (isPlaceholder(n)) return true;
		return false;
	}

	private void openList(WheelNode node, List<WheelNode> children) {
		keyStillHeld = false; // returning from the list must not look like a key release
		// If the hold key is still down (e.g. a click opened the list), the list must not type it into its filter.
		minecraft.gui.setScreen(new ListScreen(this, node, children, typingKeyDown(minecraft, holdKey) ? holdKey : null));
	}

	/** True while {@code key} is a keyboard key that is physically down (a mouse button never types). */
	static boolean typingKeyDown(net.minecraft.client.Minecraft mc, KeyMapping key) {
		if (key == null || mc == null) return false;
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
		if (bound.getType() != InputConstants.Type.KEYSYM || bound.getValue() < 0) return false;
		return InputConstants.isKeyDown(mc.getWindow(), bound.getValue());
	}

	/** Up one level (re-resolving that ring from the cache; never sends); at the root, closes. */
	private void back() {
		if (path.size() <= 1) {
			onClose();
			return;
		}
		path.removeLast();
		starts.removeLast();
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
		return Math.max(48, Math.min(100, Math.min(width, height) * 0.22));
	}

	/** Fills a ring (or a disc when {@code inner} is 0) as one-unit-tall strips. */
	private static void fillRing(GuiGraphicsExtractor g, int cx, int cy, int outer, int inner, int argb) {
		for (int[] s : RadialMath.ringSpans(outer, inner)) {
			g.fill(cx + s[1], cy + s[0], cx + s[2], cy + s[0] + 1, argb);
		}
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
