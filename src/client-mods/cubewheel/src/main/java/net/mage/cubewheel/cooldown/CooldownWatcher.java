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
	/**
	 * A right-click with an item that has a "When Consumed" cooldown, waiting to see if it was consumed at once
	 * (ManaCube's "Infinite Uses" potions apply on the click, with no drinking): a potion effect gained or
	 * refreshed within {@link #INSTANT_CONSUME_MS} counts as the consumption.
	 */
	private static ItemStack instantItem;
	private static long instantUntil;
	private static java.util.Map<Object, Integer> instantEffects = java.util.Map.of();
	private static final long INSTANT_CONSUME_MS = 2_500;

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
			ItemStack stack = player.getItemInHand(hand);
			trigger(stack, Action.USE, player.isShiftKeyDown());
			if (!stack.isEmpty() && ItemAbilities.parse(lore(stack)).abilities().stream().anyMatch(a -> a.action() == Action.CONSUME)) {
				instantItem = stack.copyWithCount(1);
				instantUntil = System.currentTimeMillis() + INSTANT_CONSUME_MS;
				instantEffects = effects(player);
			}
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	/**
	 * An action-bar message: a "still cooling down" notice ("PHOENIX STAFF CD: ⬛⬛ (7s)") corrects that item's
	 * countdown, or starts one if none was running.
	 */
	public static void onActionBar(String text) {
		try {
			if (!active()) return;
			List<CooldownBar.Notice> notices = CooldownBar.parse(text);
			if (notices.isEmpty()) return;
			LocalPlayer p = Minecraft.getInstance().player;
			long now = System.currentTimeMillis();
			for (CooldownBar.Notice n : notices) {
				// "(7s)" counts down whole seconds: take the middle of that second.
				ItemStack held = p == null ? ItemStack.EMPTY : heldNamed(p, n.item());
				ItemAbilities.Ability guess = null;
				if (!held.isEmpty()) {
					List<ItemAbilities.Ability> abilities = ItemAbilities.parse(lore(held)).abilities();
					if (abilities.size() == 1) guess = abilities.get(0);
				}
				tracker.sync(n.item(), n.seconds() * 1000 + 500, now, guess, held.isEmpty() ? null : held.copyWithCount(1));
			}
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	/** The main- or off-hand item called {@code name} (any case), else empty. */
	private static ItemStack heldNamed(LocalPlayer p, String name) {
		for (ItemStack s : List.of(p.getMainHandItem(), p.getOffhandItem())) {
			if (!s.isEmpty() && s.getHoverName().getString().replaceAll("§.", "").trim().equalsIgnoreCase(name)) return s;
		}
		return ItemStack.EMPTY;
	}

	/** The player's effects and their remaining ticks, keyed by effect. */
	private static java.util.Map<Object, Integer> effects(Player player) {
		java.util.Map<Object, Integer> out = new java.util.HashMap<>();
		for (net.minecraft.world.effect.MobEffectInstance e : player.getActiveEffects()) out.put(e.getEffect(), e.getDuration());
		return out;
	}

	/** Did the player gain an effect, or one get longer (refreshed), since {@code before}? */
	private static boolean gainedEffect(Player player, java.util.Map<Object, Integer> before) {
		for (net.minecraft.world.effect.MobEffectInstance e : player.getActiveEffects()) {
			Integer old = before.get(e.getEffect());
			if (old == null || e.getDuration() > old + 5) return true;
		}
		return false;
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
			if (instantItem != null) {
				if (System.currentTimeMillis() > instantUntil) instantItem = null;
				else if (gainedEffect(p, instantEffects)) {
					trigger(instantItem, Action.CONSUME, false); // a running countdown is never restarted
					instantItem = null;
				}
			}
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
		if (tracker.trigger(name, a.abilities(), action, sneaking, System.currentTimeMillis(), stack.copyWithCount(1))) {
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
	 * The "Cooldowns" panel: "R Samurai Katana 4.5s", soonest first, then command cooldowns ("/ Heal 4:30",
	 * {@link CommandWatcher#lines}), then mcMMO abilities ({@link McmmoWatcher#lines}), then the held item's "Uses: N".
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
				String itemId = e.icon() instanceof ItemStack s && !s.isEmpty() ? s.typeHolder().getRegisteredName() : null;
				lines.add(new Panel.Line(CooldownTracker.shownTrigger(e.trigger(), itemId), Panel.GRAY, e.label(), color,
						Durations.shortCountdown(left))
						.withIcon(e.icon()));
			}
		}
		lines.addAll(CommandWatcher.lines(now));
		lines.addAll(McmmoWatcher.lines(now));
		String uses = heldUses;
		if (items && cfg.cooldowns.showUses && uses != null) lines.add(new Panel.Line(uses, Panel.GRAY));
		if (lines.isEmpty()) return Optional.empty();
		CubeWheelConfig.Position p = cfg.cooldowns.position;
		return Optional.of(new Panel("Cooldowns", lines, HudLayout.Corner.parse(p.corner), p.x, p.y));
	}
}
