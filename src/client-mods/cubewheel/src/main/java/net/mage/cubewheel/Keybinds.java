package net.mage.cubewheel;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** CubeWheel key mappings (category "cubewheel:main", label key.category.cubewheel.main). */
public final class Keybinds {
	private Keybinds() {}

	public static KeyMapping wheel;
	public static KeyMapping reload;
	public static KeyMapping trackerHud;
	public static KeyMapping trackerPicker;
	public static KeyMapping capture;
	public static KeyMapping refresh;
	public static KeyMapping eventsHud;
	public static KeyMapping svaCatalog;
	public static KeyMapping jobsPanel;
	public static KeyMapping arrange;

	public static void register() {
		KeyMapping.Category category =
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath(CubeWheelClient.MOD_ID, "main"));
		wheel = key("key.cubewheel.wheel", InputConstants.KEY_G, category);
		reload = key("key.cubewheel.reload", InputConstants.UNKNOWN.getValue(), category);
		trackerHud = key("key.cubewheel.tracker_hud", InputConstants.UNKNOWN.getValue(), category);
		trackerPicker = key("key.cubewheel.tracker_picker", InputConstants.UNKNOWN.getValue(), category);
		capture = key("key.cubewheel.capture", InputConstants.UNKNOWN.getValue(), category);
		refresh = key("key.cubewheel.refresh", InputConstants.UNKNOWN.getValue(), category);
		eventsHud = key("key.cubewheel.events_hud", InputConstants.UNKNOWN.getValue(), category);
		svaCatalog = key("key.cubewheel.sva_catalog", InputConstants.UNKNOWN.getValue(), category);
		jobsPanel = key("key.cubewheel.jobs_panel", InputConstants.UNKNOWN.getValue(), category);
		arrange = key("key.cubewheel.arrange", InputConstants.UNKNOWN.getValue(), category);
	}

	private static KeyMapping key(String name, int code, KeyMapping.Category category) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYSYM, code, category));
	}
}
