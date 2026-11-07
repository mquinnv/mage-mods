package net.mage.cubewheel.live;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.sidebar.Gamemode;
import net.mage.cubewheel.tracker.ContainerScanner.ItemView;
import net.mage.cubewheel.wheel.SliceViews;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Minecraft adapter for the live wheel slices: feeds chat to {@link CowParser} / {@link BossParser}, the /cow
 * menu's lore to {@link CowStore}, and registers the Fly, Daily reward and Boss event slice providers. Purely
 * passive: never hides a message, never sends anything. Chat and menus are read only in ManaCube Survival,
 * except boss announcements, which Skyblock sends too (its boss is kept in {@code cubewheel-boss-skyblock.json}).
 */
public final class LiveWatcher {
	/** A menu opened this soon after you sent /cow is read as the /cow menu, whatever its title. */
	private static final long COW_MENU_WINDOW_MS = 10_000;
	/** A "You received N Daily Crate Key(s)" line this soon after /cow is read as your claim from its menu. */
	private static final long COW_CLAIM_WINDOW_MS = 300_000;

	private static CowStore cow;
	private static final BossSlice boss = new BossSlice();
	private static Path bossFile;
	private static final BossSlice bossSkyblock = new BossSlice();
	private static Path bossSkyblockFile;
	private static final TpaSlice tpa = new TpaSlice();
	private static long cowSentAt = Long.MIN_VALUE;
	private static VaultPages vaults;
	private static long pvSentAt = Long.MIN_VALUE;
	/** The page the last /pv asked for ("/pv 3" -> 3, "/pv" -> 1). */
	private static int pvPage = 1;
	private static boolean chatFailureLogged;
	private static boolean menuFailureLogged;

	private LiveWatcher() {}

	public static void init(Path configDir) {
		cow = new CowStore(configDir.resolve("cubewheel-cow.json"));
		cow.load();
		vaults = new VaultPages(configDir.resolve("cubewheel-vaults.json"));
		vaults.load();
		// A boss outlives a client restart (2026-10-03: a restart mid-Mana Golem left the slice at "No boss").
		bossFile = configDir.resolve("cubewheel-boss.json");
		boss.load(bossFile);
		bossSkyblockFile = configDir.resolve("cubewheel-boss-skyblock.json");
		bossSkyblock.load(bossSkyblockFile);
		SliceViews.register((node, now) -> {
			Minecraft mc = Minecraft.getInstance();
			return SliceViews.fly(node, mc.player != null && mc.player.getAbilities().mayfly);
		});
		SliceViews.register((node, now) -> {
			CubeWheelConfig.DailyReward d = CubeWheelClient.config().current().dailyReward;
			if (!d.enabled) return null;
			return CowStore.slice(node, cow.status(player(), periods(d), now));
		});
		SliceViews.register((node, now) -> {
			if (!node.isSlice() || !"boss".equals(node.dynamic)) return null;
			CubeWheelConfig.Events e = CubeWheelClient.config().current().events;
			return currentBoss().view(e.bossWarps, e.bossMinutes * 60_000L, now);
		});
		SliceViews.register((node, now) -> {
			if (!node.isSlice() || !"tpa".equals(node.dynamic)) return null;
			return tpa.view(now);
		});
	}

	/** Your vault count as last read from a /pv page, else {@code fallback} (the configured vaultCount). */
	/** Used slots per vault page for you, as of each page's last open (pages never opened are absent). */
	public static java.util.Map<Integer, Integer> vaultFill() {
		return vaults == null ? java.util.Map.of() : vaults.fill(player());
	}

	public static int vaultCount(int fallback) {
		Integer n = vaults == null ? null : vaults.count(player());
		return n == null ? fallback : n;
	}

	static CowStore.Periods periods(CubeWheelConfig.DailyReward d) {
		return CowStore.Periods.of(d.dailyHours, d.weeklyDays, d.monthlyDays);
	}

