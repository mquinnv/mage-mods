package net.mage.cubewheel.tracker.local.mc;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.capture.CaptureLog;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.mixin.FishingHookAccessor;
import net.mage.cubewheel.sidebar.SidebarLinker;
import net.mage.cubewheel.tracker.TrackerStore;
import net.mage.cubewheel.tracker.local.FishDetector;
import net.mage.cubewheel.tracker.local.KillAttribution;
import net.mage.cubewheel.tracker.local.LocalCounter;
import net.mage.cubewheel.tracker.local.LootMatch;
import net.mage.cubewheel.tracker.local.PendingBreaks;
import net.mage.cubewheel.tracker.local.PlacedBlocks;
import net.mage.cubewheel.tracker.local.Pos;
import net.mage.cubewheel.tracker.local.RemovalKills;
import net.mage.cubewheel.tracker.local.Signal;
import net.mage.cubewheel.tracker.local.StackWatch;
import net.mage.cubewheel.tracker.local.WorldInfo;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minecraft adapter for local counting: the static facade that Fabric callbacks and the optional mixins
 * call. Purely passive: it only observes (own block breaks, damage/death/removal packets, action-bar
 * loot lines, reeling in a biting bobber) and never sends, opens or clicks anything; its only write is the local tracker file. Active
 * only in ManaCube Survival (host gate plus sidebar title, see {@link ServerGate#survival}), in
 * survival/adventure game mode, with {@code tracker.local.enabled}. Every entry point is guarded: a failing hook is
 * logged once and switched off for the session after {@link #MAX_FAILURES} failures.
 */
public final class LocalSignals {
	/** Hook kinds that can fail independently. */
	public enum Hook { BREAK, SYNC, PLACE, ATTACK, DAMAGE, DEATH, STACK, REMOVE, LOOT, FISH, TICK, LEVEL }

	private static final int MAX_FAILURES = 10;
	private static final long SAVE_INTERVAL_MS = 30_000;
	private static final int DEATH_EVENT = 3; // EntityEvent.DEATH

	private static final Map<Hook, Integer> failures = new EnumMap<>(Hook.class);
	private static final SidebarLinker.SaveThrottle saveThrottle = new SidebarLinker.SaveThrottle(SAVE_INTERVAL_MS);
	private static final PendingBreaks pending = new PendingBreaks();
	private static final PlacedBlocks placed = new PlacedBlocks(PlacedBlocks.DEFAULT_CAPACITY);
	private static final KillAttribution kills = new KillAttribution();
	private static final StackWatch stacks = new StackWatch();
	private static final RemovalKills removals = new RemovalKills();
	/** A hit mob removed farther away than this left our view rather than died. */
	private static final double REMOVAL_KILL_RANGE = 16;
	/** Deaths nobody near us hit are written to capture only within this many blocks. */
	private static final double CAPTURE_DEATH_RANGE = 32;
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
			watchStack(target);
			captureEntity("attack", target, describe(target));
			probe(target);
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
			if (!playerCause) return; // a cause-less event keeps the last hit (e.g. our own melee attack)
			kills.onDamage(entityId, causeId, tick);
			if (causeId == mc.player.getId()) {
				Entity target = mc.level.getEntity(entityId);
				if (target != null && !(target instanceof Player)) {
					watchStack(target);
					probe(target); // area hits (a katana's sweep) have no attack callback
				}
			}
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
			stacks.forget(entity.getId());
			removals.settle(entity.getId()); // its later removal is the corpse, never a second kill
			KillAttribution.Verdict verdict = kills.onDeathVerdict(entity.getId(), mc.player.getId(), tick);
			if (verdict != KillAttribution.Verdict.LOCAL) {
				// Not provably ours: still explain it in capture if anyone hit it or it died near us.
				if (verdict != KillAttribution.Verdict.NO_HIT || entity.distanceTo(mc.player) <= CAPTURE_DEATH_RANGE) {
					captureEntity("death", entity, verdict.name().toLowerCase(java.util.Locale.ROOT));
				}
				return;
			}
			Signal.MobKilled signal = EntityFacts.of(entity, world());
			List<LocalCounter.Contribution> added = count(signal);
			capture("kill", signal.typeId(), rawName(entity), "death, local hit", signal.world(), added);
		} catch (Throwable t) {
			fail(Hook.DEATH, t);
		}
	}

	/**
	 * Mixin, ClientPacketListener.handleSetEntityData RETURN (main thread, data applied): a watched entity's
	 * name changed. A stacked mob the local player hit going "5x Tiger" -> "4x Tiger" lost one mob to us.
	 */
	public static void onEntityData(int entityId) {
		try {
			if (!stacks.watching(entityId) || !enabled(Hook.STACK) || !local().kills) return;
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.player == null) return;
			Entity entity = mc.level.getEntity(entityId);
			if (entity == null) return;
			Optional<StackWatch.Change> change = stacks.onName(entityId, rawName(entity));
			if (change.isEmpty()) return;
			StackWatch.Change c = change.get();
			Entity root = c.rootId() == entityId ? entity : mc.level.getEntity(c.rootId());
			boolean ours = kills.hitBy(c.rootId(), mc.player.getId(), tick);
			String detail = c.oldName() + " -> " + c.newName() + " (killed " + c.killed()
					+ (ours ? ", local hit" : ", no recent local hit")
					+ (root == entity ? "" : ", passenger of " + (root == null ? "#" + c.rootId() : root.typeHolder().getRegisteredName()))
					+ ")";
			if (c.killed() > 0) removals.settle(c.rootId()); // a stack drop and a removal never both count
			if (c.killed() <= 0 || !ours || !survivalMode(mc.player)) {
				captureEntity("stack", entity, detail);
				return;
			}
			Signal.MobKilled signal = EntityFacts.of(root == null ? entity : root, world(), c.newName(), c.killed());
			List<LocalCounter.Contribution> added = count(signal);
			capture("kill", signal.typeId(), c.newName(), "stack: " + detail, signal.world(), added);
		} catch (Throwable t) {
			fail(Hook.STACK, t);
		}
	}

	/**
	 * Mixin, ClientPacketListener.handleRemoveEntities (main thread, before removal). The server removing
	 * a mob the local player hit within {@link RemovalKills#WINDOW_TICKS} ticks, while it is within
	 * {@link #REMOVAL_KILL_RANGE} blocks, is a kill (custom-model mobs die without a death event); counted
	 * once per entity and never when a death or stack drop already settled it. Chunk unloads and level
	 * changes never come through this packet (a level change clears all state). Other removals of hit
	 * mobs go to capture only.
	 */
	public static void onRemoveEntities(IntList ids) {
		try {
			if (!enabled(Hook.REMOVE) || !local().kills) return;
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.player == null) return;
			for (int i = 0; i < ids.size(); i++) {
				int id = ids.getInt(i);
				if (!kills.tracks(id) && !stacks.watching(id)) continue;
				Entity entity = mc.level.getEntity(id);
				if (entity != null && !(entity instanceof Player)) {
					boolean recent = kills.hitByWithin(id, mc.player.getId(), tick, RemovalKills.WINDOW_TICKS);
					boolean near = entity.distanceTo(mc.player) <= REMOVAL_KILL_RANGE;
					String why = !recent ? (kills.hitBy(id, mc.player.getId(), tick) ? "local hit, too long ago" : "no recent local hit")
							: !near ? "local hit, too far to be a kill"
							: removals.settled(id) ? "local hit, already counted or refused"
							: !survivalMode(mc.player) ? "local hit, not in survival mode" : null;
					if (why == null && removals.claim(id, true, true)) {
						onRemovalKill(entity);
					} else {
						captureEntity("removed", entity, why == null ? "local hit" : why);
					}
				}
				stacks.forget(id);
			}
		} catch (Throwable t) {
			fail(Hook.REMOVE, t);
		}
	}

	/** A removal counted as a kill: named from the hint found at hit time, else from the next loot line. */
	private static void onRemovalKill(Entity entity) {
		WorldInfo at = world();
		String typeId = entity.typeHolder().getRegisteredName();
		Optional<RemovalKills.Hint> hint = removals.hint(entity.getId());
		if (hint.isEmpty() && !removals.seen(entity.getId())) {
			hint = Optional.ofNullable(NearbyProbe.of(entity, false).hint());
		}
		if (hint.isPresent()) {
			RemovalKills.Hint h = hint.get();
			Signal.MobKilled facts = EntityFacts.of(entity, at, h.name(), 1);
			// A name from another entity, or an invisible hitbox: the hitbox's type ("slime") is not the mob.
			boolean hitbox = h.method() != RemovalKills.Method.OWN || entity.isInvisible();
			Signal.MobKilled signal = hitbox
					? new Signal.MobKilled("", facts.name(), Set.of("mob", "monster"), 1, at)
					: facts;
			List<LocalCounter.Contribution> added = count(signal);
			capture("kill", typeId, h.name(), "removal, method " + h.method().code + " (" + h.source() + "), local hit", at, added);
			return;
		}
		RemovalKills.Removed r = new RemovalKills.Removed(entity.getId(), typeId, rawName(entity), at);
		removals.awaitLoot(r, System.currentTimeMillis()).ifPresent(loot -> creditLoot(r, loot));
	}

	/** Method c: credit the one kill objective the loot names; else method d (unattributed, capture only). */
	private static void creditLoot(RemovalKills.Removed r, List<String> loot) {
		TrackerStore store = CubeWheelClient.tracker();
		Optional<LootMatch.Credit> credit = store == null ? Optional.empty()
				: LootMatch.match(loot, store.activeRules(local().worlds), r.world());
		if (credit.isEmpty()) {
			capture("kill", r.typeId(), r.name(), "removal, method d (loot " + loot + " names no single kill objective), local hit",
					r.world(), List.of());
			return;
		}
		List<LocalCounter.Contribution> added = LocalCounter.credit(credit.get().ruleIds(), store, System.currentTimeMillis());
		if (!added.isEmpty()) saveThrottle.markDirty();
		capture("kill", r.typeId(), credit.get().target(), "removal, method c (loot " + loot + " -> " + credit.get().target() + "), local hit",
				r.world(), added);
	}

	/** Mixin, Hud.setOverlayMessage HEAD: an action-bar message; its loot names removal kills waiting for it. */
	public static void onOverlayMessage(Component message) {
		try {
			if (message == null || !enabled(Hook.LOOT) || !local().kills) return;
			String text = message.getString();
			if (text.indexOf('+') < 0) return;
			for (RemovalKills.Resolved r : removals.onActionBar(text, System.currentTimeMillis())) creditLoot(r.removed(), r.loot());
		} catch (Throwable t) {
			fail(Hook.LOOT, t);
		}
	}

	/** Once per hit mob: remembers what names it and, while capturing, writes what is around it. */
	private static void probe(Entity target) {
		if (removals.seen(target.getId())) return;
		CaptureLog capture = CubeWheelClient.capture();
		boolean forCapture = capture != null && capture.enabled();
		NearbyProbe.Result r = NearbyProbe.of(target, forCapture);
		removals.remember(target.getId(), r.hint());
		if (forCapture) {
			capture.nearby(target.getId(), target.typeHolder().getRegisteredName(), rawName(target), r.near(),
					new ArrayList<>(world().tokens()), System.currentTimeMillis());
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
			stacks.clear();
			removals.clear();
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
			stacks.retainRoots(kills::tracks);
			if (removals.awaitingLoot()) {
				for (RemovalKills.Removed r : removals.expire(System.currentTimeMillis())) {
					capture("kill", r.typeId(), r.name(), "removal, method d (no name tag, no loot line within "
							+ RemovalKills.LOOT_WAIT_MS + " ms), local hit", r.world(), List.of());
				}
			}
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

	private static void onSnapBack(String id, net.mage.cubewheel.tracker.local.Accuracy acc) {
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

	/** The world the player is in (cached per second), or {@link WorldInfo#UNKNOWN} outside a level. */
	public static WorldInfo currentWorld() {
		try {
			return Minecraft.getInstance().level == null ? WorldInfo.UNKNOWN : world();
		} catch (Throwable t) {
			fail(Hook.LEVEL, t);
			return WorldInfo.UNKNOWN;
		}
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

	/** Watches the hit entity's name (and its passengers' name tags) for stack-count drops. */
	private static void watchStack(Entity target) {
		stacks.watch(target.getId(), target.getId(), rawName(target));
		for (Entity p : target.getPassengers()) stacks.watch(p.getId(), target.getId(), rawName(p));
	}

	private static String rawName(Entity e) {
		return e.getName().getString();
	}

	/** For capture: whether the name is a custom name, and the passengers' types and names. */
	private static String describe(Entity e) {
		StringBuilder b = new StringBuilder(e.hasCustomName() ? "custom name" : "type name");
		for (Entity p : e.getPassengers()) {
			b.append("; passenger ").append(p.typeHolder().getRegisteredName()).append('=').append(rawName(p));
		}
		return b.toString();
	}

	/** Capture only: an entity observation that changed nothing. */
	private static void captureEntity(String kind, Entity e, String detail) {
		CaptureLog capture = CubeWheelClient.capture();
		if (capture == null || !capture.enabled()) return;
		capture.local(kind, e.typeHolder().getRegisteredName(), rawName(e), detail, new ArrayList<>(world().tokens()),
				List.of(), 0, System.currentTimeMillis());
	}

	private static void capture(String kind, String id, String name, WorldInfo at, List<LocalCounter.Contribution> added) {
		capture(kind, id, name, null, at, added);
	}

	private static void capture(String kind, String id, String name, String detail, WorldInfo at,
			List<LocalCounter.Contribution> added) {
		CaptureLog capture = CubeWheelClient.capture();
		if (capture == null || !capture.enabled()) return;
		List<String> matched = new ArrayList<>();
		long units = 0;
		for (LocalCounter.Contribution c : added) {
			matched.add(c.id());
			units = c.units();
		}
		capture.local(kind, id, name, detail, new ArrayList<>(at.tokens()), matched, units, System.currentTimeMillis());
	}
}
