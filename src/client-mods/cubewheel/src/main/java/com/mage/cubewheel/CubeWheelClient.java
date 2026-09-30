package com.mage.cubewheel;

import com.google.gson.JsonElement;
import com.mage.cubewheel.capture.CaptureLog;
import com.mage.cubewheel.config.ConfigStore;
import com.mage.cubewheel.config.CubeWheelConfig;
import com.mage.cubewheel.config.WheelNode;
import com.mage.cubewheel.homes.HomesCache;
import com.mage.cubewheel.homes.HomesFetcher;
import com.mage.cubewheel.tracker.ContainerHook;
import com.mage.cubewheel.tracker.TrackerHud;
import com.mage.cubewheel.tracker.TrackerScreen;
import com.mage.cubewheel.tracker.TrackerStore;
import com.mage.cubewheel.wheel.RadialScreen;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CubeWheelClient implements ClientModInitializer {
	public static final String MOD_ID = "cubewheel";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	private static ConfigStore config;
	private static HomesCache homes;
	private static HomesFetcher homesFetcher;
	private static TrackerStore tracker;
	private static CaptureLog capture;

	public static ConfigStore config() { return config; }

	/** Per-server homes cache; null only before onInitializeClient (callers treat null as "no homes"). */
	public static HomesCache homes() { return homes; }

	/** Tracked progress; null only before onInitializeClient. */
	public static TrackerStore tracker() { return tracker; }

	/** Capture-mode log (off by default); null only before onInitializeClient. */
	public static CaptureLog capture() { return capture; }

	@Override
	public void onInitializeClient() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		config = new ConfigStore(configDir.resolve("cubewheel.json"));
		String err = config.reload();
		if (err != null) LOG.error("[cubewheel] config load failed, using defaults/previous: {}", err);
		homes = new HomesCache(configDir.resolve("cubewheel-homes.json"));
		homes.load();
		homesFetcher = new HomesFetcher(homes);
		RadialScreen.childrenProvider = homesFetcher::childrenFor;
		RadialScreen.placeholderPending = () -> homesFetcher.isLoading(System.currentTimeMillis());
		tracker = new TrackerStore(configDir.resolve("cubewheel-tracker.json"));
		tracker.load();
		capture = new CaptureLog(configDir.resolve("cubewheel-captures"));
		// Capture listens first and always allows, so it also records the /homes replies the next listener hides.
		ClientReceiveMessageEvents.ALLOW_GAME.register(CubeWheelClient::captureChat);
		ClientReceiveMessageEvents.ALLOW_GAME.register(homesFetcher::onGameMessage);
		ClientSendMessageEvents.COMMAND.register(homesFetcher::onCommand);
		ContainerHook.register();
		TrackerHud.register();
		Keybinds.register();
		ClientTickEvents.END_CLIENT_TICK.register(CubeWheelClient::onEndTick);
		LOG.info("[cubewheel] initialised");
	}

	private static void onEndTick(Minecraft mc) {
		try {
			handleWheelKey(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] wheel key handler failed", e);
		}
		try {
			handleReloadKey(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] reload key handler failed", e);
		}
		try {
			handleTrackerHudKey(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] tracker HUD key handler failed", e);
		}
		try {
			handleTrackerPickerKey(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] tracker picker key handler failed", e);
		}
		try {
			handleCaptureKey(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] capture key handler failed", e);
		}
	}

	/** True once per tick if the key was pressed; queued extra presses are dropped. */
	private static boolean pressed(KeyMapping key) {
		if (!key.consumeClick()) return false;
		while (key.consumeClick()) {
			// drain
		}
		return true;
	}

	private static void handleTrackerHudKey(Minecraft mc) {
		if (!pressed(Keybinds.trackerHud)) return;
		CubeWheelConfig cfg = config.current();
		cfg.tracker.hudVisible = !cfg.tracker.hudVisible;
		try {
			config.save();
		} catch (IOException e) {
			LOG.warn("[cubewheel] could not save tracker HUD setting: {}", e.toString());
		}
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(cfg.tracker.hudVisible ? "Tracker HUD ON" : "Tracker HUD OFF"));
		}
	}

	private static void handleTrackerPickerKey(Minecraft mc) {
		if (!pressed(Keybinds.trackerPicker)) return;
		if (mc.gui.screen() != null) return;
		mc.gui.setScreen(new TrackerScreen());
	}

	private static void handleCaptureKey(Minecraft mc) {
		if (!pressed(Keybinds.capture)) return;
		capture.toggle();
		LOG.info("[cubewheel] capture {} ({})", capture.enabled() ? "on" : "off", capture.dir());
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(
					capture.enabled() ? "Capture ON → config/cubewheel-captures" : "Capture OFF"));
		}
	}

	/** ALLOW_GAME listener registered before the homes one; never hides anything. */
	private static boolean captureChat(Component message, boolean overlay) {
		try {
			if (overlay || message == null || !capture.enabled()) return true;
			if (!ServerGate.active(config.current())) return true;
			capture.chat(componentJson(message), message.getString(), System.currentTimeMillis());
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] chat capture failed", e);
		}
		return true;
	}

	/** The component's JSON form (registry-aware when in a world); falls back to toString(). */
	private static String componentJson(Component message) {
		Minecraft mc = Minecraft.getInstance();
		DynamicOps<JsonElement> ops = mc.level != null
				? mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE)
				: JsonOps.INSTANCE;
		return ComponentSerialization.CODEC.encodeStart(ops, message).result()
				.map(JsonElement::toString)
				.orElseGet(message::toString);
	}

	private static void handleWheelKey(Minecraft mc) {
		if (!pressed(Keybinds.wheel)) return; // one press opens one wheel
		if (mc.gui.screen() != null || mc.player == null) return;
		CubeWheelConfig cfg = config.current();
		if (!cfg.enabled) return;
		if (!ServerGate.active(cfg)) {
			mc.player.sendOverlayMessage(Component.literal("CubeWheel is only active on ManaCube"));
			return;
		}
		WheelNode root = WheelNode.ring("CubeWheel", null, cfg.wheel.toArray(WheelNode[]::new));
		mc.gui.setScreen(new RadialScreen(root, Keybinds.wheel));
	}

	private static void handleReloadKey(Minecraft mc) {
		if (!pressed(Keybinds.reload)) return;
		String err = config.reload();
		if (err != null) LOG.warn("[cubewheel] reload failed: {}", err);
		if (mc.player == null) return;
		Component msg = err != null
				? Component.literal("[CubeWheel] " + err).withStyle(ChatFormatting.RED)
				: Component.literal("[CubeWheel] config reloaded").withStyle(ChatFormatting.GREEN);
		mc.player.sendSystemMessage(msg);
	}
}