	/** Your session username (ManaCube shows the Minecraft name in broadcasts); null before login. */
	private static String player() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getUser() == null ? null : mc.getUser().getName();
	}

	/** A boss spawn or defeat in {@code text}, recorded in {@code b} (saved to {@code file}); true if it was one. */
	private static boolean readBoss(BossSlice b, Path file, String text, long now) {
		if (b.onChat(text, now)) {
			b.save(file);
			return true;
		}
		Optional<BossParser.Spawn> spawn = BossParser.parse(text);
		if (spawn.isEmpty()) return false;
		b.spawned(spawn.get(), now);
		b.save(file);
		CubeWheelClient.LOG.info("[cubewheel] {}boss {} at {}", spawn.get().mini() ? "mini " : "",
				spawn.get().boss(), spawn.get().location());
		return true;
	}

	/** The boss of the gamemode we are in: Skyblock's own, else Survival's. */
	private static BossSlice currentBoss() {
		return ServerGate.gamemode(CubeWheelClient.config().current()) == Gamemode.SKYBLOCK ? bossSkyblock : boss;
	}

	/** ClientReceiveMessageEvents.ALLOW_GAME: always returns true (never hides the message). */
	public static boolean onGameMessage(Component message, boolean overlay) {
		try {
			if (overlay || message == null || cow == null) return true;
			CubeWheelConfig cfg = CubeWheelClient.config().current();
			Gamemode mode = ServerGate.gamemode(cfg);
			String text = message.getString();
			long now = System.currentTimeMillis();
			// Skyblock announces bosses exactly like Survival (capture 2026-11-06: "A BOSS SPAWNED / Boss Sahuagin /
			// Location: Boss Arena"); its own boss is kept apart. Everything else here is Survival's.
			if (mode == Gamemode.SKYBLOCK) {
				readBoss(bossSkyblock, bossSkyblockFile, text, now);
				return true;
			}
			if (mode != Gamemode.SURVIVAL) return true;
			if (tpa.onChat(text, now)) return true;
			if (readBoss(boss, bossFile, text, now)) return true;
			Optional<CowParser.Claim> claim = CowParser.claim(text);
			String me = player();
			if (claim.isPresent() && me != null && claim.get().player().equalsIgnoreCase(me)) {
				if (cow.claimed(me, claim.get(), now)) cow.save();
				CubeWheelClient.LOG.info("[cubewheel] /cow claim: {} {}",
						claim.get().tier() == null ? "their" : claim.get().tier().id(), claim.get().reward());
				return true;
			}
			// The daily claim broadcasts nothing; its private key line counts only while the /cow menu is fresh.
			Optional<CowParser.Claim> own = CowParser.received(text);
			if (own.isPresent() && me != null && now >= cowSentAt && now - cowSentAt <= COW_CLAIM_WINDOW_MS) {
				if (cow.claimed(me, own.get(), now)) cow.save();
				CubeWheelClient.LOG.info("[cubewheel] /cow claim: {} {}", own.get().tier().id(), own.get().reward());
			}
		} catch (RuntimeException e) {
			if (!chatFailureLogged) CubeWheelClient.LOG.error("[cubewheel] live slice chat parse failed", e);
			chatFailureLogged = true;
		}
		return true;
	}

	/** ClientSendMessageEvents.COMMAND: notes /cow so the menu it opens is read. */
	public static void onCommand(String command) {
		try {
			if (command == null) return;
			String c = command.trim().toLowerCase(Locale.ROOT);
			if (c.startsWith("/")) c = c.substring(1);
			if (c.equals("cow") || c.startsWith("cow ") || c.equals("cashcow")) cowSentAt = System.currentTimeMillis();
			if (c.equals("pv") || c.startsWith("pv ")) {
				pvSentAt = System.currentTimeMillis();
				String arg = c.length() > 3 ? c.substring(3).trim() : "";
				pvPage = arg.matches("\\d{1,2}") ? Integer.parseInt(arg) : 1;
			}
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] /cow note failed", e);
		}
	}

	/** Called by ContainerHook for each scan of a Survival server menu. */
	public static void onMenu(String title, List<ItemView> items, long now) {
		try {
			if (cow == null || items == null) return;
			// A /pv page: opened right after /pv, or recognised by its page-button row (its title is only a glyph). Read
			// how many pages you have and how full this one is (each scan, so the close scan has the final contents).
			OptionalInt open = VaultPages.current(items);
			boolean afterPv = now >= pvSentAt && now - pvSentAt <= COW_MENU_WINDOW_MS;
			if (vaults != null && (afterPv || open.isPresent())) {
				String me = player();
				OptionalInt pages = VaultPages.unlocked(items);
				boolean changed = false;
				if (me != null && pages.isPresent() && vaults.set(me, pages.getAsInt())) {
					changed = true;
					CubeWheelClient.LOG.info("[cubewheel] {} has {} vaults", me, pages.getAsInt());
				}
				int page = open.isPresent() ? open.getAsInt() : afterPv ? pvPage : -1;
				if (me != null && page > 0 && pages.isPresent()) changed |= vaults.fill(me, page, VaultPages.used(items));
				if (changed) vaults.save();
				return;
			}
			CubeWheelConfig cfg = CubeWheelClient.config().current();
			if (!cfg.dailyReward.enabled) return;
			boolean afterCow = now - cowSentAt <= COW_MENU_WINDOW_MS && now >= cowSentAt;
			if (!afterCow && !titleMatches(title, cfg.dailyReward.menuTitlePattern)) return;
			String me = player();
			if (me == null) return;
			boolean changed = false;
			for (ItemView item : items) {
				Optional<CowParser.MenuState> st = CowParser.menuItem(item.name(), item.lore());
				if (st.isPresent()) changed |= cow.menu(me, st.get(), now);
			}
			if (changed) cow.save();
		} catch (RuntimeException e) {
			if (!menuFailureLogged) CubeWheelClient.LOG.error("[cubewheel] /cow menu read failed", e);
			menuFailureLogged = true;
		}
	}

	private static boolean titleMatches(String title, String regex) {
		if (title == null || regex == null || regex.isEmpty()) return false;
		try {
			return Pattern.compile(regex).matcher(CowParser.strip(title)).find();
		} catch (PatternSyntaxException e) {
			return false;
		}
	}
}
