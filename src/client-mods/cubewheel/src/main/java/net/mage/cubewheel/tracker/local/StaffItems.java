package net.mage.cubewheel.tracker.local;

import java.util.Locale;

/**
 * ManaCube's ability weapons that shoot on right-click ("Phoenix Staff", "Arcane Staff"): no projectile entity, the
 * server damages mobs in a wide cone ahead (343 captured shots, 2026-10-12..14: hits up to ~16 blocks out and ~45
 * degrees off the crosshair, aiming at the sky hits as often as aiming at a mob). Pure: no Minecraft/Fabric imports.
 */
public final class StaffItems {
	/** How far a staff shot reaches (blocks from the eye). */
	public static final double RANGE = 16;
	/** Half-angle of the cone a staff shot sweeps, in degrees. */
	public static final double CONE_DEGREES = 60;
	public static final double CONE_COS = Math.cos(Math.toRadians(CONE_DEGREES));

	private StaffItems() {}

	/** Is a held item of this (display) name a staff? Colour codes are ignored. */
	public static boolean isStaff(String itemName) {
		if (itemName == null) return false;
		return itemName.replaceAll("§.", "").toLowerCase(Locale.ROOT).contains("staff");
	}

	/**
	 * Is a point {@code (dx, dy, dz)} from the eye inside the cone of a shot along the unit look vector
	 * {@code (lx, ly, lz)}: within {@link #RANGE} and at most {@link #CONE_DEGREES} off it?
	 */
	public static boolean inCone(double dx, double dy, double dz, double lx, double ly, double lz) {
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len > RANGE) return false;
		if (len < 1e-6) return true;
		double cos = (dx * lx + dy * ly + dz * lz) / len;
		return cos >= CONE_COS;
	}
}
