package com.mage.cubewheel.sva.mc;

import com.mage.cubewheel.CubeWheelClient;
import com.mage.cubewheel.ServerGate;
import com.mage.cubewheel.config.CubeWheelConfig;
import com.mage.cubewheel.sva.SvaCache;
import com.mage.cubewheel.sva.SvaCatalog;
import com.mage.cubewheel.sva.SvaFormat;
import com.mage.cubewheel.sva.SvaHttp;
import com.mage.cubewheel.sva.SvaService;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

	private static SvaService service;
	private static int tooltipFailures;

	private SvaClient() {}

	public static SvaService service() {
		return service;
	}

	public static void init(Path configDir) {
		String version = FabricLoader.getInstance().getModContainer(CubeWheelClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		service = new SvaService(new SvaHttp(version), new SvaCache(configDir.resolve("cubewheel-cache")),
				SvaClient::gateOpen, System::currentTimeMillis);
		// ~700 KB of JSON: parse off the client thread; the tooltip simply stays off until it is there.
		CompletableFuture.runAsync(() -> {
			try {
				service.loadCached();
			} catch (RuntimeException e) {
				CubeWheelClient.LOG.warn("[cubewheel] SVA cache load failed", e);
			}
		});
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
			service.refresh(false); // stale-only: at most the catalog every 6 h, owned every 10 min
		} catch (RuntimeException e) {
			CubeWheelClient.LOG.error("[cubewheel] SVA refresh on join failed", e);
		}
	}

	static void setSelf(Minecraft mc) {
		UUID id = mc.getUser() == null ? null : mc.getUser().getProfileId();
		if (id != null) service.setSelf(id.toString());
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
		if (tooltipFailures >= MAX_FAILURES || service == null || stack == null || stack.isEmpty()) return;
		try {
			SvaCatalog catalog = service.catalog();
			if (catalog == null) return;
			CubeWheelConfig cfg = CubeWheelClient.config().current();
			if (!cfg.svas.enabled || !cfg.svas.tooltip || !ServerGate.active(cfg)) return;
			if (Minecraft.getInstance().gui.screen() instanceof SvaCatalogScreen) return; // it has its own
			Identifier model = stack.get(DataComponents.ITEM_MODEL);
			SvaCatalog.Match match = catalog.match(stack.getHoverName().getString(),
					BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),
					model == null ? null : model.toString(),
					() -> SvaItems.plainLore(stack));
			String line = SvaFormat.tooltipLine(match, service.owned());
			if (line != null) lines.add(Component.literal(line).withStyle(ChatFormatting.LIGHT_PURPLE));
		} catch (RuntimeException e) {
			if (++tooltipFailures >= MAX_FAILURES) {
				CubeWheelClient.LOG.error("[cubewheel] SVA tooltip disabled for this session after repeated failures", e);
			}
		}
	}
}
