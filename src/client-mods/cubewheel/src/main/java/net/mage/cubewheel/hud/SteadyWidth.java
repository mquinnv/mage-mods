package net.mage.cubewheel.hud;

/**
 * A width that only grows until {@link #reset}. Lines whose digits change every frame (coordinates, fps, speed)
 * would otherwise make the panel twitch, and with it the panel's second column and the other panels in its corner.
 * Pure: no Minecraft/Fabric imports.
 */
public final class SteadyWidth {
	private int widest;

	/** {@code natural} or the widest seen since the last reset, whichever is wider. */
	public int apply(int natural) {
		widest = Math.max(widest, natural);
		return widest;
	}

	/** Start over (another world): the next width is taken as it is. */
	public void reset() {
		widest = 0;
	}
}
