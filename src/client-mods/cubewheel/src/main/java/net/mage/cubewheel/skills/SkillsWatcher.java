package net.mage.cubewheel.skills;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.capture.CaptureLog;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.tracker.ContainerScanner;
import net.mage.cubewheel.tracker.local.ActionBarFeed;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Minecraft adapter for the mcMMO skills panel. Purely passive: it reads boss bars, chat and action-bar lines the
 * server already pushes, the sidebar's power level, and the {@code /mcmmo} menu when the player opens it. It sends
 * nothing; the one command of this feature ({@code /mcmmo}) goes out on a keypress in {@code CubeWheelClient}.
 * Active only in ManaCube Survival with {@code skills.enabled}. Every hook is guarded; after {@link #MAX_FAILURES}
 * failures the feature switches itself off for the session.
 */
public final class SkillsWatcher {
	private static final int MAX_FAILURES = 10;
	private static final long SAVE_INTERVAL_MS = 10_000;

	private static SkillStore store;
	private static int failures;
	private static long lastSaveAt;
	private static double lastPower = -1;
	private static boolean saveFailureLogged;

	private SkillsWatcher() {}

	public static void init(Path configDir) {
		Path file = configDir.resolve("cubewheel-skills.json");
		store = new SkillStore(file);
		if (!store.load()) setAsideCorrupt(file);
	}

	/** A malformed state file would be overwritten by the next save: keep it as {@code <file>.corrupt} and start empty. */
	private static void setAsideCorrupt(Path file) {
		try {
			if (!Files.exists(file)) return;
			Path aside = file.resolveSibling(file.getFileName() + ".corrupt");
			CubeWheelClient.LOG.warn("[cubewheel] mcMMO skills file {} is unreadable; moving it to {} and starting empty", file, aside);
			Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | RuntimeException e) {
			CubeWheelClient.LOG.warn("[cubewheel] could not set aside the unreadable mcMMO skills file: {}", e.toString());
		}
	}

	/** The store, or null before {@link #init}. */
	static SkillStore store() {
		return store;
	}

	/** Lower-case session username; null before login. */
	static String account() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getUser() == null ? null : mc.getUser().getName().toLowerCase(Locale.ROOT);
	}

	static boolean active() {
		if (store == null || failures >= MAX_FAILURES) return false;
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		return cfg.skills.enabled && ServerGate.survival(cfg);
	}

	/** ClientReceiveMessageEvents.ALLOW_GAME: always returns true (never hides the message). */
	public static boolean onGameMessage(Component message, boolean overlay) {
		try {
			// Overlay (action-bar) messages also reach onActionBarEvent; reading them here too would double them.
			if (message == null || overlay || !active()) return true;
			handle(message.getString(), System.currentTimeMillis());
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
		return true;
	}

	/** From LocalSignals' action-bar hooks. A duplicate level-up is harmless: the store's levelUp is idempotent. */
	public static void onActionBarEvent(ActionBarFeed.Source source, Component message) {
		try {
			if (message == null || !active()) return;
			handle(message.getString(), System.currentTimeMillis());
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
	}

	private static void handle(String text, long now) {
		String name = account();
		if (name == null) return;
		// The store key is lower-case; the broadcast carries the name as typed, so the parser gets the raw one.
		Optional<SkillTextParser.Message> m = SkillTextParser.parse(text, Minecraft.getInstance().getUser().getName());
		if (m.isEmpty()) return;
		SkillTextParser.Message msg = m.get();
		if (msg instanceof SkillTextParser.LevelUp l) store.levelUp(name, l.skill(), l.level(), now);
		else if (msg instanceof SkillTextParser.PowerLevel p) store.power(name, p.power(), now);
		// SkillMilestone carries nothing the level-up line did not.
	}

	/** END_CLIENT_TICK: boss bars and the sidebar power level into the store, then a throttled save. */
	public static void tick(Minecraft mc) {
		try {
			if (!active()) return;
			String name = account();
			if (name == null) return;
			long now = System.currentTimeMillis();
			List<CaptureLog.BossBar> bars = CubeWheelClient.bossBars(mc);
			if (bars != null) {
				for (CaptureLog.BossBar bar : bars) {
					String text = bar.name();
					if (text == null || text.length() > 80) continue;
					Optional<SkillBarParser.Reading> r = SkillBarParser.parse(strip(text));
					if (r.isPresent() && changed(name, r.get())) store.bar(name, r.get(), now);
				}
			}
			Double power = CubeWheelClient.sidebar().values().get("Skills");
			if (power != null && power != lastPower) {
				lastPower = power;
				store.power(name, Math.round(power), now);
			}
			if (store.dirty() && now - lastSaveAt >= SAVE_INTERVAL_MS) save(now);
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
	}

	/** True when the reading's level or XP differs from what the store holds for that skill. */
	private static boolean changed(String name, SkillBarParser.Reading r) {
		SkillStore.State s = store.account(name).skills().get(r.skill());
		return s == null || s.level() != r.level() || s.xp() != r.xp();
	}

	/** {@code §x} colour codes removed (the live Component text may carry them). */
	private static String strip(String s) {
		return s.indexOf('§') < 0 ? s : s.replaceAll("§.", "");
	}

	/** From ContainerHook: the {@code /mcmmo} menu, when the player opened it. Survival only (the caller gates). */
	public static void onMenu(String title, List<ContainerScanner.ItemView> items, long now) {
		try {
			if (!active() || items == null || items.isEmpty()) return;
			String name = account();
			if (name == null) return;
			List<SkillsMenu.Item> menu = new ArrayList<>(items.size());
			for (ContainerScanner.ItemView v : items) menu.add(new SkillsMenu.Item(v.name(), v.lore()));
			if (!SkillsMenu.isSkillsMenu(menu)) return;
			store.menu(name, SkillsMenu.parse(menu), now);
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
	}

	/** Disconnect / quit: writes what is still unsaved. */
	public static void flush() {
		try {
			if (store != null && store.dirty()) save(System.currentTimeMillis());
		} catch (RuntimeException | LinkageError e) {
			fail(e);
		}
	}

	/** The file is small, so the save is synchronous; an IOException is logged once. */
	private static void save(long now) {
		lastSaveAt = now;
		try {
			store.save();
			store.clearDirty();
		} catch (IOException e) {
			if (!saveFailureLogged) {
				saveFailureLogged = true;
				CubeWheelClient.LOG.warn("[cubewheel] could not save mcMMO skills: {}", e.toString());
			}
		}
	}

	private static void fail(Throwable e) {
		if (failures == 0) CubeWheelClient.LOG.error("[cubewheel] mcMMO skills hook failed", e);
		if (++failures == MAX_FAILURES) CubeWheelClient.LOG.warn("[cubewheel] mcMMO skills panel switched off for this session after repeated failures");
	}
}
