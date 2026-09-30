package com.mage.cubewheel.tracker.local.mc;

import com.mage.cubewheel.CubeWheelClient;
import com.mage.cubewheel.ServerGate;
import com.mage.cubewheel.capture.CaptureLog;
import com.mage.cubewheel.config.CubeWheelConfig;
import com.mage.cubewheel.mixin.FishingHookAccessor;
import com.mage.cubewheel.sidebar.SidebarLinker;
import com.mage.cubewheel.tracker.TrackerStore;
import com.mage.cubewheel.tracker.local.FishDetector;
import com.mage.cubewheel.tracker.local.KillAttribution;
import com.mage.cubewheel.tracker.local.LocalCounter;
import com.mage.cubewheel.tracker.local.PendingBreaks;
import com.mage.cubewheel.tracker.local.PlacedBlocks;
import com.mage.cubewheel.tracker.local.Pos;
import com.mage.cubewheel.tracker.local.Signal;
import com.mage.cubewheel.tracker.local.WorldInfo;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minecraft adapter for local counting: the static facade that Fabric callbacks and the optional mixins
 * call. Purely passive: it only observes (own block breaks, damage/death packets, reeling in a biting
 * bobber) and never sends, opens or clicks anything; its only write is the local tracker file. Active
 * only in ManaCube Survival (host gate plus sidebar title, see {@link ServerGate#survival}), in
 * survival/adventure game mode, with {@code tracker.local.enabled}. Every entry point is guarded: a failing hook is
 * logged once and switched off for the session after {@link #MAX_FAILURES} failures.
 */
public final class LocalSignals {
	/** Hook kinds that can fail independently. */
	public enum Hook { BREAK, SYNC, PLACE, ATTACK, DAMAGE, DEATH, FISH, TICK, LEVEL }

	private static final int MAX_FAILURES = 10;
	private static final long SAVE_INTERVAL_MS = 30_000;
	private static final int DEATH_EVENT = 3; // EntityEvent.DEATH

	private static final Map<Hook, Integer> failures = new EnumMap<>(Hook.class);
	private static final SidebarLinker.SaveThrottle saveThrottle = new SidebarLinker.SaveThrottle(SAVE_INTERVAL_MS);
	private static final PendingBreaks pending = new PendingBreaks();
	private static final PlacedBlocks placed = new PlacedBlocks(PlacedBlocks.DEFAULT_CAPACITY);
	private static final KillAttribution kills = new KillAttribution();
	private static final WorldProbe world = new WorldProbe();
	private static long tick;
	/** Recomputed every client tick: in ManaCube Survival, counting enabled, a store and a player exist. */
	private static boolean active;

	private LocalSignals() {}

	public static void register() {
		ClientPlayerBlockBreakEvents.AFTER.register(LocalSignals::afterBlockBreak);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			try {
				onAttack(player, level.isClientSide(), entity);
			} catch (Throwable t) {
				fail(Hook.ATTACK, t);
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			try {
				onUseItem(player, level.isClientSide(), player.getItemInHand(hand).getItem() instanceof FishingRodItem);
			} catch (Throwable t) {
				fail(Hook.FISH, t);
			}
			return InteractionResult.PASS;
		});
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((mc, level) -> onLevelChange());
		ClientTickEvents.END_CLIENT_TICK.register(LocalSignals::onEndTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> flush());
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> flush());
		TrackerStore store = CubeWheelClient.tracker();
		if (store != null) store.setSnapBackListener(LocalSignals::onSnapBack);
	}

	// ---- entry points (Fabric callbacks and mixins) ----

	/** ClientPlayerBlockBreakEvents.AFTER: {@code state} is the state before the break. */
	private static void afterBlockBreak(ClientLevel level, Player player, BlockPos pos, BlockState state) {
		try {
			if (!enabled(Hook.BREAK) || !local().blocks) return;
			if (!survivalMode(player)) return;
			Pos p = new Pos(pos.getX(), pos.getY(), pos.getZ());
			int stateId = Block.getId(state);
			if (placed.consumeIfPlaced(p, stateId)) return; // plugins ignore blocks you placed
			Signal.BlockBroken signal = BlockFacts.of(state, level, pos, world());
			List<LocalCounter.Contribution> added = count(signal);
			if (added.isEmpty()) return;
			pending.record(p, stateId, added, tick);
			capture("break", signal.id(), signal.name(), signal.world(), added);
		} catch (Throwable t) {
			fail(Hook.BREAK, t);
		}
	}

	/** Mixin, ClientLevel.syncBlockState HEAD: the server's verdict on a predicted block change. */
	public static void onSyncBlockState(BlockPos pos, BlockState serverState) {
		try {
			if (pending.isEmpty() || !enabled(Hook.SYNC)) return;
			Optional<List<LocalCounter.Contribution>> rejected =
					pending.onSync(new Pos(pos.getX(), pos.getY(), pos.getZ()), Block.getId(serverState));
			if (rejected.isEmpty()) return;
			TrackerStore store = CubeWheelClient.tracker();
			LocalCounter.reverse(rejected.get(), store);
			saveThrottle.markDirty();
			capture("reject", serverState.typeHolder().getRegisteredName(), null, world(), rejected.get());
		} catch (Throwable t) {
			fail(Hook.SYNC, t);
		}
	}

	/** Mixin, BlockItem.place RETURN (client side, successful): remember the placed block. */
	public static void onPlaced(ClientLevel level, BlockPos pos) {
		try {
			if (!enabled(Hook.PLACE) || !local().blocks) return;
			placed.placed(new Pos(pos.getX(), pos.getY(), pos.getZ()), Block.getId(level.getBlockState(pos)));
		} catch (Throwable t) {
			fail(Hook.PLACE, t);
		}
	}

	/** AttackEntityCallback (client side): an own melee hit counts as damage by the local player. */
	private static void onAttack(Player player, boolean clientSide, Entity target) {
		try {
			if (!clientSide || !enabled(Hook.ATTACK) || !local().kills) return;
			Minecraft mc = Minecraft.getInstance();
			if (player != mc.player || !survivalMode(player) || target instanceof Player) return;
			kills.onDamage(target.getId(), player.getId(), tick);
		} catch (Throwable t) {
			fail(Hook.ATTACK, t);
		}
	}

	/** Mixin, ClientPacketListener.handleDamageEvent (main thread): remember the last player to hurt it. */
	public static void onDamageEvent(int entityId, int causeId) {
		try {
			if (!enabled(Hook.DAMAGE) || !local().kills) return;
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.player == null || causeId < 0) return;
			boolean playerCause = causeId == mc.player.getId() || mc.level.getEntity(causeId) instanceof Player;
			if (playerCause) kills.onDamage(entityId, causeId, tick);
		} catch (Throwable t) {
			fail(Hook.DAMAGE, t);
		}
	}

	/** Mixin, ClientPacketListener.handleEntityEvent (main thread), before the client applies the event. */
	public static void onEntityEvent(Entity entity, byte eventId) {
		try {
			if (eventId != DEATH_EVENT || entity == null || !enabled(Hook.DEATH) || !local().kills) return;
			Minecraft mc = Minecraft.getInstance();
			if (!survivalMode(mc.player) || !(entity instanceof LivingEntity) || entity instanceof Player) return;
			if (!kills.onDeath(entity.getId(), mc.player.getId(), tick)) return;
			Signal.MobKilled signal = EntityFacts.of(entity, world());
			List<LocalCounter.Contribution> added = count(signal);
			capture("kill", signal.typeId(), signal.name(), signal.world(), added);
		} catch (Throwable t) {
			fail(Hook.DEATH, t);
		}
	}

	/** UseItemCallback (client side): reeling in while the bobber is biting is a catch. */
	private static void onUseItem(Player player, boolean clientSide, boolean holdingRod) {
		try {
			if (!clientSide || !holdingRod || !enabled(Hook.FISH) || !local().fish) return;
			if (player != Minecraft.getInstance().player || !survivalMode(player)) return;
			boolean hasHook = player.fishing != null;
			boolean biting = hasHook && ((FishingHookAccessor) player.fishing).cubewheel$isBiting();
			if (!FishDetector.onRodUse(hasHook, biting)) return;
			Signal.FishCaught signal = new Signal.FishCaught(world());
			capture("fish", null, null, signal.world(), count(signal));
		} catch (Throwable t) {
			fail(Hook.FISH, t);
		}
	}

	private static void onLevelChange() {
		try {
			pending.clear();
			placed.clear();
			kills.clear();
			world.invalidate();
		} catch (Throwable t) {
			fail(Hook.LEVEL, t);
		}
	}

	private static void onEndTick(Minecraft mc) {
		try {
			tick++;
			CubeWheelConfig cfg = CubeWheelClient.config().current();
			TrackerStore store = CubeWheelClient.tracker();
			active = cfg.tracker.local.enabled && store != null && mc.player != null && mc.level != null && ServerGate.survival(cfg);
			pending.expire(tick);
			kills.expire(tick);
			if (store != null && saveThrottle.shouldSave(System.currentTimeMillis())) store.save();
		} catch (Throwable t) {
			fail(Hook.TICK, t);
		}
	}

	private static void flush() {
		try {
			TrackerStore store = CubeWheelClient.tracker();
			if (store != null && saveThrottle.consumeDirty()) store.save();
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable e) {
			CubeWheelClient.LOG.warn("[cubewheel] saving local estimates failed: {}", e.toString());
		}
	}

	private static void onSnapBack(String id, com.mage.cubewheel.tracker.local.Accuracy acc) {
		try {
			CubeWheelClient.LOG.info("[cubewheel] estimate for {}: counted {}, actual {}", id, acc.counted(), acc.actual());
			CaptureLog capture = CubeWheelClient.capture();
			if (capture != null && capture.enabled()) capture.estimate(id, acc.counted(), acc.actual(), System.currentTimeMillis());
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			CubeWheelClient.LOG.warn("[cubewheel] logging an estimate snap-back failed: {}", t.toString());
		}
	}

	// ---- helpers ----

	private static List<LocalCounter.Contribution> count(Signal signal) {
		TrackerStore store = CubeWheelClient.tracker();
		List<LocalCounter.Contribution> added = LocalCounter.onSignal(signal, store, local().worlds, System.currentTimeMillis());
		if (!added.isEmpty()) saveThrottle.markDirty();
		return added;
	}

	private static WorldInfo world() {
		return world.current(Minecraft.getInstance(), local().specialWorlds, tick);
	}

	/** Creative and spectator actions never advance ManaCube objectives. */
	private static boolean survivalMode(Player player) {
		return player != null && !player.isCreative() && !player.isSpectator();
	}

	private static CubeWheelConfig.Local local() {
		return CubeWheelClient.config().current().tracker.local;
	}

	private static boolean enabled(Hook hook) {
		return active && failures.getOrDefault(hook, 0) < MAX_FAILURES;
	}

	/** Called by the mixins when their own glue fails (e.g. an accessor that did not apply). */
	public static void fail(Hook hook, Throwable t) {
		if (t instanceof VirtualMachineError e) throw e;
		int n = failures.merge(hook, 1, Integer::sum);
		if (n == 1) CubeWheelClient.LOG.error("[cubewheel] local counting hook {} failed", hook, t);
		if (n == MAX_FAILURES) CubeWheelClient.LOG.warn("[cubewheel] local counting hook {} disabled for this session", hook);
	}

	private static void capture(String kind, String id, String name, WorldInfo at, List<LocalCounter.Contribution> added) {
		CaptureLog capture = CubeWheelClient.capture();
		if (capture == null || !capture.enabled()) return;
		List<String> matched = new ArrayList<>();
		long units = 0;
		for (LocalCounter.Contribution c : added) {
			matched.add(c.id());
			units = c.units();
		}
		capture.local(kind, id, name, new ArrayList<>(at.tokens()), matched, units, System.currentTimeMillis());
	}
}
