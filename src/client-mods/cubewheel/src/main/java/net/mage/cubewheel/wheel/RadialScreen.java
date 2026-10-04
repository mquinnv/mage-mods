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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Hold-to-open radial command wheel. Release or left-click commits; right-click goes back. */
public final class RadialScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF;
	/** Unselected labels: light enough to read over a bright world. */
	private static final int GREY = 0xFFDADADA;
	/** Plate behind labels drawn off the band (outer tiers, arcs), so the world behind can't wash them out. */
	private static final int PLATE = 0xB0101014;
	/** Space between an outer label and the next tier's disc. */
	private static final int LABEL_GAP = 3;
	/** Widest gap between neighbouring entries of a sub-ring, in degrees. */
	private static final double SUB_RING_STEP = 45;
	/** Depth of each tier beyond the ring (one icon + label block plus padding). */
	private static final int TIER_STEP = 34;
	/** Closest tiers may be squeezed when the screen edge is near (icons still clear each other). */
	private static final int MIN_TIER_STEP = 20;
	/** How far a chain must lean sideways (sine of its angle) before its labels move beside the icons. */
	private static final double SIDEWAYS = 0.35;
	/** How far above the screen centre the wheel sits. */
	private static final int LIFT = 60;
	/** Distance between neighbouring arc entries (one disc plus a gap). */
	private static final int ARC_SPACING = 40;
	/** The most of the circle an arc may cover. */
	private static final double MAX_ARC_DEGREES = 300;
	/** At most this many "more" dots past a slice. */
	private static final int MAX_DOTS = 4;
	/** Longest arc a ring is fanned into (more entries: open the ring). */
	private static final int MAX_ARC = 9;
	private static final int DOT = 0xB0AAAAAA;
	/** Background disc behind an outer entry. */
	private static final int OUTER_DISC = 16;
	/** Light dim over the world, so it stays visible behind the wheel. */
	private static final int BACKDROP = 0x33000000;
	/** Translucent charcoal band the slices sit on. */
	private static final int RING = 0xC0141418;
	/** The depth-2 band (where arcs fan out): lighter than the ring. */
	private static final int BAND2 = 0x70303038;
	/** Slightly lighter hub inside the dead-zone, behind the ring label. */
	private static final int HUB = 0xB0202028;
	/** Soft highlight behind the hovered slice (icon + label). */
	private static final int HOVER = 0x50FFFFFF;
	/** A slice's icon (16) + gap (2) + label line (9), centred on the slice point. */
	private static final int BLOCK_HEIGHT = 27;
	/** Space between a slice block and the band's edges. */
	private static final int BAND_PADDING = 5;
	private static final int HOVER_RADIUS = 17;
	/** The hub is the dead zone: inside it nothing is selected (and a click goes back). */
	private static final double HUB_FRACTION = 0.32;
	private static final int MIN_HUB_RADIUS = 22;
	private static final int HUB_GAP = 4;
	/** The top level's dead zone (and hub disc), in GUI pixels. */
	private static final int TOP_HUB_RADIUS = 12;

	/** Radius used by the last frame, so clicks hit-test against what was drawn. */
	private double renderedRadius;
	/** Hub radius as last drawn: the selection cutoff. */
	private double renderedHub;

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
	/** Tier of the hovered slice: 0 = the ring entry, 1+ = its outer entries (further from the centre). */
	private int hoveredTier;
	private final TierPicker tiers = new TierPicker();
	private boolean cursorPlaced;
	/** Set by the first real mouse move; until then the reported position is stale (see extractRenderState). */
	private boolean pointerMoved;

	@Override
	public void mouseMoved(double x, double y) {
		pointerMoved = true;
		super.mouseMoved(x, y);
	}
	/** The slice whose arc is showing (it, or one of its arc entries, is pointed at), or -1. */
	private int arcOpen = -1;
	/** The arc entry pointed at, or -1. */
	private int hoveredArc = -1;
	/** Where the open arc's entries sit, as last drawn. */
	private double[] arcAngles = new double[0];
	private double[] arcRadii = new double[0];
	/** The open arc's widest gap between neighbours, in degrees (sets how far the pointer may stray). */
	private double arcStep;
	/** Each slice's arc entries this frame (null: none). */
	private List<List<WheelNode>> arcLists = List.of();
	/** Outer-tier spacing per slice as last drawn (shrunk where the screen edge is near). */
	private double[] tierSteps = new double[0];
	/** Per slice: the step at which every outer label fits beside its icon; below it only the hovered one is labelled. */
	private double[] labelSteps = new double[0];
	/** The ring's outer edge as last drawn; beyond it the outer tiers start. */
	private double renderedEdge;
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

	/** How many outer entries a slice has (0 for a plain slice). */
	private static int outerCount(WheelNode node) {
		int n = 0;
		for (WheelNode o = node == null ? null : node.outer; o != null; o = o.outer) n++;
		return n;
	}

	/** The slice's entry at {@code tier}: 0 = itself, 1 = its outer entry, 2 = that one's outer … */
	private static WheelNode atTier(WheelNode node, int tier) {
		WheelNode at = node;
		for (int t = 0; t < tier && at.outer != null; t++) at = at.outer;
		return at;
	}

	private double start() {
		Double s = starts.peekLast();
		return s == null ? 0 : s;
	}

	/** Default resolver: vault count as read from /pv (else config), homes from the per-server cache. */
	public static List<WheelNode> defaultChildren(WheelNode node) {
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		HomesCache homes = CubeWheelClient.homes();
		List<String> names = homes == null ? List.of() : ServerGate.currentHost().map(homes::get).orElse(List.of());
		return WheelResolver.children(node, net.mage.cubewheel.live.LiveWatcher.vaultCount(cfg.vaultCount), names);
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
		int cy = centerY();
		// Until the mouse really moves, Minecraft still reports the old screen centre (below the lifted hub), which
		// read as "pointing straight down" (Fly) on a release without moving: treat the pointer as on the hub.
		if (!pointerMoved) {
			mouseX = cx;
			mouseY = cy;
		}
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
		// A small fixed dead zone: anything past it selects by direction, so the pointer barely has to move. A
		// sub-ring keeps the larger hub, which holds its name.
		int hub = path.size() <= 1 ? TOP_HUB_RADIUS : Math.min((int) Math.round(r * HUB_FRACTION), ri - half - HUB_GAP);
		double[] dirs = directions();
		renderedHub = hub;
		hovered = RadialMath.nearest(mouseX - cx, mouseY - cy, dirs, hub);
		renderedEdge = ri + half;
		tierSteps = new double[entries.size()];
		labelSteps = new double[entries.size()];
		for (int i = 0; i < entries.size(); i++) {
			// Every outer label but the last has another disc beyond it, so the widest of those sets the spacing.
			int widest = 0;
			for (WheelNode o = entries.get(i).outer; o != null && o.outer != null; o = o.outer)
				widest = Math.max(widest, font.width(o.label == null ? "" : o.label));
			labelSteps[i] = RadialMath.labelStep(dirs[i], widest, OUTER_DISC, LABEL_GAP, SIDEWAYS, TIER_STEP);
			tierSteps[i] = RadialMath.tierStep(renderedEdge, RadialMath.reach(cx, cy, dirs[i], width, height, 2),
					outerCount(entries.get(i)), OUTER_DISC + font.lineHeight, MIN_TIER_STEP, labelSteps[i]);
		}
		// An open arc keeps the pointer while it is on one of the arc's entries, wherever the ring's nearest slice is.
		// The arc's entries count as the arc slice's tiers (tier 1 = first entry), so pointing, scrolling and Shift
		// all work the same on both.
		arcLists = new java.util.ArrayList<>();
		for (WheelNode e : entries) arcLists.add(arcOf(e));
		double mdx = mouseX - cx;
		double mdy = mouseY - cy;
		int onArc = -1;
		if (arcOpen >= entries.size() || arcOpen >= 0 && arcAt(arcOpen) == null) arcOpen = -1;
		if (arcOpen >= 0) {
			layoutArc(cx, cy, dirs[arcOpen], arcAt(arcOpen));
			// The arc answers from the ring's middle line out: the slice's inner half is the slice, its outer half
			// already reaches into the arc, so the pointer never has to cross the whole band.
			onArc = ArcLayout.pick(mdx, mdy, renderedRadius, arcAngles, arcSlack());
			if (onArc >= 0) hovered = arcOpen; // an open arc keeps the pointer wherever the ring's nearest slice is
		}
		if (onArc < 0) {
			arcOpen = hovered >= 0 && arcAt(hovered) != null ? hovered : -1;
			if (arcOpen >= 0) layoutArc(cx, cy, dirs[arcOpen], arcAt(arcOpen));
		}
		WheelNode hoveredNode = hovered < 0 ? null : entries.get(hovered);
		boolean shift = isShiftDown();
		if (arcOpen >= 0 && hovered == arcOpen) {
			int n = arcAngles.length;
			hoveredTier = tiers.pick(hoveredNode, onArc + 1, n, false);
			if (shift) hoveredTier = 1 + ArcLayout.pick(mdx, mdy, -1, arcAngles, 360); // the arc entry nearest the pointer
			hoveredArc = hoveredTier - 1;
		} else {
			hoveredTier = tiers.pick(hoveredNode, hoveredNode == null ? 0 : distanceTier(hovered, Math.hypot(mdx, mdy)),
					outerCount(hoveredNode), shift);
			hoveredArc = -1;
		}
		if (!entries.isEmpty()) fillRing(g, cx, cy, ri + half, ri - half, RING);
		// Depth 2: a lighter band just outside the ring, where arc entries sit, whenever any slice has an arc.
		boolean anyArc = false;
		for (int i = 0; i < entries.size(); i++) anyArc |= arcAt(i) != null;
		if (anyArc) fillRing(g, cx, cy, ri + half + ARC_SPACING, ri + half, BAND2);
		// The hovered slice lights up its whole wedge of the ring.
		if (hovered >= 0 && hoveredTier == 0 && hoveredArc < 0) {
			double w = sliceHalfWidth(dirs, hovered);
			fillSector(g, cx, cy, ri + half, ri - half, dirs[hovered] - w, dirs[hovered] + w, HOVER);
		}
		fillRing(g, cx, cy, hub, 0, HUB);
		for (int i = 0; i < entries.size(); i++) {
			WheelNode node = entries.get(i);
			double[] o = RadialMath.offset(dirs[i], r);
			int x = cx + (int) Math.round(o[0]);
			int y = cy + (int) Math.round(o[1]);
			boolean hot = i == hovered && hoveredTier == 0 && hoveredArc < 0;
			// Icon and label are stacked as one block centred on the slice point.
			int top = y - BLOCK_HEIGHT / 2;
			ItemStack icon = Icons.stack(node.icon);
			if (!icon.isEmpty()) g.item(icon, x - 8, top);
			SliceViews.View view = views[i];
			int colour = view != null && view.colour() != null ? view.colour() : hot ? WHITE : GREY;
			g.centeredText(font, SliceViews.label(node, view), x, top + 18, colour);
			// Outer tiers: the same slice, further out (e.g. Sell hand, then Sell all). A long chain shows only its
			// first outer tier until hovered; squeezed tiers label only the selected one.
			int count = outerCount(node);
			// Short chains stay visible; long ones show their first tier until hovered. An open arc hides the tiers
			// of the slices it covers (dots mark that they are there).
			boolean coveredByArc = i != arcOpen && underArc(dirs[i]);
			int shown = i == hovered ? count : coveredByArc ? 0 : count <= 2 ? count : 1;
			if (shown == 0 && count > 0 || i != arcOpen && arcAt(i) != null) moreDots(g, cx, cy, dirs[i], arcAt(i) != null ? arcAt(i).size() : count);
			boolean roomy = tierSteps[i] >= labelSteps[i] - 0.5;
			WheelNode outer = node.outer;
			for (int tier = 1; outer != null && tier <= shown; tier++, outer = outer.outer) {
				double[] oo = RadialMath.offset(dirs[i], renderedEdge + (tier - 0.5) * tierSteps[i]);
				int ox = cx + (int) Math.round(oo[0]);
				int oy = cy + (int) Math.round(oo[1]);
				boolean outerHot = i == hovered && hoveredTier == tier;
				fillRing(g, ox, oy, OUTER_DISC, 0, outerHot ? HOVER : RING);
				int otop = oy - 8; // icon centred in its disc; the label sits beside it
				ItemStack oicon = Icons.stack(outer.icon);
				if (!oicon.isEmpty()) g.item(oicon, ox - 8, otop);
				// The last tier has no disc beyond it to collide with, so its label always shows.
				if (roomy || outerHot || outer.outer == null) outerLabel(g, outer.label == null ? "" : outer.label, ox, oy, dirs[i], outerHot ? WHITE : GREY);
			}
		}
		if (arcOpen >= 0) drawArc(g, cx, cy, arcAt(arcOpen));
		WheelNode current = path.peekLast();
		// The top level shows just a dot in its small hub; a sub-ring names itself so you know where you are.
		String centre = path.size() <= 1 ? "·" : current.label == null ? "" : current.label;
		g.centeredText(font, centre, cx, cy - font.lineHeight / 2, WHITE);
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
			if (hoveredArc >= 0 && arcOpen >= 0) activate(arcAt(arcOpen).get(hoveredArc));
			else if (hovered >= 0 && hovered < entries.size()) activate(atTier(entries.get(hovered), hoveredTier));
			// A tap (released without moving the mouse) just shows the wheel: it stays open in click mode
			// (Michael 2026-10-04). Pointing back at the hub after moving still cancels.
			else if (pointerMoved) onClose();
		} catch (RuntimeException e) {
			fail("tick", e);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		InputConstants.Key bound = holdKey == null ? null : KeyMappingHelper.getBoundKeyOf(holdKey);
		if (bound != null && bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() == event.key()) {
			// Key repeat while held does nothing; pressing it again once the wheel is open in click mode closes it.
			if (!keyStillHeld) onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		try {
			keyStillHeld = false; // any mouse-driven action ends hold mode; a later release must not commit
			int cx = width / 2;
			int cy = centerY();
			double dx = pointerMoved ? event.x() - cx : 0;
			double dy = pointerMoved ? event.y() - cy : 0;
			double dead = renderedHub > 0 ? renderedHub : radius() * HUB_FRACTION;
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				if (hoveredArc >= 0 && arcOpen >= 0 && arcOpen < entries.size() && arcAt(arcOpen) != null) {
					activate(arcAt(arcOpen).get(hoveredArc));
					return true;
				}
				int slice = RadialMath.nearest(dx, dy, directions(), dead);
				if (slice >= 0) {
					WheelNode node = entries.get(slice);
					int tier = slice == hovered ? hoveredTier
							: isShiftDown() ? outerCount(node) : distanceTier(slice, Math.hypot(dx, dy));
					activate(atTier(node, tier));
				}
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
			if (!SliceViews.opens(node)) return; // e.g. "Loading…", "No boss": not actionable
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

	/** Minecraft parks the cursor at the screen centre; move it onto the (lifted) hub so nothing starts selected. */
	@Override
	protected void init() {
		super.init();
		if (cursorPlaced || minecraft == null) return;
		cursorPlaced = true;
		Window window = minecraft.getWindow();
		double sx = window.getScreenWidth() / (double) Math.max(1, width);
		double sy = window.getScreenHeight() / (double) Math.max(1, height);
		GLFW.glfwSetCursorPos(window.handle(), width / 2 * sx, centerY() * sy);
	}

	/** The wheel sits a little above the screen centre so its lower tiers clear HUDs below the crosshair. */
	private int centerY() {
		return height / 2 - Math.min(LIFT, height / 6);
	}

	private double radius() {
		return Math.max(48, Math.min(100, Math.min(width, height) * 0.22));
	}

	/** Fills a ring (or a disc when {@code inner} is 0) as one-unit-tall strips. */
	/** Half the angle a slice owns: half the gap to its nearest neighbour (a lone entry: half a sub-ring step). */
	private static double sliceHalfWidth(double[] dirs, int i) {
		if (dirs.length <= 1) return SUB_RING_STEP / 2.0;
		double best = 360;
		for (int j = 0; j < dirs.length; j++) {
			if (j == i) continue;
			double d = Math.abs(dirs[i] - dirs[j]) % 360;
			best = Math.min(best, Math.min(d, 360 - d));
		}
		return best / 2.0;
	}

	/** Recent wedge shapes, so a steady hover costs nothing per frame (a wedge is computed pixel by pixel). */
	private final java.util.Map<String, List<int[]>> sectors = new java.util.LinkedHashMap<>(16, 0.75f, true) {
		@Override protected boolean removeEldestEntry(java.util.Map.Entry<String, List<int[]>> e) {
			return size() > 16;
		}
	};

	/** Fills the wedge of the band between {@code inner} and {@code outer} from {@code from} to {@code to} degrees. */
	private void fillSector(GuiGraphicsExtractor g, int cx, int cy, int outer, int inner, double from, double to, int argb) {
		long f = Math.round(from * 4), t = Math.round(to * 4); // quarter degrees: plenty for a highlight
		List<int[]> spans = sectors.computeIfAbsent(outer + ":" + inner + ":" + f + ":" + t,
				k -> RadialMath.sectorSpans(outer, inner, f / 4.0, t / 4.0));
		for (int[] s : spans) g.fill(cx + s[1], cy + s[0], cx + s[2], cy + s[0] + 1, argb);
	}

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

	/**
	 * An outer tier's label, set beside its icon on the side away from the wheel so a chain's labels don't run into
	 * the next circle: right of the icon on the right half, left of it on the left; low on a chain going up, high on
	 * one going down. A chain going straight up keeps the label centred under the icon.
	 */
	private void outerLabel(GuiGraphicsExtractor g, String label, int ox, int oy, double direction, int colour) {
		double[] d = RadialMath.offset(direction, 1);
		int w = font.width(label);
		int y = d[1] < 0 ? oy + 2 : oy - 2 - font.lineHeight;
		if (d[0] > SIDEWAYS) plated(g, label, ox + 10, y, colour);
		else if (d[0] < -SIDEWAYS) plated(g, label, ox - 10 - w, y, colour);
		else if (d[1] < 0) plated(g, label, ox - w / 2, oy - BLOCK_HEIGHT / 2 + 18, colour);
		else plated(g, label, ox + 10, oy - font.lineHeight / 2, colour);
	}

	/** A label on a dark plate, for labels drawn over the world rather than on the band. */
	private void plated(GuiGraphicsExtractor g, String label, int x, int y, int colour) {
		if (label.isEmpty()) return;
		g.fill(x - 2, y - 1, x + font.width(label) + 1, y + font.lineHeight, PLATE);
		g.text(font, label, x, y, colour);
	}

	/** Slice {@code i}'s arc entries as of this frame, or null. */
	private List<WheelNode> arcAt(int i) {
		return i >= 0 && i < arcLists.size() ? arcLists.get(i) : null;
	}

	/**
	 * A slice's arc: its own {@code arc} entries, or for an {@code asArc} ring its resolved entries (plain commands
	 * only, at most {@link #MAX_ARC}); null when there are none.
	 */
	private static List<WheelNode> arcOf(WheelNode node) {
		if (node == null) return null;
		if (node.arc != null) return node.arc.isEmpty() ? null : node.arc;
		if (!node.asArc || node.isLeaf()) return null;
		List<WheelNode> out = new java.util.ArrayList<>();
		for (WheelNode c : resolve(node, false)) {
			if (c != null && c.command != null && !c.isSlice()) out.add(c);
			if (out.size() == MAX_ARC) break;
		}
		return out.isEmpty() ? null : out;
	}

	/** Whether direction {@code deg} lies under the open arc (between its end entries, plus half a step). */
	private boolean underArc(double deg) {
		if (arcOpen < 0 || arcAngles.length == 0) return false;
		double centre = directions()[arcOpen];
		double first = Math.abs(arcAngles[0] - centre) % 360;
		double half = Math.min(first, 360 - first) + arcStep / 2.0;
		double d = Math.abs(deg - centre) % 360;
		return Math.min(d, 360 - d) <= half;
	}

	/** How far (degrees) from an arc entry the pointer may stray and still be on it. */
	private double arcSlack() {
		return arcStep * 0.75;
	}

	/** Small dots just past the ring hinting that a slice has more (outer tiers or an arc) when hovered. */
	private void moreDots(GuiGraphicsExtractor g, int cx, int cy, double direction, int count) {
		for (int k = 0; k < Math.min(count, MAX_DOTS); k++) {
			double[] o = RadialMath.offset(direction, renderedEdge + 5 + k * 6);
			int x = cx + (int) Math.round(o[0]);
			int y = cy + (int) Math.round(o[1]);
			g.fill(x - 1, y - 1, x + 2, y + 2, DOT);
		}
	}

	/**
	 * Places the arc's entries around {@code centre}, pulled in where the screen edge is near. Neighbours sit at
	 * least {@link #ARC_SPACING} apart, and further where their labels run along the arc (above and below the
	 * wheel) so labels never meet. Where an entry lands decides how its label runs, so the gaps are settled over a
	 * few passes.
	 */
	private void layoutArc(int cx, int cy, double centre, List<WheelNode> arc) {
		int count = arc.size();
		double radius = renderedEdge + ARC_SPACING / 2.0;
		double base = ArcLayout.step(radius, ARC_SPACING);
		arcAngles = ArcLayout.angles(count, centre, base);
		int[] widths = new int[count];
		for (int j = 0; j < count; j++) widths[j] = font.width(arc.get(j).label == null ? "" : arc.get(j).label);
		double widest = base;
		for (int pass = 0; pass < 3 && count > 1; pass++) {
			double[] gaps = new double[count - 1];
			double total = 0;
			for (int k = 0; k < count - 1; k++) {
				gaps[k] = Math.max(ARC_SPACING, LABEL_GAP
						+ ArcLayout.extent(arcAngles[k], widths[k], font.lineHeight, OUTER_DISC, SIDEWAYS)
						+ ArcLayout.extent(arcAngles[k + 1], widths[k + 1], font.lineHeight, OUTER_DISC, SIDEWAYS));
				total += ArcLayout.step(radius, gaps[k]);
			}
			// Even spacing reads as one arc: every gap takes the widest one's size.
			double even = 0;
			for (double gap : gaps) even = Math.max(even, gap);
			java.util.Arrays.fill(gaps, even);
			total = gaps.length * ArcLayout.step(radius, even);
			// A long arc may not wrap all the way round; past that it tightens (labels may then touch).
			if (total > MAX_ARC_DEGREES) for (int k = 0; k < gaps.length; k++) gaps[k] *= MAX_ARC_DEGREES / total;
			arcAngles = ArcLayout.angles(gaps, centre, radius);
			widest = base;
			for (double gap : gaps) widest = Math.max(widest, ArcLayout.step(radius, gap));
		}
		arcStep = widest;
		arcRadii = new double[count];
		for (int j = 0; j < count; j++) {
			double room = RadialMath.reach(cx, cy, arcAngles[j], width, height, 2) - OUTER_DISC - font.lineHeight;
			arcRadii[j] = Math.max(renderedEdge - OUTER_DISC, Math.min(radius, room));
		}
	}

	private void drawArc(GuiGraphicsExtractor g, int cx, int cy, List<WheelNode> arc) {
		int inner = (int) Math.round(renderedEdge);
		// The open arc's span of the depth-2 band is darkened so its entries read as one group.
		if (arcAngles.length > 0) {
			double first = arcAngles[0] - arcStep / 2.0, span = arcStep * arcAngles.length;
			fillSector(g, cx, cy, inner + ARC_SPACING, inner, first, first + span, RING);
		}
		for (int j = 0; j < arc.size() && j < arcAngles.length; j++) {
			WheelNode a = arc.get(j);
			double[] o = RadialMath.offset(arcAngles[j], arcRadii[j]);
			int x = cx + (int) Math.round(o[0]);
			int y = cy + (int) Math.round(o[1]);
			boolean hot = j == hoveredArc;
			// The hovered entry lights up its whole wedge of the band.
			if (hot) fillSector(g, cx, cy, inner + ARC_SPACING, inner, arcAngles[j] - arcStep / 2.0, arcAngles[j] + arcStep / 2.0, HOVER);
			ItemStack icon = Icons.stack(a.icon);
			if (!icon.isEmpty()) g.item(icon, x - 8, y - 8);
			radialLabel(g, a.label == null ? "" : a.label, x, y, arcAngles[j], hot ? WHITE : GREY);
		}
	}

	/** A label just outside a disc, on the side facing away from the wheel's centre. */
	private void radialLabel(GuiGraphicsExtractor g, String label, int x, int y, double direction, int colour) {
		double[] d = RadialMath.offset(direction, 1);
		int w = font.width(label);
		int reach = OUTER_DISC + 2;
		int lx = d[0] > SIDEWAYS ? x + reach : d[0] < -SIDEWAYS ? x - reach - w : x - w / 2;
		int ly = d[1] > SIDEWAYS ? y + reach - 4 : d[1] < -SIDEWAYS ? y - reach - font.lineHeight + 4 : y - font.lineHeight / 2;
		plated(g, label, lx, ly, colour);
	}

	/** The tier the pointer's distance past the ring picks on slice {@code index}. */
	private int distanceTier(int index, double distance) {
		if (renderedEdge <= 0 || index < 0 || index >= entries.size()) return 0;
		double step = index < tierSteps.length ? tierSteps[index] : TIER_STEP;
		return RadialMath.tier(distance, renderedEdge, step, outerCount(entries.get(index)));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (hovered >= 0 && hovered < entries.size()) {
			WheelNode node = entries.get(hovered);
			tiers.scroll(scrollY, arcAt(hovered) != null ? arcAt(hovered).size() : outerCount(node));
		}
		return true;
	}

	/** Either Shift key, unless Shift is the wheel's own hold key (then it is always down and means nothing). */
	private boolean isShiftDown() {
		if (minecraft == null) return false;
		if (holdKey != null) {
			InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(holdKey);
			if (bound.getType() == InputConstants.Type.KEYSYM
					&& (bound.getValue() == GLFW.GLFW_KEY_LEFT_SHIFT || bound.getValue() == GLFW.GLFW_KEY_RIGHT_SHIFT)) return false;
		}
		Window window = minecraft.getWindow();
		return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
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
