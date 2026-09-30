package com.mage.cubewheel.wheel;

import com.mage.cubewheel.config.WheelNode;

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
					result.add(WheelNode.leaf("Vault " + i, "minecraft:ender_chest", "/pv " + i));
				}
			} else if ("homes".equals(node.dynamic)) {
				// Create leaves for each home, sorted case-insensitive
				List<String> sortedHomes = new ArrayList<>(homes);
				sortedHomes.sort(String.CASE_INSENSITIVE_ORDER);
				for (String home : sortedHomes) {
					String icon = node.icon != null ? node.icon : "minecraft:red_bed";
					result.add(WheelNode.leaf(home, icon, "/home " + home));
				}
			}
			// For unknown dynamic sources, just add extras

			// Add extras to the result
			result.addAll(node.children);
			return result;
		}

		return result;
	}
}
