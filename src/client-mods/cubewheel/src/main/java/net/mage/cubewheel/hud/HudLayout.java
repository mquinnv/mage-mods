package net.mage.cubewheel.hud;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Places HUD panels in a screen corner plus an offset. Panels sharing a corner stack away from it (down from
 * a top corner, up from a bottom one) with a {@link #GAP}, so they never overlap each other; {@link #reserve}
 * keeps them clear of something already drawn there (the tracker HUD at the top right). One instance per
 * frame. Pure: no Minecraft/Fabric imports.
 */
public final class HudLayout {
	public static final int GAP = 3;
	/** {@link Corner#parse} results by spelling. */
	private static final Map<String, Corner> PARSED = new java.util.concurrent.ConcurrentHashMap<>();

	public enum Corner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
		/** Centred above the hotbar (its offset Y clears the hotbar, hearts and armour); offset X nudges it right. */
		BOTTOM_CENTER,
		/** Exactly where it was dragged to: x/y are its top-left corner in GUI pixels; it stacks with nothing. */
		CUSTOM;

		/** "top_left", "Top-Left", "top left" ...; anything unknown is TOP_LEFT. */
		public static Corner parse(String s) {
			if (s == null) return TOP_LEFT;
			// Every panel's position is parsed every frame; the config holds only a few spellings.
			Corner known = PARSED.get(s);
			if (known != null) return known;
			Corner c = parseUncached(s);
			if (PARSED.size() < 64) PARSED.put(s, c);
			return c;
		}

		private static Corner parseUncached(String s) {
			String k = s.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
			for (Corner c : values()) {
				if (c.name().equals(k)) return c;
			}
			return TOP_LEFT;
		}

		/** The config spelling ("top_left"). */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		boolean top() {
			return this == TOP_LEFT || this == TOP_RIGHT;
		}

		boolean left() {
			return this == TOP_LEFT || this == BOTTOM_LEFT;
		}
	}

	/** Top-left corner and size of a placed panel. */
	public record Box(int x, int y, int w, int h) {}

	private final int screenW;
	private final int screenH;
	/** Per corner: distance from that corner's top/bottom edge already used by earlier panels. */
	private final Map<Corner, Integer> used = new EnumMap<>(Corner.class);

	public HudLayout(int screenW, int screenH) {
		this.screenW = screenW;
		this.screenH = screenH;
	}

	/** Marks the first {@code extent} pixels from the corner's top/bottom edge as taken. */
	public void reserve(Corner corner, int extent) {
		used.merge(corner, Math.max(0, extent), Math::max);
	}

	/** Places a w x h panel {@code offX}/{@code offY} from the corner, below/above earlier panels there. */
	public Box place(Corner corner, int offX, int offY, int w, int h) {
		if (corner == Corner.CUSTOM) {
			return new Box(clamp(offX, 0, Math.max(0, screenW - w)), clamp(offY, 0, Math.max(0, screenH - h)), w, h);
		}
		int start = used.getOrDefault(corner, 0);
		int fromEdge = Math.max(Math.max(0, offY), start == 0 ? 0 : start + GAP);
		used.put(corner, fromEdge + h);
		int x = corner == Corner.BOTTOM_CENTER ? (screenW - w) / 2 + offX
				: corner.left() ? Math.max(0, offX) : screenW - Math.max(0, offX) - w;
		int y = corner.top() ? fromEdge : screenH - fromEdge - h;
		x = clamp(x, 0, Math.max(0, screenW - w));
		y = clamp(y, 0, Math.max(0, screenH - h));
		return new Box(x, y, w, h);
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
