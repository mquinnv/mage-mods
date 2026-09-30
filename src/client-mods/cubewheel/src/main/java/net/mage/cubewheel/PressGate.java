package net.mage.cubewheel;

/**
 * One action per physical key press. Minecraft (26.2 KeyboardHandler.keyPress) counts every GLFW
 * key-repeat event as another click while a key is held, so holding a toggle key past the OS repeat delay
 * toggled it twice. This fires on the first click of a press and re-arms only once the key was seen up.
 * Pure: no Minecraft/Fabric imports.
 */
public final class PressGate {
	private boolean armed = true;

	/**
	 * Called once per tick with whether the key was clicked since the last tick (clicks drained) and
	 * whether it is held now; returns true when the action should run.
	 */
	public boolean fire(boolean clicked, boolean down) {
		boolean fire = clicked && armed;
		if (fire) armed = false;
		if (!down) armed = true;
		return fire;
	}
}
