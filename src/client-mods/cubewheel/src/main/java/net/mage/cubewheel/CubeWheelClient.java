package net.mage.cubewheel;

import net.mage.cubewheel.config.DefaultConfig;

import com.google.gson.JsonElement;
import net.mage.cubewheel.boosters.BoosterWatcher;
import net.mage.cubewheel.capture.CaptureLog;
import net.mage.cubewheel.config.ConfigStore;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.config.WheelNode;
import net.mage.cubewheel.cooldown.CooldownWatcher;
import net.mage.cubewheel.cooldown.McmmoWatcher;
import net.mage.cubewheel.events.EventHud;
import net.mage.cubewheel.homes.HomesCache;
import net.mage.cubewheel.homes.HomesFetcher;
import net.mage.cubewheel.hud.PanelsHud;
import net.mage.cubewheel.live.LiveWatcher;
import net.mage.cubewheel.mixin.BossHealthOverlayAccessor;
import net.mage.cubewheel.mixin.HudAccessor;
import net.mage.cubewheel.mixin.LerpingBossEventAccessor;
import net.mage.cubewheel.sidebar.SidebarWatcher;
import net.mage.cubewheel.sva.mc.SvaClient;
import net.mage.cubewheel.tracker.ContainerHook;
import net.mage.cubewheel.tracker.JobsPanel;
import net.mage.cubewheel.tracker.RefreshController;
import net.mage.cubewheel.tracker.TrackerHud;
import net.mage.cubewheel.tracker.TrackerPanel;
import net.mage.cubewheel.tracker.TrackerScreen;
import net.mage.cubewheel.tracker.TrackerStore;
import net.mage.cubewheel.tracker.local.mc.LocalSignals;
import net.mage.cubewheel.wheel.RadialScreen;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
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
	private static final SidebarWatcher sidebar = new SidebarWatcher();
	private static final java.util.Map<KeyMapping, PressGate> PRESS_GATES = new java.util.IdentityHashMap<>();
	/** Set once if the optional HUD accessor mixins are unusable; action-bar/boss-bar capture then stays off. */
	private static boolean hudCaptureDisabled;

	public static ConfigStore config() { return config; }

	/** Per-server homes cache; null only before onInitializeClient (callers treat null as "no homes"). */
	public static HomesCache homes() { return homes; }

	/** Tracked progress; null only before onInitializeClient. */
	public static TrackerStore tracker() { return tracker; }

	/** Capture-mode log (off by default); null only before onInitializeClient. */
	public static CaptureLog capture() { return capture; }

	/** Live scoreboard-sidebar reader. */
	public static SidebarWatcher sidebar() { return sidebar; }

	@Override
	public void onInitializeClient() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		config = new ConfigStore(configDir.resolve("cubewheel.json"));
		String err = config.reload();
		if (err != null) LOG.error("[cubewheel] config load failed, using defaults/previous: {}", err);
		for (String w : config.warnings()) LOG.warn("[cubewheel] config: {}", w);
		homes = new HomesCache(configDir.resolve("cubewheel-homes.json"));
		homes.load();
		homesFetcher = new HomesFetcher(homes);
		RadialScreen.childrenProvider = homesFetcher::childrenFor; // (node, userInitiated)
		RadialScreen.placeholderPending = () -> homesFetcher.isLoading(System.currentTimeMillis());
		tracker = new TrackerStore(configDir.resolve("cubewheel-tracker.json"));
		tracker.load();
		capture = new CaptureLog(configDir.resolve("cubewheel-captures"));
		// Capture listens first and always allows, so it also records the /homes replies the next listener hides.
		ClientReceiveMessageEvents.ALLOW_GAME.register(CubeWheelClient::captureChat);
		ClientReceiveMessageEvents.ALLOW_GAME.register(homesFetcher::onGameMessage);
		BoosterWatcher.init(configDir);
		ClientReceiveMessageEvents.ALLOW_GAME.register(BoosterWatcher::onGameMessage);
		LiveWatcher.init(configDir);
		ClientReceiveMessageEvents.ALLOW_GAME.register(LiveWatcher::onGameMessage);
		ClientSendMessageEvents.COMMAND.register(LiveWatcher::onCommand);
		McmmoWatcher.init(configDir);
		ClientReceiveMessageEvents.ALLOW_GAME.register(McmmoWatcher::onGameMessage);
		ClientSendMessageEvents.COMMAND.register(homesFetcher::onCommand);
		ClientSendMessageEvents.COMMAND.register(CubeWheelClient::noteCommand);
		ContainerHook.register();
		LocalSignals.register(); // after tracker and capture exist
		ClientReceiveMessageEvents.ALLOW_GAME.register(LocalSignals::onGameMessage);
		CooldownWatcher.register();
		TrackerHud.register();
		// Panels in one corner stack in this order. The ones that come and go (boosters, cooldowns) go last so
		// they don't push the always-present Jobs/Tracker panels up and down.
		net.mage.cubewheel.status.StatusPanel.register();
		PanelsHud.add(net.mage.cubewheel.status.StatusPanel::panel, c -> c.status.position, DefaultConfig::statusPosition); // top left, above Jobs
		PanelsHud.add(EventHud::panel, c -> c.events.position, DefaultConfig::eventsPosition);
		PanelsHud.add(JobsPanel::panel, c -> c.tracker.jobsPanel.position, DefaultConfig::jobsPanelPosition);
		PanelsHud.add(TrackerPanel::panel, c -> c.tracker.position, DefaultConfig::trackerPosition); // under the Jobs panel
		PanelsHud.add(BoosterWatcher::panel, c -> c.boosters.position, DefaultConfig::boostersPosition);
		PanelsHud.add(CooldownWatcher::panel, c -> c.cooldowns.position, DefaultConfig::cooldownsPosition);
		PanelsHud.add(net.mage.cubewheel.charms.CharmsPanel::panel, c -> c.charms.position, DefaultConfig::charmsPosition); // above the hotbar, in place of schrumboHUD
		PanelsHud.register();
		SvaClient.init(configDir);
		Keybinds.register();
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			try {
				RefreshController.sampleInput(mc);
			} catch (RuntimeException e) {
				LOG.error("[cubewheel] refresh input sample failed", e);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(CubeWheelClient::onEndTick);
		// Sidebar-driven changes are saved at most every 10 s; write the rest when leaving or quitting.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> sidebar.flush());
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> sidebar.flush());
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
			handleEventsHudKey(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] event HUD key handler failed", e);
		}
		try {
			handleJobsPanelKey(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] jobs panel key handler failed", e);
		}
		try {
			if (pressed(Keybinds.arrange) && mc.gui.screen() == null) mc.gui.setScreen(new net.mage.cubewheel.hud.ArrangeScreen());
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] arrange key handler failed", e);
		}
		EventHud.tick(mc); // catches and logs its own failures
		try {
			handleTrackerPickerKey(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] tracker picker key handler failed", e);
		}
		try {
			if (pressed(Keybinds.svaCatalog)) SvaClient.openCatalog(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] SVA catalog key handler failed", e);
		}
		try {
			handleCaptureKey(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] capture key handler failed", e);
		}
		try {
			pollHudCapture(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] action-bar/boss-bar capture failed", e);
		}
		try {
			if (pressed(Keybinds.refresh)) RefreshController.request(mc, false);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] refresh key handler failed", e);
		}
		try {
			RefreshController.tick(mc);
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] tracker refresh tick failed", e);
		}
		sidebar.tick(mc); // catches and logs its own failures
	}

	/** COMMAND listener: remembers the last command so captured menus can say what opened them. */
	private static void noteCommand(String command) {
		try {
			capture.noteCommand(command, System.currentTimeMillis());
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] command note failed", e);
		}
	}

	/**
	 * True once per physical press: queued clicks are drained, and key-repeat clicks while the key stays
	 * held are ignored (see {@link PressGate}). Call once per tick per key.
	 */
	private static boolean pressed(KeyMapping key) {
		boolean clicked = false;
		while (key.consumeClick()) clicked = true;
		return PRESS_GATES.computeIfAbsent(key, k -> new PressGate()).fire(clicked, key.isDown());
	}

	private static void handleTrackerHudKey(Minecraft mc) {
		if (!pressed(Keybinds.trackerHud)) return;
		CubeWheelConfig cfg = config.current();
		cfg.tracker.hudVisible = !cfg.tracker.hudVisible;
		saveToggle(mc, cfg.tracker.hudVisible ? "Tracker HUD ON" : "Tracker HUD OFF");
	}

	private static void handleEventsHudKey(Minecraft mc) {
		if (!pressed(Keybinds.eventsHud)) return;
		CubeWheelConfig cfg = config.current();
		cfg.events.hudVisible = !cfg.events.hudVisible;
		saveToggle(mc, cfg.events.hudVisible ? "Event HUD ON" : "Event HUD OFF");
	}

	private static void handleJobsPanelKey(Minecraft mc) {
		if (!pressed(Keybinds.jobsPanel)) return;
		CubeWheelConfig cfg = config.current();
		cfg.tracker.jobsPanel.enabled = !cfg.tracker.jobsPanel.enabled;
		saveToggle(mc, cfg.tracker.jobsPanel.enabled ? "Jobs panel ON" : "Jobs panel OFF (jobs back on the tracker HUD)");
	}

	/** Saves a toggled HUD setting and confirms it on the action bar. */
	private static void saveToggle(Minecraft mc, String confirmation) {
		// After a failed load the in-memory config is not the file on disk: saving would overwrite
		// the user's (broken) cubewheel.json with defaults/the previous config. Toggle in memory only.
		boolean saved = config.lastLoadOk();
		if (saved) {
			try {
				config.save();
			} catch (IOException e) {
				LOG.warn("[cubewheel] could not save HUD setting: {}", e.toString());
			}
		}
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(saved
					? confirmation
					: "CubeWheel: HUD toggled for this session (config has errors, not saved)"));
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
			// Chat, not the action bar: servers (ManaCube included) overwrite the action bar constantly.
			mc.player.sendSystemMessage(Component.literal(capture.enabled()
					? "[CubeWheel] Capture ON → config/cubewheel-captures"
					: "[CubeWheel] Capture OFF").withStyle(capture.enabled() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		}
	}

	/** ALLOW_GAME listener registered before the homes one; never hides anything. */
	private static boolean captureChat(Component message, boolean overlay) {
		try {
			if (message == null || !capture.enabled()) return true;
			if (!ServerGate.active(config.current())) return true;
			capture.chat(componentJson(message), message.getString(), overlay, System.currentTimeMillis());
		} catch (RuntimeException e) {
			LOG.error("[cubewheel] chat capture failed", e);
		}
		return true;
	}

	/**
	 * While capturing, records the HUD's action-bar text and the boss bars each tick (CaptureLog writes
	 * only changes). Read-only: the SetActionBarText packet and boss events never reach ALLOW_GAME.
	 */
	private static void pollHudCapture(Minecraft mc) {
		if (hudCaptureDisabled) return;
		if (!capture.enabled() || mc.player == null || !ServerGate.active(config.current())) return;
		long now = System.currentTimeMillis();
		String actionBarText;
		List<CaptureLog.BossBar> bars = new ArrayList<>();
		try {
			Component actionBar = ((HudAccessor) mc.gui.hud).cubewheel$getOverlayMessage();
			actionBarText = actionBar == null ? null : actionBar.getString();
			for (LerpingBossEvent e : ((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).cubewheel$getEvents().values()) {
				bars.add(new CaptureLog.BossBar(e.getName().getString(), ((LerpingBossEventAccessor) e).cubewheel$getTargetPercent()));
			}
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			// The accessor mixins are optional (required=false): if one did not apply (e.g. a field was
			// renamed in a Minecraft update) the casts/calls fail here. Disable once, don't spam per tick.
			hudCaptureDisabled = true;
			LOG.warn("[cubewheel] action-bar/boss-bar capture disabled for this session (mixin accessor unavailable): {}", t.toString());
			return;
		}
		capture.actionBar(actionBarText, now);
		capture.bossBars(bars, now);
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
		if (err == null) {
			for (String w : config.warnings()) {
				mc.player.sendSystemMessage(Component.literal("[CubeWheel] " + w).withStyle(ChatFormatting.YELLOW));
			}
		}
	}
}
