package net.mage.cubewheel.wheel;

import net.mage.cubewheel.config.WheelNode;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live label / colour / command hooks for wheel slices ("Fly: on", "Daily reward: ready", "Mana Golem · 2m").
 * Small providers are registered once; the wheel asks them per frame. The first provider returning a view
 * for a node wins. Pure: no Minecraft/Fabric imports.
 */
public final class SliceViews {
	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");

	public static final int GREEN = 0xFF55FF55;
	public static final int RED = 0xFFFF7777;
	public static final int GOLD = 0xFFFFAA00;
	public static final int DIM = 0xFF777777;

	/**
	 * How one slice looks and acts right now.
	 *
	 * @param label   text under the icon
	 * @param colour  ARGB label colour, or null for the normal hover white / idle grey
	 * @param command command to send instead of the node's own, or null to keep the node's
	 * @param inert   true: clicking does nothing (a placeholder such as "No boss event")
	 */
	public record View(String label, Integer colour, String command, boolean inert) {}

	@FunctionalInterface
	public interface Provider {
		/** The view of {@code node}, or null when this provider does not handle it. */
		View view(WheelNode node, long now);
	}

	private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();
	private static boolean failureLogged;

	private SliceViews() {}

	public static void register(Provider p) {
		if (p != null) PROVIDERS.add(p);
	}

	/** Tests only. */
	static void clear() {
		PROVIDERS.clear();
	}

	/** The first provider's view of {@code node}; null if none handles it. A throwing provider is skipped. */
	public static View view(WheelNode node, long now) {
		if (node == null) return null;
		for (Provider p : PROVIDERS) {
			try {
				View v = p.view(node, now);
				if (v != null) return v;
			} catch (RuntimeException e) {
				if (!failureLogged) LOG.error("[cubewheel] slice provider failed", e);
				failureLogged = true;
			}
		}
		return null;
	}

	/** Label to draw: the view's, else the node's own. */
	public static String label(WheelNode node, View view) {
		if (view != null && view.label() != null) return view.label();
		return node == null || node.label == null ? "" : node.label;
	}

	/**
	 * The command activating {@code node} sends, or null when activating it sends nothing (an inert view,
	 * a ring, a slice without a live command, a placeholder).
	 */
	public static String command(WheelNode node, View view) {
		if (node == null) return null;
		if (view != null && view.inert()) return null;
		if (view != null && view.command() != null && !view.command().isBlank()) return view.command();
		return node.isLeaf() ? node.command : null;
	}

	/** True if activating {@code node} opens a ring (not a leaf, slice or placeholder). */
	public static boolean opens(WheelNode node) {
		return node != null && !node.isLeaf() && !node.isSlice() && (node.isRing() || node.isDynamic());
	}

	/** True if {@code node} sends exactly {@code command} (case-insensitive, trimmed). */
	public static boolean sends(WheelNode node, String command) {
		return node != null && node.command != null
				&& node.command.trim().toLowerCase(Locale.ROOT).equals(command.toLowerCase(Locale.ROOT));
	}

	/** The /fly toggle: "Fly: on" green when the server allows flight, "Fly: off" red otherwise. */
	public static View fly(WheelNode node, boolean mayFly) {
		if (!sends(node, "/fly")) return null;
		String base = node.label == null ? "" : node.label;
		return new View(base + (mayFly ? ": on" : ": off"), mayFly ? GREEN : RED, null, false);
	}
}
