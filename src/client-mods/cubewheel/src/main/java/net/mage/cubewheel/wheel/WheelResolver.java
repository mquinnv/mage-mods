package net.mage.cubewheel.wheel;

import net.mage.cubewheel.config.WheelNode;

import java.util.ArrayList;
import java.util.List;

/** Resolves dynamic wheel rings (vaults, homes, etc.) into concrete WheelNode lists. */
public final class WheelResolver {
	private WheelResolver() {}

	/**
	 * Resolve a wheel node to its final children.
	 *
	 * @param node wheel node to resolve
	 * @param vaultCount number of personal vaults available
	 * @param homes list of home names for the homes ring
	 * @return list of resolved child nodes; empty if leaf or if dynamic source produces no items
	 */
	public static List<WheelNode> children(WheelNode node, int vaultCount, List<String> homes) {
		List<WheelNode> result = new ArrayList<>();

		if (node.isLeaf()) {
			// Leaves have no children
			return result;
		}

		if (node.isRing()) {
			// Rings return a copy of their children
			result.addAll(node.children);
			return result;
		}

		if (node.isDynamic()) {
			// Resolve dynamic sources
			if ("vaults".equals(node.dynamic)) {
				// Create leaf for each vault (1 to vaultCount)
				for (int i = 1; i <= vaultCount; i++) {
					result.add(WheelNode.leaf("PV " + i, "minecraft:ender_chest", "/pv " + i));
				}
			} else if ("homes".equals(node.dynamic)) {
				// The Homes slice's own entries are pins: they go first (so the arc shows them before any fetch, and
				// ahead of a long home list), and a fetched home that one of them already sends is not listed twice
				// (Michael 2026-10-08: "i need a quick way to get to /home armor").
				java.util.Set<String> pinned = new java.util.HashSet<>();
				if (node.children != null) {
					for (WheelNode pin : node.children) {
						if (pin == null) continue;
						result.add(pin);
						if (pin.command != null) pinned.add(commandKey(pin.command));
					}
				}
				// Create leaves for each home, sorted case-insensitive
				List<String> sortedHomes = new ArrayList<>(homes);
				sortedHomes.sort(String.CASE_INSENSITIVE_ORDER);
				String defaultIcon = node.icon != null ? node.icon : "minecraft:red_bed";
				for (String home : sortedHomes) {
					if (pinned.contains(commandKey("/home " + home))) continue;
					result.add(WheelNode.leaf(home, defaultIcon, "/home " + home));
				}
				return result;
			}
			// For unknown dynamic sources, just add extras

			// Add extras to the result (guard against null children from deserialization)
			if (node.children != null) {
				result.addAll(node.children);
			}
			return result;
		}

		return result;
	}

	/** "/Home  Armor" and "home armor" are the same command. */
	static String commandKey(String command) {
		String c = command.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
		return c.startsWith("/") ? c.substring(1) : c;
	}
}
