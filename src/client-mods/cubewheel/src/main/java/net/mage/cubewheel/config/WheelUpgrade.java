package net.mage.cubewheel.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * The version 3 wheel upgrade: the old wheel is replaced by the new default, and every command leaf whose
 * command the new default does not have anywhere (i.e. one you added yourself) is kept, appended under
 * More › Custom. Best effort: rings you made are flattened to their leaves. Pure: no Minecraft/Fabric imports.
 */
public final class WheelUpgrade {
	/** Entries per ring; more moved leaves are split into "Custom 1", "Custom 2", ... inside Custom. */
	static final int RING_MAX = 8;
	public static final String CUSTOM = "Custom";
	/**
	 * Commands earlier defaults had and the current one dropped on purpose (2026-10-02: Travel is just Teleporter
	 * and Back; 2026-10-08: the Tangleroot warp is singular, "/warp tangleroots" was the default's own misspelling).
	 * They are not "yours", so the upgrade does not carry them into More > Custom.
	 */
	static final Set<String> RETIRED = Set.of("/spawn", "/rtp", "/warp", "/warp tangleroots");

	/** The upgraded wheel and the leaves moved into More › Custom, as "Label (/command)". */
	public record Result(List<WheelNode> wheel, List<String> moved) {}

	private WheelUpgrade() {}

	public static Result upgrade(List<WheelNode> old) {
		List<WheelNode> wheel = DefaultConfig.wheel();
		Set<String> known = new HashSet<>();
		for (WheelNode n : walk(wheel)) if (n.command != null) known.add(key(n.command));
		for (String c : RETIRED) known.add(key(c));
		Map<String, WheelNode> keep = new LinkedHashMap<>();
		if (old != null) {
			for (WheelNode n : walk(old)) {
				if (n.command == null || n.command.isBlank() || n.label == null || n.label.isBlank()) continue;
				String k = key(n.command);
				if (known.contains(k) || keep.containsKey(k)) continue;
				keep.put(k, WheelNode.leaf(n.label, n.icon, n.command));
			}
		}
		List<String> moved = new ArrayList<>();
		for (WheelNode n : keep.values()) moved.add(n.label + " (" + n.command.trim() + ")");
		if (!keep.isEmpty()) more(wheel).children.add(custom(new ArrayList<>(keep.values())));
		return new Result(wheel, moved);
	}

	private static WheelNode custom(List<WheelNode> leaves) {
		WheelNode ring = WheelNode.ring(CUSTOM, "minecraft:name_tag");
		if (leaves.size() <= RING_MAX) {
			ring.children.addAll(leaves);
			return ring;
		}
		for (int i = 0; i < leaves.size(); i += RING_MAX) {
			WheelNode part = WheelNode.ring(CUSTOM + " " + (i / RING_MAX + 1), "minecraft:name_tag");
			part.children.addAll(leaves.subList(i, Math.min(leaves.size(), i + RING_MAX)));
			ring.children.add(part);
		}
		return ring;
	}

	private static WheelNode more(List<WheelNode> wheel) {
		for (WheelNode n : wheel) if (DefaultConfig.MORE.equals(n.label) && n.isRing()) return n;
		WheelNode more = WheelNode.ring(DefaultConfig.MORE, "minecraft:chest");
		wheel.add(more);
		return more;
	}

	/**
	 * The version 6 upgrade (2026-10-03): entries that stuck out past their slice (outer tiers) move into the
	 * slice's arc, in order, after any arc entries it already had. Your own chains are kept, just shown as arcs.
	 * Returns true if anything changed.
	 */
	public static boolean chainsToArcs(List<WheelNode> wheel) {
		boolean changed = false;
		if (wheel == null) return false;
		for (WheelNode n : walk(wheel)) {
			if (n.outer == null) continue;
			List<WheelNode> arc = n.arc == null ? new ArrayList<>() : new ArrayList<>(n.arc);
			for (WheelNode o = n.outer; o != null; ) {
				WheelNode next = o.outer;
				o.outer = null;
				arc.add(o);
				o = next;
			}
			n.outer = null;
			n.arc = arc;
			changed = true;
		}
		return changed;
	}

