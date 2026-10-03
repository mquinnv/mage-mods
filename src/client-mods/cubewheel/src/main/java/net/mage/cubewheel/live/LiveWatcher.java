package net.mage.cubewheel.live;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
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
 * passive: never hides a message, never sends anything. Chat and menus are read only in ManaCube Survival.
 */
public final class LiveWatcher {
	/** A menu opened this soon after you sent /cow is read as the /cow menu, whatever its title. */
	private static final long COW_MENU_WINDOW_MS = 10_000;
	/** A "You received N Daily Crate Key(s)" line this soon after /cow is read as your claim from its menu. */
	private static final long COW_CLAIM_WINDOW_MS = 300_000;

	private static CowStore cow;
	private static final BossSlice boss = new BossSlice();
	private static Path bossFile;
	private static final TpaSlice tpa = new TpaSlice();
	private static long cowSentAt = Long.MIN_VALUE;
	private static VaultPages vaults;
	private static long pvSentAt = Long.MIN_VALUE;
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
			return boss.view(e.bossWarps, e.bossMinutes * 60_000L, now);
		});
		SliceViews.register((node, now) -> {
			if (!node.isSlice() || !"tpa".equals(node.dynamic)) return null;
			return tpa.view(now);
		});
	}

	/** Your vault count as last read from a /pv page, else {@code fallback} (the configured vaultCount). */
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

	/** ClientReceiveMessageEvents.ALLOW_GAME: always returns true (never hides the message). */
	public static boolean onGameMessage(Component message, boolean overlay) {
		try {
			if (overlay || message == null || cow == null) return true;
			CubeWheelConfig cfg = CubeWheelClient.config().current();
			if (!ServerGate.survival(cfg)) return true;
			String text = message.getString();
			long now = System.currentTimeMillis();
			if (tpa.onChat(text, now)) return true;
			if (boss.onChat(text, now)) {
				boss.save(bossFile);
				return true;
			}
			Optional<BossParser.Spawn> spawn = BossParser.parse(text);
			if (spawn.isPresent()) {
				boss.spawned(spawn.get(), now);
				boss.save(bossFile);
				CubeWheelClient.LOG.info("[cubewheel] {}boss {} at {}", spawn.get().mini() ? "mini " : "",
						spawn.get().boss(), spawn.get().location());
				return true;
			}
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
			if (c.equals("pv") || c.startsWith("pv ")) pvSentAt = System.currentTimeMillis();
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] /cow note failed", e);
		}
	}

	/** Called by ContainerHook for each scan of a Survival server menu. */
	public static void onMenu(String title, List<ItemView> items, long now) {
		try {
			if (cow == null || items == null) return;
			// A /pv page (its title is a glyph, so only "opened right after /pv" identifies it): read the page row.
			if (vaults != null && now >= pvSentAt && now - pvSentAt <= COW_MENU_WINDOW_MS) {
				String me = player();
				OptionalInt pages = VaultPages.unlocked(items);
				if (me != null && pages.isPresent() && vaults.set(me, pages.getAsInt())) {
					vaults.save();
					CubeWheelClient.LOG.info("[cubewheel] {} has {} vaults", me, pages.getAsInt());
				}
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
