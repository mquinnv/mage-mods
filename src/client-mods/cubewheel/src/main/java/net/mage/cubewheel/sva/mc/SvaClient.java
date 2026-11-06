package net.mage.cubewheel.sva.mc;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.sidebar.Gamemode;
import net.mage.cubewheel.sva.SvaCache;
import net.mage.cubewheel.sva.SvaCatalog;
import net.mage.cubewheel.sva.SvaFormat;
import net.mage.cubewheel.sva.SvaHttp;
import net.mage.cubewheel.sva.SvaService;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft side of the SVA feature: owns the {@link SvaService}, fetches on joining ManaCube, opens the
 * catalog screen and appends the tooltip line. Only ManaCube's and Mojang's public web APIs are contacted —
 * never the game server (the catalog's "/ah search" click goes through CommandSender).
 */
public final class SvaClient {
	private static final int MAX_FAILURES = 10;

	/** One service per tracked gamemode; filled in init, read-only afterwards. */
	private static final Map<Gamemode, SvaService> services = new EnumMap<>(Gamemode.class);
	private static final AtomicInteger tooltipFailures = new AtomicInteger();

	private SvaClient() {}

	/** The current gamemode's service; Survival's when the mode is neither tracked one (or not detected yet). */
	public static SvaService service() {
		SvaService s = services.get(ServerGate.gamemode(CubeWheelClient.config().current()));
		return s != null ? s : services.get(Gamemode.SURVIVAL);
	}

	public static void init(Path configDir) {
		String version = FabricLoader.getInstance().getModContainer(CubeWheelClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		SvaHttp http = new SvaHttp(version);
		for (Gamemode mode : new Gamemode[] {Gamemode.SURVIVAL, Gamemode.SKYBLOCK}) {
			SvaService svc = new SvaService(mode, http, new SvaCache(configDir.resolve("cubewheel-cache"), mode),
					SvaClient::gateOpen, System::currentTimeMillis);
			services.put(mode, svc);
			// ~700 KB of JSON each: parse off the client thread; the tooltip stays off until it is there.
			CompletableFuture.runAsync(() -> {
				try {
					svc.loadCached();
				} catch (RuntimeException e) {
					CubeWheelClient.LOG.warn("[cubewheel] SVA cache load failed ({})", mode, e);
				}
			});
		}
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> appendTooltip(stack, lines));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(() -> onJoin(mc)));
	}

	/** Network is allowed only with svas.enabled while connected to a gated (ManaCube) server. */
	private static boolean gateOpen() {
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		return cfg.svas.enabled && ServerGate.active(cfg);
	}

	private static void onJoin(Minecraft mc) {
		try {
			if (!gateOpen()) return;
			setSelf(mc);
			// stale-only: at most the catalog every 6 h, owned every 10 min. The sidebar may still be empty here,
			// making this Survival's service on Skyblock; the tooltip and the screen refresh the right one.
			service().refresh(false);
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] SVA refresh on join failed", e);
		}
	}

	static void setSelf(Minecraft mc) {
		UUID id = mc.getUser() == null ? null : mc.getUser().getProfileId();
		if (id != null) services.values().forEach(s -> s.setSelf(id.toString()));
	}

	/** Key handler: opens the catalog screen (works offline with the cached catalog). */
	public static void openCatalog(Minecraft mc) {
		if (mc.gui.screen() != null) return;
		if (!CubeWheelClient.config().current().svas.enabled) {
			if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("CubeWheel: SVAs are switched off (svas.enabled)"));
			return;
		}
		setSelf(mc);
		mc.gui.setScreen(new SvaCatalogScreen());
	}

	private static void appendTooltip(ItemStack stack, List<Component> lines) {
		if (tooltipFailures.get() >= MAX_FAILURES || services.isEmpty() || stack == null || stack.isEmpty()) return;
		try {
			CubeWheelConfig cfg = CubeWheelClient.config().current();
			if (!cfg.svas.enabled || !cfg.svas.tooltip || !ServerGate.tracks(cfg)) return;
			SvaService service = service();
			SvaCatalog catalog = service.catalog();
			if (catalog == null) {
				// e.g. Skyblock's list when the sidebar was empty at JOIN: fetch it now (backoff and in-flight guarded)
				service.refresh(false);
				return;
			}
			if (Minecraft.getInstance().gui.screen() instanceof SvaCatalogScreen) return; // it has its own
			// the registry key and item model are only looked up once the name is an SVA name
			SvaCatalog.Match match = catalog.match(stack.getHoverName().getString(),
					() -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),
					() -> {
						Identifier model = stack.get(DataComponents.ITEM_MODEL);
						return model == null ? null : model.toString();
					},
					() -> SvaItems.plainLore(stack));
			String line = SvaFormat.tooltipLine(match, service.owned());
			if (line == null) return;
			lines.add(Component.literal(line).withStyle(match.nameOnly() ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
		} catch (RuntimeException e) {
			if (tooltipFailures.incrementAndGet() == MAX_FAILURES) {
				CubeWheelClient.LOG.error("[cubewheel] SVA tooltip disabled for this session after repeated failures", e);
			}
		}
	}
}
