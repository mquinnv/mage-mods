package net.mage.cubewheel.cooldown;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.cooldown.ItemAbilities.Action;
import net.mage.cubewheel.hud.Durations;
import net.mage.cubewheel.hud.HudLayout;
import net.mage.cubewheel.hud.Panel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

/**
 * Minecraft adapter for item cooldowns. Purely passive: it watches the local player's own right-clicks (item,
 * block and entity use callbacks), attack clicks (ClientPreAttackCallback, never cancelled), finished
 * eating/drinking and starting to sneak, reads the used item's lore and starts a countdown; every callback
 * returns PASS/false so the game handles the click as usual. Active only in ManaCube Survival with
 * {@code cooldowns.enabled}. Each hook is guarded; after {@link #MAX_FAILURES} failures the feature switches
 * itself off for the session.
 */
public final class CooldownWatcher {
	private static final int MAX_FAILURES = 10;
	private static final int USES_INTERVAL_TICKS = 10;
	/** Under this many ms left a countdown is shown yellow. */
	private static final long ENDING_SOON_MS = 3_000;

	private static final CooldownTracker tracker = new CooldownTracker();
	private static final CooldownTracker.ConsumeDetector<ItemStack> eating = new CooldownTracker.ConsumeDetector<>();
	private static final CooldownTracker.Edge sneakEdge = new CooldownTracker.Edge();
	private static int failures;
	private static int ticks;
	/** "Uses: 123" of the main-hand item, refreshed every {@link #USES_INTERVAL_TICKS} ticks; null = none. */
	private static String heldUses;
	private static ItemStack usingRef;
	private static ItemStack usingCopy;

	private CooldownWatcher() {}

	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			onUse(player, level, hand);
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			onUse(player, level, hand);
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			onUse(player, level, hand);
			return InteractionResult.PASS;
		});
		ClientPreAttackCallback.EVENT.register((mc, player, clickCount) -> {
			if (clickCount > 0) onAttack(player);
			return false; // never cancel the attack
		});
		ClientTickEvents.END_CLIENT_TICK.register(CooldownWatcher::onEndTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			tracker.clear();
			heldUses = null;
		});
	}

	private static boolean active() {
		if (failures >= MAX_FAILURES) return false;
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		return cfg.cooldowns.enabled && ServerGate.survival(cfg);
	}

	private static void onUse(Player player, Level level, InteractionHand hand) {
		try {
			if (!level.isClientSide() || player != Minecraft.getInstance().player || !active() || player.isSpectator()) return;
			trigger(player.getItemInHand(hand), Action.USE, player.isShiftKeyDown());
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	private static void onAttack(LocalPlayer player) {
		try {
			if (player == null || !active() || player.isSpectator()) return;
			trigger(player.getMainHandItem(), Action.ATTACK, player.isShiftKeyDown());
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	private static void onEndTick(Minecraft mc) {
		try {
			LocalPlayer p = mc.player;
			if (p == null || !active()) {
				heldUses = null;
				return;
			}
			boolean consuming = false;
			ItemStack using = null;
			if (p.isUsingItem()) {
				using = p.getUseItem();
				ItemUseAnimation anim = using.getUseAnimation();
				consuming = anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK;
			}
			if (consuming && using != usingRef) { // copy once per use: the stack shrinks (or empties) when eaten
				usingRef = using;
				usingCopy = using.copy();
			}
			if (!consuming) usingRef = null;
			ItemStack eaten = eating.tick(consuming, consuming ? p.getUseItemRemainingTicks() : 0, consuming ? usingCopy : null);
			if (eaten != null) trigger(eaten, Action.CONSUME, false);
			if (sneakEdge.rose(p.isShiftKeyDown()) && mc.gui.screen() == null) trigger(p.getMainHandItem(), Action.SNEAK, true);
			if (++ticks >= USES_INTERVAL_TICKS) {
				ticks = 0;
				heldUses = usesOf(p.getMainHandItem());
			}
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	private static void trigger(ItemStack stack, Action action, boolean sneaking) {
		if (stack == null || stack.isEmpty()) return;
		ItemAbilities a = ItemAbilities.parse(lore(stack));
		if (a.abilities().isEmpty()) return;
		String name = stack.getHoverName().getString().replaceAll("§.", "").trim();
		if (tracker.trigger(name, a.abilities(), action, sneaking, System.currentTimeMillis())) {
			CubeWheelClient.LOG.debug("[cubewheel] cooldown started: {} ({})", name, action);
		}
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static String usesOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		OptionalLong u = ItemAbilities.parse(lore(stack)).uses();
		return u.isPresent() ? String.format(java.util.Locale.ROOT, "Uses: %,d", u.getAsLong()) : null;
	}

	private static void fail(RuntimeException e) {
		if (failures == 0) CubeWheelClient.LOG.error("[cubewheel] item cooldown hook failed", e);
		if (++failures == MAX_FAILURES) CubeWheelClient.LOG.warn("[cubewheel] item cooldowns switched off for this session after repeated failures");
	}

	/**
	 * The "Cooldowns" panel: "R Samurai Katana 4.5s", soonest first, then mcMMO abilities
	 * ({@link McmmoWatcher#lines}), then the held item's "Uses: N".
	 */
	public static Optional<Panel> panel(long now) {
		CubeWheelConfig cfg = CubeWheelClient.config().current();
		List<Panel.Line> lines = new ArrayList<>();
		boolean items = active();
		if (items) {
			for (CooldownTracker.Entry e : tracker.active(now)) {
				long left = e.endsAt() - now;
				int color = left < ENDING_SOON_MS ? Panel.YELLOW : Panel.WHITE;
				// "R  Phoenix Staff   12s": the trigger in the tag column, a short name, the time on the right.
				lines.add(new Panel.Line(e.trigger(), Panel.GRAY, e.label(), color, Durations.shortCountdown(left)));
			}
		}
		lines.addAll(McmmoWatcher.lines(now));
		String uses = heldUses;
		if (items && cfg.cooldowns.showUses && uses != null) lines.add(new Panel.Line(uses, Panel.GRAY));
		if (lines.isEmpty()) return Optional.empty();
		CubeWheelConfig.Position p = cfg.cooldowns.position;
		return Optional.of(new Panel("Cooldowns", lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
	}
}
