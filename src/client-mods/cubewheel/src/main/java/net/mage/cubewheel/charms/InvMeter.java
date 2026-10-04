package net.mage.cubewheel.charms;

/** How full a set of slots is: fine, 80% or more, or full. Pure: no Minecraft/Fabric imports. */
public final class InvMeter {
	public enum Level { OK, HIGH, FULL }

	private InvMeter() {}

	public static Level level(int used, int total) {
		if (total <= 0 || used >= total) return Level.FULL;
		return used * 5 >= total * 4 ? Level.HIGH : Level.OK;
	}
}