	/**
	 * The version 10 upgrade (2026-10-08): an Isles slice that is a command leaf (the /isles menu, with the isle warps
	 * as its arc) becomes {@link DefaultConfig#isles()}, a ring of the isles each with the places under it. Isle
	 * warps you added to the old arc that the ring lacks are kept, appended to the ring. Returns true if anything
	 * changed.
	 */
	public static boolean islesRing(List<WheelNode> wheel) {
		if (wheel == null) return false;
		boolean changed = false;
		List<List<WheelNode>> lists = new ArrayList<>();
		lists.add(wheel);
		for (WheelNode n : walk(wheel)) if (n.children != null) lists.add(n.children);
		for (List<WheelNode> list : lists) {
			for (int i = 0; i < list.size(); i++) {
				WheelNode n = list.get(i);
				// A bare /isles leaf (no arc) is left alone: the arc was removed on purpose, and the ring holds one itself.
				if (n == null || n.command == null || !key(n.command).equals("isles") || n.arc == null || n.arc.isEmpty()) continue;
				WheelNode ring = DefaultConfig.isles();
				Set<String> known = new HashSet<>();
				for (WheelNode k : walk(List.of(ring))) if (k.command != null) known.add(key(k.command));
				if (n.arc != null) {
					tanglerootSingular(n.arc); // so the old "Tangleroots" entry is the ring's, not kept as an extra
					for (WheelNode a : n.arc) {
						if (a != null && a.command != null && known.add(key(a.command))) ring.children.add(a);
					}
				}
				list.set(i, ring);
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * Version 11 (2026-10-08, "we need at least the pond on sandara"): every isle in an Isles ring gains the places
	 * {@link DefaultConfig#isles()} lists under that isle (matched by the isle's warp) that its arc lacks (matched by
	 * command), appended; places of your own and your order are kept. Returns true if anything changed.
	 */
	public static boolean isleArcs(List<WheelNode> wheel) {
		if (wheel == null) return false;
		boolean changed = false;
		Map<String, WheelNode> defaults = new LinkedHashMap<>();
		for (WheelNode d : DefaultConfig.isles().children) if (d.command != null) defaults.put(key(d.command), d);
		for (WheelNode ring : walk(wheel)) {
			if (ring.children == null || ring.dynamic != null || !"Isles".equalsIgnoreCase(ring.label == null ? "" : ring.label)) continue;
			for (WheelNode isle : ring.children) {
				if (isle == null || isle.command == null) continue;
				WheelNode d = defaults.get(key(isle.command));
				if (d == null || d.arc == null) continue;
				Set<String> has = new HashSet<>();
				if (isle.arc != null) for (WheelNode a : isle.arc) if (a != null && a.command != null) has.add(key(a.command));
				for (WheelNode place : d.arc) {
					if (place.command == null || !has.add(key(place.command))) continue;
					if (isle.arc == null) isle.arc = new ArrayList<>();
					isle.arc.add(WheelNode.leaf(place.label, place.icon, place.command));
					changed = true;
				}
			}
		}
		return changed;
	}

	/**
	 * Version 14 (2026-11-04, "the one under sandara should go to sandarapond"): under the Sandara isle of an Isles
	 * ring, "/warp pond" (the main Mana Pond, which keeps its own entry under More › Warps › Server warps) becomes
	 * "/warp sandarapond", and the old default label "Mana Pond" becomes "Sandara Pond". When that arc already has
	 * "/warp sandarapond" (an earlier upgrade appended it) the old entry is dropped instead. Returns true if anything
	 * changed.
	 */
	public static boolean sandaraPond(List<WheelNode> wheel) {
		if (wheel == null) return false;
		boolean changed = false;
		for (WheelNode ring : walk(wheel)) {
			if (ring.children == null || ring.dynamic != null || !"Isles".equalsIgnoreCase(ring.label == null ? "" : ring.label)) continue;
			for (WheelNode isle : ring.children) {
				if (isle == null || isle.command == null || isle.arc == null || !key(isle.command).equals("warp sandara")) continue;
				boolean has = false;
				for (WheelNode a : isle.arc) if (a != null && a.command != null && key(a.command).equals("warp sandarapond")) has = true;
				for (java.util.Iterator<WheelNode> it = isle.arc.iterator(); it.hasNext(); ) {
					WheelNode a = it.next();
					if (a == null || a.command == null || !key(a.command).equals("warp pond")) continue;
					if (has) {
						it.remove();
					} else {
						a.command = "/warp sandarapond";
						if ("Mana Pond".equals(a.label)) a.label = "Sandara Pond";
						has = true;
					}
					changed = true;
				}
			}
		}
		return changed;
	}

	/**
	 * Also version 10: the Tangleroot warp is singular on the server ("Mini Boss at /warp tangleroot"); every
	 * "/warp tangleroots" becomes "/warp tangleroot", and a "Tangleroots" label on it "Tangleroot". Returns true if
	 * anything changed.
	 */
	public static boolean tanglerootSingular(List<WheelNode> wheel) {
		if (wheel == null) return false;
		boolean changed = false;
		for (WheelNode n : walk(wheel)) {
			if (n.command == null || !key(n.command).equals("warp tangleroots")) continue;
			n.command = "/warp tangleroot";
			if ("Tangleroots".equals(n.label)) n.label = "Tangleroot";
			changed = true;
		}
		return changed;
	}

	/**
	 * The version 13 upgrade (2026-10-30): the top-level Party quests slice becomes Party (/party), with Party quests
	 * first in its arc, ahead of what the arc already had. Returns true if anything changed.
	 */
	public static boolean partySlice(List<WheelNode> wheel) {
		if (wheel == null) return false;
		for (WheelNode n : wheel) {
			if (n == null || n.command == null || !key(n.command).equals("pquests")) continue;
			List<WheelNode> arc = new ArrayList<>();
			arc.add(WheelNode.leaf(n.label == null || n.label.isBlank() ? "Party quests" : n.label,
					n.icon == null ? "minecraft:writable_book" : n.icon, n.command));
			if (n.arc != null) for (WheelNode a : n.arc) if (a != null && !(a.command != null && key(a.command).equals("pquests"))) arc.add(a);
			n.label = "Party";
			n.icon = "minecraft:white_banner";
			n.command = "/party";
			n.arc = arc;
			return true;
		}
		return false;
	}

	/**
	 * The version 15 upgrade (2026-11-06): /craft on both wheels and /sell on Skyblock's. Survival's Craft joins the
	 * Heal slice's arc (Fly, Feed), or the end of the wheel without one; Skyblock's Sell and Craft become slices after
	 * Shop. Nothing is added for a command the wheel already runs anywhere. Returns the labels added.
	 */
	public static List<String> craftAndSell(List<WheelNode> wheel, boolean skyblock) {
		List<String> added = new ArrayList<>();
		if (wheel == null) return added;
		int at = afterShop(wheel);
		if (skyblock && !runs(wheel, "sell")) {
			wheel.add(at++, DefaultConfig.skyblockSellLeaf());
			added.add("Sell");
		} else if (skyblock && at < wheel.size() && isCommand(wheel.get(at), "sell")) {
			at++;
		}
		if (!runs(wheel, "craft")) {
			WheelNode heal = skyblock ? null : topLevel(wheel, "heal");
			if (heal != null) {
				if (heal.arc == null) heal.arc = new ArrayList<>();
				heal.arc.add(DefaultConfig.craftLeaf());
			} else {
				wheel.add(skyblock ? at : wheel.size(), DefaultConfig.craftLeaf());
			}
			added.add("Craft");
		}
		return added;
	}

	/**
	 * The version 16 upgrade (2026-11-06), Skyblock's wheel: a Heal slice (/heal) with Fly and Feed in its arc, as on
	 * Survival. A plain top-level Craft slice (version 15's) moves into that arc and Heal takes its place; otherwise
	 * Heal goes after Sell (or Shop). Fly, Feed or Craft that the wheel already runs elsewhere stay where they are.
	 * Nothing changes when the wheel already runs /heal. Returns true if anything changed.
	 */
	public static boolean skyblockHeal(List<WheelNode> wheel) {
		if (wheel == null || runs(wheel, "heal")) return false;
		int at = -1;
		for (int i = 0; i < wheel.size(); i++) {
			WheelNode n = wheel.get(i);
			if (isCommand(n, "craft") && (n.arc == null || n.arc.isEmpty()) && (n.children == null || n.children.isEmpty())
					&& n.dynamic == null) {
				wheel.remove(i); // Craft moves into Heal's arc
				at = i;
				break;
			}
		}
		WheelNode heal = WheelNode.leaf("Heal", "minecraft:golden_apple", "/heal");
		heal.arc = new ArrayList<>();
		for (WheelNode a : DefaultConfig.healArc()) if (!runs(wheel, key(a.command))) heal.arc.add(a);
		if (at < 0) {
			at = afterShop(wheel);
			if (at < wheel.size() && isCommand(wheel.get(at), "sell")) at++;
		}
		wheel.add(Math.min(at, wheel.size()), heal);
		return true;
	}

	/**
	 * The version 17 upgrade (2026-11-06), Skyblock's wheel: the island menu (/is) first in the Island slice's arc
	 * (the slice that runs /is go; a top-level slice before More without one), and a Boss event slice after Heal
	 * (else before More). Neither is added when the wheel already has it. Returns the labels added.
	 */
	public static List<String> skyblockBossAndIsland(List<WheelNode> wheel) {
		List<String> added = new ArrayList<>();
		if (wheel == null) return added;
		if (!runs(wheel, "is")) {
			WheelNode island = topLevel(wheel, "is go");
			if (island != null) {
				if (island.arc == null) island.arc = new ArrayList<>();
				island.arc.add(0, DefaultConfig.islandMenuLeaf());
			} else {
				wheel.add(beforeMore(wheel), DefaultConfig.islandMenuLeaf());
			}
			added.add("Island menu");
		}
		boolean hasBoss = false;
		for (WheelNode n : walk(wheel)) hasBoss |= n.isSlice() && "boss".equals(n.dynamic);
		if (!hasBoss) {
			int at = beforeMore(wheel);
			for (int i = 0; i < wheel.size(); i++) if (isCommand(wheel.get(i), "heal")) at = i + 1;
			wheel.add(at, DefaultConfig.bossSlice());
			added.add("Boss event");
		}
		return added;
	}

	private static int beforeMore(List<WheelNode> wheel) {
		for (int i = 0; i < wheel.size(); i++) {
			if (wheel.get(i) != null && DefaultConfig.MORE.equals(wheel.get(i).label)) return i;
		}
		return wheel.size();
	}

	private static boolean isCommand(WheelNode n, String command) {
		return n != null && n.command != null && key(n.command).equals(command);
	}

	private static boolean runs(List<WheelNode> wheel, String command) {
		for (WheelNode n : walk(wheel)) if (isCommand(n, command)) return true;
		return false;
	}

	private static WheelNode topLevel(List<WheelNode> wheel, String command) {
		for (WheelNode n : wheel) if (isCommand(n, command)) return n;
		return null;
	}

	/** The index just after the top-level Shop slice, else just before More, else the end. */
	private static int afterShop(List<WheelNode> wheel) {
		for (int i = 0; i < wheel.size(); i++) if (isCommand(wheel.get(i), "shop")) return i + 1;
		for (int i = 0; i < wheel.size(); i++) {
			if (wheel.get(i) != null && DefaultConfig.MORE.equals(wheel.get(i).label)) return i;
		}
		return wheel.size();
	}

	/** Every node of the tree: rings' and dynamic nodes' children and slices' outer tiers included. */
	static List<WheelNode> walk(List<WheelNode> roots) {
		List<WheelNode> out = new ArrayList<>();
		Deque<WheelNode> q = new ArrayDeque<>();
		for (WheelNode n : roots) if (n != null) q.add(n);
		while (!q.isEmpty()) {
			WheelNode n = q.poll();
			out.add(n);
			if (n.children != null) for (WheelNode c : n.children) if (c != null) q.add(c);
			if (n.outer != null) q.add(n.outer); // a slice's outer tiers count as part of the tree
			if (n.arc != null) for (WheelNode a : n.arc) if (a != null) q.add(a);
		}
		return out;
	}

	/** "/Warp  Crops " and "warp crops" are the same command. */
	static String key(String command) {
		String c = command.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		return c.startsWith("/") ? c.substring(1) : c;
	}
}
