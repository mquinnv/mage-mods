package net.mage.cubewheel.tracker.local;

/**
 * Reeling in counts as a catch exactly when the bobber is biting (the server's retrieve awards loot only
 * then). Pure: no Minecraft/Fabric imports.
 */
public final class FishDetector {
	private FishDetector() {}

	public static boolean onRodUse(boolean hasHook, boolean biting) {
		return hasHook && biting;
	}
}
