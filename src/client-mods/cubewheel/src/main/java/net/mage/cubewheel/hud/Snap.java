package net.mage.cubewheel.hud;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where a panel dragged on the arrange screen would land if it rejoined a corner's stack, and whether it was
 * dropped close enough to do so. A dragged panel becomes "custom" (exact coordinates) and stacks with nothing, so
 * every panel moved by hand collided on its own (Michael 2026-10-08: "i keep having to move things around"). Dropping
 * one within {@link #RANGE} of the slot a corner's stack would give it puts it back in that corner instead, where it
 * stacks with the others again. Pure: no Minecraft/Fabric imports.
 */
public final class Snap {
	/** How close (|dx| + |dy|, GUI px) a drop must be to a stack slot to take it. */
	public static final int RANGE = 12;
	/** The corners a panel can rejoin ({@link HudLayout.Corner#CUSTOM} is the one it leaves). */
	static final List<HudLayout.Corner> CORNERS = List.of(HudLayout.Corner.TOP_LEFT, HudLayout.Corner.TOP_RIGHT,
			HudLayout.Corner.BOTTOM_LEFT, HudLayout.Corner.BOTTOM_RIGHT, HudLayout.Corner.BOTTOM_CENTER);

	/** A panel as placed last frame: which source it is, the corner it is in, and its box. */
	public record Other(int source, HudLayout.Corner corner, HudLayout.Box box) {}

	/** A corner's offsets for the dragged panel (its default ones there, or the corner's usual ones). */
	public interface Offsets {
		int[] of(HudLayout.Corner corner);
	}

	/** The corner a drop rejoins, the offsets to store, and where the panel then sits. */
	public record Target(HudLayout.Corner corner, int offX, int offY, HudLayout.Box box) {}

	private Snap() {}

	/**
	 * The stack slot nearest to where source {@code dragged} (now at {@code dropped}) was dropped, within
	 * {@link #RANGE}. Panels in one corner stack in registration order, so the slot is below (or above, in a bottom
	 * corner) the corner's panels registered before it only; {@code reserved} is what else the corner's edge already
	 * holds (the effect icons at the top right).
	 */
	public static Optional<Target> find(int dragged, HudLayout.Box dropped, List<Other> others,
			Map<HudLayout.Corner, Integer> reserved, int screenW, int screenH, Offsets offsets) {
		Target best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (HudLayout.Corner c : CORNERS) {
			HudLayout layout = new HudLayout(screenW, screenH);
			layout.reserve(c, reserved.getOrDefault(c, 0));
			for (Other o : others) {
				if (o.corner() != c || o.source() == dragged || o.source() > dragged) continue;
				layout.reserve(c, c.top() ? o.box().y() + o.box().h() : screenH - o.box().y());
			}
			int[] off = offsets.of(c);
			HudLayout.Box slot = layout.place(c, off[0], off[1], dropped.w(), dropped.h());
			int distance = Math.abs(slot.x() - dropped.x()) + Math.abs(slot.y() - dropped.y());
			if (distance <= RANGE && distance < bestDistance) {
				best = new Target(c, off[0], off[1], slot);
				bestDistance = distance;
			}
		}
		return Optional.ofNullable(best);
	}
}
