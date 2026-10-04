package net.mage.cubewheel.tracker.local.mc;

import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.ServerGate;
import net.mage.cubewheel.capture.CaptureLog;
import net.mage.cubewheel.config.CubeWheelConfig;
import net.mage.cubewheel.mixin.FishingHookAccessor;
import net.mage.cubewheel.mixin.HudAccessor;
import net.mage.cubewheel.sidebar.SidebarLinker;
import net.mage.cubewheel.tracker.TrackerStore;
import net.mage.cubewheel.cooldown.McmmoParser;
import net.mage.cubewheel.cooldown.McmmoWatcher;
import net.mage.cubewheel.tracker.local.AreaBreaks;
import net.mage.cubewheel.tracker.local.ActionBarFeed;
import net.mage.cubewheel.tracker.local.FishCatchParser;
import net.mage.cubewheel.tracker.local.FishDedup;
import net.mage.cubewheel.tracker.local.FishDetector;
import net.mage.cubewheel.tracker.local.KeyThrottle;
import net.mage.cubewheel.tracker.local.KillAttribution;
import net.mage.cubewheel.tracker.local.LocalCounter;
import net.mage.cubewheel.tracker.local.LootKills;
import net.mage.cubewheel.tracker.local.LootLine;
import net.mage.cubewheel.tracker.local.LootMatch;
import net.mage.cubewheel.tracker.local.ModelHitbox;
import net.mage.cubewheel.tracker.local.PendingBreaks;
import net.mage.cubewheel.tracker.local.QuestCompleted;
import net.mage.cubewheel.tracker.local.PendingMilk;
import net.mage.cubewheel.tracker.local.PendingShears;
import net.mage.cubewheel.tracker.local.ShearTool;
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
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minecraft adapter for local counting: the static facade that Fabric callbacks and the optional mixins
 * call. Purely passive: it only observes (own block breaks, damage/death/removal packets, action-bar
 * loot lines, reeling in a biting bobber, ManaCube's catch and quest-completed chat lines) and never sends, opens or clicks anything; its only write is the local tracker file. Active
 * only in ManaCube Survival (host gate plus sidebar title, see {@link ServerGate#survival}), in
 * survival/adventure game mode, with {@code tracker.local.enabled}. Every entry point is guarded: a failing hook is
 * logged once and switched off for the session after {@link #MAX_FAILURES} failures.
 */
public final class LocalSignals {
	/** Hook kinds that can fail independently. */
	public enum Hook { BREAK, SYNC, PLACE, ATTACK, DAMAGE, DEATH, STACK, REMOVE, LOOT, LOOT_POLL, FISH, SHEAR, MILK, TICK, LEVEL, AREA, QUEST }

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
	private static final LootKills lootKills = new LootKills();
	private static final PendingShears shears = new PendingShears();
	private static final PendingMilk milkings = new PendingMilk();
	private static final AreaBreaks areas = new AreaBreaks();
	private static final FishDedup fishDedup = new FishDedup();
	/** Unsheared shearables within this many blocks of a sheared one wait for an area shear. */
	private static final double AREA_SHEAR_RANGE = 5;
	private static final int MAX_AREA_SHEAR = 32;
	/** A hit mob removed farther away than this left our view rather than died. */
	private static final double REMOVAL_KILL_RANGE = 16;
	/** Deaths nobody near us hit are written to capture only within this many blocks. */
	private static final double CAPTURE_DEATH_RANGE = 32;
	private static final WorldProbe world = new WorldProbe();
	private static final ActionBarFeed actionBars = new ActionBarFeed();
	private static final long UNMATCHED_BREAK_WINDOW_MS = 10_000;
	private static final KeyThrottle unmatchedBreaks = new KeyThrottle(UNMATCHED_BREAK_WINDOW_MS, 256);
	/** Capture: at most one counted-area-break line per block id per this long (the summary has the totals). */
	private static final long AREA_LINE_WINDOW_MS = 2_000;
	private static final KeyThrottle areaLines = new KeyThrottle(AREA_LINE_WINDOW_MS, 256);
	/** Where the first loot line came from (logged once, proving the path works), else null. */
	private static ActionBarFeed.Source lootSourceLogged;
	private static long tick;
	/** Recomputed every client tick: in ManaCube Survival, counting enabled, a store and a player exist. */
	private static boolean active;

	private LocalSignals() {}

	public static void register() {
		ClientPlayerBlockBreakEvents.AFTER.register(LocalSignals::afterBlockBreak);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			try {
				onAttackBlock(player, level.isClientSide(), level.getBlockState(pos), pos);
			} catch (Throwable t) {
				fail(Hook.AREA, t);
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			try {
				onAttack(player, level.isClientSide(), entity);
			} catch (Throwable t) {
				fail(Hook.ATTACK, t);
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			try {
				onUseEntity(player, level.isClientSide(), entity, player.getItemInHand(hand).getHoverName().getString());
			} catch (Throwable t) {
				fail(Hook.ATTACK, t);
			}
			try {
				onShearUse(player, level.isClientSide(), entity, player.getItemInHand(hand));
			} catch (Throwable t) {
				fail(Hook.SHEAR, t);
			}
			try {
				onMilkUse(player, level.isClientSide(), entity, player.getItemInHand(hand));
			} catch (Throwable t) {
				fail(Hook.MILK, t);
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
			if (areaBreaksOn()) areas.ownBreak(p, state.is(BlockTags.LOGS), tick);
			int stateId = Block.getId(state);
			if (placed.consumeIfPlaced(p, stateId)) { // plugins ignore blocks you placed
				captureUnmatched(state.typeHolder().getRegisteredName(), state.getBlock().getName().getString(),
						"placed by you");
				return;
			}
			Signal.BlockBroken signal = BlockFacts.of(state, level, pos, world());
			List<LocalCounter.Contribution> added = count(signal);
			if (added.isEmpty()) {
				captureUnmatched(signal.id(), signal.name(), "no rule; crop=" + signal.crop() + " mature="
						+ signal.mature() + " trivial=" + signal.trivial() + " groups=" + signal.groups());
				return;
			}
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

	/**
	 * Mixin, ClientLevel.setServerVerifiedBlockState HEAD (block update and section update packets, client
	 * thread): {@code level} still holds the old state. While an area window is open, a server change of a
	 * nearby block to air counts as a break of the old block (see {@link AreaBreaks}). Returns at once when no
	 * window is open: this runs for every block in every section update.
	 */
	public static void onServerBlockState(ClientLevel level, BlockPos pos, BlockState newState) {
		if (!areas.armed(tick)) return;
		try {
			if (!areaBreaksOn()) return;
			Minecraft mc = Minecraft.getInstance();
			if (level == null || level != mc.level || !survivalMode(mc.player)) return;
			BlockState old = level.getBlockState(pos);
			if (old == newState) return;
			Pos p = new Pos(pos.getX(), pos.getY(), pos.getZ());
			String id = old.typeHolder().getRegisteredName();
			AreaBreaks.Change change = new AreaBreaks.Change(p, id, Block.getId(old), old.isAir(),
					old.getBlock() instanceof LiquidBlock, newState.isAir(), old.is(BlockTags.LOGS),
					!old.isAir() && BlockFacts.popsOff(old, level, pos));
			if (areas.onChange(change, tick, placed) != AreaBreaks.Result.COUNT) return;
			Signal.BlockBroken signal = BlockFacts.of(old, level, pos, world());
			List<LocalCounter.Contribution> added = count(signal);
			if (added.isEmpty()) areas.unmatched(id);
			CaptureLog capture = CubeWheelClient.capture();
			if (capture != null && capture.enabled() && areaLines.allow(id, System.currentTimeMillis())) {
				capture("area", id, signal.name(), (areas.treeFeller(tick) ? "tree feller; " : "") + "crop="
						+ signal.crop() + " mature=" + signal.mature(), signal.world(), added);
			}
		} catch (Throwable t) {
			fail(Hook.AREA, t);
		}
	}

	/** AttackBlockCallback (client side): starting to break a block arms an area window around it. */
	private static void onAttackBlock(Player player, boolean clientSide, BlockState state, BlockPos pos) {
		if (!clientSide || !areaBreaksOn()) return;
		if (player != Minecraft.getInstance().player || !survivalMode(player) || state.isAir()) return;
		areas.attack(new Pos(pos.getX(), pos.getY(), pos.getZ()), state.is(BlockTags.LOGS), tick);
	}

	/** A mcMMO "TREE FELLER ACTIVATED" / "... has worn off" action-bar line. */
	private static void checkAbility(String text) {
		Optional<Boolean> ended = AreaBreaks.wornOff(text);
		if (ended.isPresent()) {
			areas.abilityEnded(ended.get());
			return;
		}
		if (text.toLowerCase(java.util.Locale.ROOT).indexOf("activated") < 0) return;
		Optional<McmmoParser.Message> m = McmmoParser.parse(text);
		if (m.isEmpty() || !(m.get() instanceof McmmoParser.Activated a)) return;
		McmmoParser.Ability ability = a.ability();
		if (ability != McmmoParser.Ability.TREE_FELLER && ability != McmmoParser.Ability.SUPER_BREAKER
				&& ability != McmmoParser.Ability.GIGA_DRILL_BREAKER && ability != McmmoParser.Ability.GREEN_TERRA) return;
		Minecraft mc = Minecraft.getInstance();
		Pos at = null;
		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos bp = hit.getBlockPos();
			at = new Pos(bp.getX(), bp.getY(), bp.getZ());
		} else if (mc.player != null) {
			BlockPos bp = mc.player.blockPosition();
			at = new Pos(bp.getX(), bp.getY(), bp.getZ());
		}
		areas.ability(ability == McmmoParser.Ability.TREE_FELLER, at, tick);
		CaptureLog capture = CubeWheelClient.capture();
		if (capture != null && capture.enabled()) {
			capture.local("area", null, ability.label, "ability activated; window at " + at, new ArrayList<>(world().tokens()),
					List.of(), 0, System.currentTimeMillis());
		}
	}

	private static boolean lootOn() {
		return enabled(Hook.LOOT) && local().kills;
	}

	private static boolean areaBreaksOn() {
		return enabled(Hook.AREA) && local().blocks && local().areaBreaks;
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
			removals.hit(target.getId(), System.currentTimeMillis());
			watchStack(target);
			captureEntity("attack", target, describe(target));
			probe(target);
			hitModel(target.getId(), player.getId());
		} catch (Throwable t) {
			fail(Hook.ATTACK, t);
		}
	}

	/**
	 * UseEntityCallback (client side): using an item on an entity (a Firefly Bottle on a firefly's
	 * interaction hitbox) counts as a local hit for removal kills, like an attack. With a Firefly Bottle in
	 * hand the removal falls back to "Firefly" loot when no loot line names it. Living mobs count only with
	 * such a bottle: right-clicking a villager or a pet is not an attack.
	 */
	private static void onUseEntity(Player player, boolean clientSide, Entity target, String heldItem) {
		try {
			if (!clientSide || !enabled(Hook.ATTACK) || !local().kills) return;
			Minecraft mc = Minecraft.getInstance();
			if (player != mc.player || !survivalMode(player) || target == null || target instanceof Player) return;
			String bottle = RemovalKills.bottleLoot(heldItem);
			if (bottle == null && target instanceof LivingEntity) return;
			kills.onDamage(target.getId(), player.getId(), tick);
			removals.used(target.getId(), System.currentTimeMillis()); // not an attack: never a monster kill alone
			removals.fallback(target.getId(), bottle);
			captureEntity("use", target, describe(target) + "; held " + heldItem);
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
					removals.hit(entityId, System.currentTimeMillis());
					watchStack(target);
					probe(target); // area hits (a katana's sweep) have no attack callback
					hitModel(entityId, causeId);
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
			killCounted();
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
			killCounted();
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
				if (entity != null && neverAKill(entity)) {
					// A deflected breeze wind charge was "killed" through a nearby armor stand's tag (2026-10-01).
					captureEntity("removed", entity, "hit, but a projectile or vehicle is never a kill");
				} else if (entity != null && !(entity instanceof Player)) {
					// Model hitboxes go 2-4.6 s after the last hit on them (an interaction, an invisible slime).
					int window = hitboxShape(entity) ? RemovalKills.HITBOX_WINDOW_TICKS : RemovalKills.WINDOW_TICKS;
					boolean recent = kills.hitByWithin(id, mc.player.getId(), tick, window);
					boolean near = entity.distanceTo(mc.player) <= REMOVAL_KILL_RANGE;
					String why = !recent ? (kills.hitBy(id, mc.player.getId(), tick) ? "local hit, too long ago" : "no recent local hit")
							: !near ? "local hit, too far to be a kill"
							: removals.awaitingLoot(id) ? "local hit, pending: waiting for loot line"
							: removals.settled(id) ? "local hit, already counted or refused"
							: !survivalMode(mc.player) ? "local hit, not in survival mode" : null;
					if (why == null && removals.claim(id, true, true, inHitboxWindow(mc.player.getId()))) {
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

	/**
	 * A removal counted as a kill: named from the hint found at hit time (looked for again now if there was none: the
	 * tag may have rendered since), else from the next loot line; a model hitbox nothing names still counts as a
	 * monster.
	 */
	private static void onRemovalKill(Entity entity) {
		WorldInfo at = world();
		String typeId = entity.typeHolder().getRegisteredName();
		Optional<RemovalKills.Hint> hint = removals.hint(entity.getId());
		if (hint.isEmpty()) {
			// Only a tag tied to the hitbox's own model: its own tag may go in the same packet, leaving a neighbour's.
			NearbyProbe.Result again = NearbyProbe.of(entity, false);
			if (removals.acceptReprobe(entity.getId(), again.hint(), again.tagTies())) {
				removals.remember(entity.getId(), again.hint());
				removals.settleModel(entity.getId(), inHitboxWindow(Minecraft.getInstance().player.getId()));
				hint = Optional.of(again.hint());
			}
		}
		// A tag names a kill only after an attack (or a Firefly Bottle): a right-clicked NPC/mount/crate is no kill.
		if (hint.isPresent() && removals.nameCounts(entity.getId(), hint.get())) {
			RemovalKills.Hint h = hint.get();
			Signal.MobKilled facts = EntityFacts.of(entity, at, h.name(), 1);
			// A name from another entity, or an invisible hitbox: the hitbox's type ("slime") is not the mob.
			boolean hitbox = h.method() != RemovalKills.Method.OWN || entity.isInvisible();
			Signal.MobKilled signal = hitbox
					? new Signal.MobKilled("", facts.name(), Set.of("mob", "monster"), 1, at)
					: facts;
			List<LocalCounter.Contribution> added = count(signal);
			killCounted();
			capture("kill", typeId, h.name(), "removal, method " + h.method().code + " (" + h.source() + "), local hit", at, added);
			return;
		}
		// Only an attacked model hitbox: not a right-clicked NPC/mount/crate, nor a Firefly Bottle catch.
		boolean generic = removals.generic(entity.getId(), ModelHitbox.isModelHitbox(NearbyProbe.hitbox(entity)));
		RemovalKills.Removed r = new RemovalKills.Removed(entity.getId(), typeId, rawName(entity), at, generic);
		removals.awaitLoot(r, System.currentTimeMillis()).ifPresent(loot -> creditLoot(r, loot));
	}

	/**
	 * Method c: credit the one kill objective the loot names (and, for a model hitbox, the generic kill objectives);
	 * else method d (unnamed: a model hitbox counts for the generic kill objectives only).
	 */
	private static void creditLoot(RemovalKills.Removed r, List<String> loot) {
		creditLoot(r, loot, "loot");
	}

	private static void creditLoot(RemovalKills.Removed r, List<String> loot, String from) {
		TrackerStore store = CubeWheelClient.tracker();
		Optional<LootMatch.Credit> credit = store == null ? Optional.empty()
				: LootMatch.match(loot, store.activeRules(local().worlds), r.world());
		if (credit.isEmpty()) {
			Optional<String> bottle = removals.fallback(r.entityId());
			if (from.equals("loot") && bottle.isPresent()) {
				creditLoot(r, List.of(bottle.get()), "held item");
				return;
			}
			killCounted();
			capture("kill", r.typeId(), r.name(), "removal, method d (" + from + " " + loot + " names no single kill objective"
					+ (r.modelHitbox() ? "; model hitbox: generic" : "") + "), local hit", r.world(), genericKill(r));
			return;
		}
		List<LocalCounter.Contribution> added = LocalCounter.lootKill(credit.get().ruleIds(), r.modelHitbox(), r.world(),
				store, local().worlds, System.currentTimeMillis());
		if (!added.isEmpty()) saveThrottle.markDirty();
		killCounted();
		capture("kill", r.typeId(), credit.get().target(), "removal, method c (" + from + " " + loot + " -> " + credit.get().target() + "), local hit",
				r.world(), added);
	}

	/** Mixin, Hud.setOverlayMessage HEAD: an action-bar message being shown. */
	public static void onOverlayMessage(Component message) {
		if (message != null) net.mage.cubewheel.cooldown.CooldownWatcher.onActionBar(message.getString());
		McmmoWatcher.onActionBarEvent(ActionBarFeed.Source.HUD, message);
		onActionBarEvent(ActionBarFeed.Source.HUD, message);
	}

	/** Mixin, ClientPacketListener.setActionBarText (client thread): an action-bar packet's text. */
	public static void onActionBarPacket(Component message) {
		McmmoWatcher.onActionBarEvent(ActionBarFeed.Source.PACKET, message);
		onActionBarEvent(ActionBarFeed.Source.PACKET, message);
	}

	private static void onActionBarEvent(ActionBarFeed.Source source, Component message) {
		try {
			if (message == null || !(lootOn() || areaBreaksOn())) return;
			String text = message.getString();
			long now = System.currentTimeMillis();
			if (actionBars.event(source, text, now)) onActionBar(source, text, now);
		} catch (Throwable t) {
			fail(Hook.LOOT, t);
		}
	}

	/**
	 * Each client tick while counting kills: the Hud's current action-bar text and remaining display ticks,
	 * so loot lines are seen even when neither hook fires (see {@link ActionBarFeed}).
	 */
	private static void pollActionBar(Minecraft mc) {
		if (!enabled(Hook.LOOT_POLL) || !(local().kills || areaBreaksOn())) return;
		try {
			HudAccessor hud = (HudAccessor) mc.gui.hud;
			Component message = hud.cubewheel$getOverlayMessage();
			String text = message == null ? null : message.getString();
			long now = System.currentTimeMillis();
			if (actionBars.poll(text, hud.cubewheel$getOverlayMessageTime(), now)) onActionBar(ActionBarFeed.Source.POLL, text, now);
		} catch (Throwable t) {
			fail(Hook.LOOT_POLL, t);
		}
	}

	/** An action-bar message, once: its loot names removal kills waiting for it (or the next removal). */
	private static void onActionBar(ActionBarFeed.Source source, String text, long now) {
		if (areaBreaksOn()) {
			try {
				checkAbility(text);
			} catch (Throwable t) {
				fail(Hook.AREA, t);
			}
		}
		if (!lootOn() || text.indexOf('+') < 0) return;
		lootKills.onActionBar(text, world(), now); // every kill line, also one that names a pending removal below
		if (lootSourceLogged == null && !LootLine.items(text).isEmpty()) {
			lootSourceLogged = source;
			CubeWheelClient.LOG.info("[cubewheel] loot lines: {}", source.label);
		}
		for (RemovalKills.Resolved r : removals.onActionBar(text, now)) creditLoot(r.removed(), r.loot());
	}

	/** Once per hit mob: remembers what names it, its model, and, while capturing, writes what is around it. */
	private static void probe(Entity target) {
		if (removals.seen(target.getId())) return;
		CaptureLog capture = CubeWheelClient.capture();
		boolean forCapture = capture != null && capture.enabled();
		NearbyProbe.Result r = NearbyProbe.of(target, forCapture);
		removals.remember(target.getId(), r.hint());
		removals.model(target.getId(), r.modelKeys());
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
			long now = System.currentTimeMillis();
			Signal.FishCaught signal = new Signal.FishCaught(world());
			if (!fishDedup.hookAllowed(now)) {
				capture("fish", null, null, "bobber reel-in; same catch as the chat line, not counted", signal.world(), List.of());
				return;
			}
			List<LocalCounter.Contribution> added = count(signal);
			fishDedup.hookCounted(now, added);
			capture("fish", null, null, "bobber reel-in", signal.world(), added);
		} catch (Throwable t) {
			fail(Hook.FISH, t);
		}
	}

	/**
	 * ClientReceiveMessageEvents.ALLOW_GAME: ManaCube's catch line ("You caught a 52.2cm Common Flounder") is
	 * a catch of that species; its custom fishing never makes the vanilla bobber bite. "You have completed the
	 * Volcano Potion Quest!" marks that quest's "Complete the …" objective done (see {@link QuestCompleted}).
	 * Never hides anything.
	 */
	public static boolean onGameMessage(Component message, boolean overlay) {
		if (message == null || overlay || !active) return true;
		try {
			if (enabled(Hook.FISH) && local().fish && survivalMode(Minecraft.getInstance().player)) {
				for (String line : message.getString().split("\n")) {
					Optional<FishCatchParser.Catch> c = FishCatchParser.parse(line);
					if (c.isEmpty()) continue;
					onChatCatch(c.get());
					break;
				}
			}
		} catch (Throwable t) {
			fail(Hook.FISH, t);
		}
		try {
			if (enabled(Hook.QUEST) && survivalMode(Minecraft.getInstance().player)) {
				for (String line : message.getString().split("\n")) {
					Optional<String> quest = QuestCompleted.parse(line);
					if (quest.isEmpty()) continue;
					onQuestCompleted(quest.get());
					break;
				}
			}
		} catch (Throwable t) {
			fail(Hook.QUEST, t);
		}
		return true;
	}

	/** You finished {@code quest}: its "Complete the …" objectives are estimated done until the next menu read. */
	private static void onQuestCompleted(String quest) {
		List<LocalCounter.Contribution> added =
				LocalCounter.questCompleted(quest, CubeWheelClient.tracker(), System.currentTimeMillis());
		if (!added.isEmpty()) saveThrottle.markDirty();
		capture("quest", null, quest, "chat: you completed it", world(), added);
	}

	private static void onChatCatch(FishCatchParser.Catch c) {
		List<LocalCounter.Contribution> undo = fishDedup.chatCatch(System.currentTimeMillis());
		if (!undo.isEmpty()) {
			LocalCounter.reverse(undo, CubeWheelClient.tracker());
			saveThrottle.markDirty();
		}
		Signal.FishCaught signal = new Signal.FishCaught(c.species(), c.rarity(), world());
		capture("fish", null, c.species(), "chat; rarity=" + c.rarity() + " size=" + c.sizeCm() + "cm"
				+ (undo.isEmpty() ? "" : "; replaces the bobber reel-in"), signal.world(), count(signal));
	}

	/**
	 * UseEntityCallback (client side): shears (vanilla or a custom "Shears" tool) used on a shearable entity
	 * that is ready (a sheep: not sheared, not a baby). Records it, and the ready shearables within
	 * {@link #AREA_SHEAR_RANGE} blocks (an area shears tool), as pending; {@link #checkShears} counts each
	 * one whose sheared state syncs true within its window. Nothing counts at use time.
	 */
	private static void onShearUse(Player player, boolean clientSide, Entity target, ItemStack held) {
		if (!clientSide || !enabled(Hook.SHEAR) || !local().shear) return;
		Minecraft mc = Minecraft.getInstance();
		if (player != mc.player || !survivalMode(player) || !(target instanceof Shearable)) return;
		if (held == null || held.isEmpty()) return;
		String heldName = held.getHoverName().getString();
		if (!ShearTool.isShears(held.getItem() instanceof ShearsItem, held.typeHolder().getRegisteredName(), heldName, lore(held))) {
			return;
		}
		boolean ready = readyForShears(target);
		List<Integer> near = new ArrayList<>();
		if (ready) {
			for (Entity e : target.level().getEntities(target, target.getBoundingBox().inflate(AREA_SHEAR_RANGE),
					e -> e instanceof Shearable && !(e instanceof Player))) {
				if (near.size() >= MAX_AREA_SHEAR) break;
				if (e.distanceTo(target) <= AREA_SHEAR_RANGE && readyForShears(e)) near.add(e.getId());
			}
			shears.use(target.getId(), near, tick);
		}
		captureEntity("use", target, "shears (held " + heldName + "); sheared before=" + !ready
				+ (ready ? "; pending, " + near.size() + " ready nearby" : "; not shearable now, ignored"));
	}

	/** Each client tick while shears are pending: confirm, drop or keep only the pending ids. */
	private static void checkShears(Minecraft mc) {
		if (!enabled(Hook.SHEAR)) return;
		try {
			for (PendingShears.Outcome o : shears.tick(tick, id -> shearState(mc.level.getEntity(id)))) {
				Entity e = mc.level.getEntity(o.entityId());
				String role = o.area() ? "area" : "target";
				if (o.result() == PendingShears.Result.CONFIRMED && e != null && local().shear) {
					Signal.Sheared signal = new Signal.Sheared(e.typeHolder().getRegisteredName(), rawName(e), world());
					capture("shear", signal.typeId(), signal.name(), "confirmed, " + role, signal.world(), count(signal));
				} else if (!o.area()) {
					// Area entries that were never sheared are the norm; only the used-on entity is explained.
					CaptureLog capture = CubeWheelClient.capture();
					if (capture != null && capture.enabled()) {
						capture.local("shear", e == null ? "#" + o.entityId() : e.typeHolder().getRegisteredName(),
								e == null ? null : rawName(e), "not counted: " + o.result().name().toLowerCase(java.util.Locale.ROOT)
										+ ", " + role, new ArrayList<>(world().tokens()), List.of(), 0, System.currentTimeMillis());
					}
				}
			}
		} catch (Throwable t) {
			fail(Hook.SHEAR, t);
		}
	}

	/**
	 * UseEntityCallback (client side): an empty bucket used on a grown cow, mooshroom or goat. Records it with the
	 * empty buckets held just before; {@link #checkMilk} counts it once the server has kept the swap.
	 */
	private static void onMilkUse(Player player, boolean clientSide, Entity target, ItemStack held) {
		if (!clientSide || !enabled(Hook.MILK) || !local().milk) return;
		Minecraft mc = Minecraft.getInstance();
		if (player != mc.player || !survivalMode(player) || !held.is(Items.BUCKET)) return;
		if (!(target instanceof AbstractCow || target instanceof Goat) || !target.isAlive()) return;
		if (target instanceof net.minecraft.world.entity.AgeableMob a && a.isBaby()) return;
		milkings.use(tick, emptyBuckets(player), target.typeHolder().getRegisteredName(), rawName(target));
		captureEntity("use", target, "bucket; milking pending");
	}

	/** Each client tick while milkings are pending: count those whose bucket stayed gone. */
	private static void checkMilk(Minecraft mc) {
		if (!enabled(Hook.MILK)) return;
		try {
			for (PendingMilk.Outcome o : milkings.tick(tick, emptyBuckets(mc.player))) {
				if (o.confirmed() && local().milk) {
					Signal.Milked signal = new Signal.Milked(o.typeId(), o.name(), world());
					capture("milk", signal.typeId(), signal.name(), "confirmed", signal.world(), count(signal));
				} else {
					CaptureLog capture = CubeWheelClient.capture();
					if (capture != null && capture.enabled()) {
						capture.local("milk", o.typeId(), o.name(), "not counted: bucket came back",
								new ArrayList<>(world().tokens()), List.of(), 0, System.currentTimeMillis());
					}
				}
			}
		} catch (Throwable t) {
			fail(Hook.MILK, t);
		}
	}

	/** Empty buckets anywhere in the player's inventory (hands included). */
	private static int emptyBuckets(Player player) {
		if (player == null) return 0;
		int n = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.BUCKET)) n += s.getCount();
		}
		return n;
	}

	/** Can shears act on it now: a sheep that is alive, unsheared and grown; other shearables by their own test. */
	private static boolean readyForShears(Entity e) {
		if (e == null || !e.isAlive()) return false;
		if (e instanceof Sheep s) return !s.isSheared() && !s.isBaby();
		return e instanceof Shearable sh && sh.readyForShearing();
	}

	/** A pending entity's state: removed or dead is gone; a sheep by its sheared flag, others by readiness lost. */
	private static PendingShears.State shearState(Entity e) {
		if (e == null || e.isRemoved() || !e.isAlive()) return PendingShears.State.GONE;
		if (e instanceof Sheep s) return s.isSheared() ? PendingShears.State.SHEARED : PendingShears.State.NOT_YET;
		if (e instanceof Shearable sh) return sh.readyForShearing() ? PendingShears.State.NOT_YET : PendingShears.State.SHEARED;
		return PendingShears.State.GONE;
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static void onLevelChange() {
		try {
			shears.clear();
			milkings.clear();
			areas.clear();
			pending.clear();
			placed.clear();
			kills.clear();
			stacks.clear();
			removals.clear();
			lootKills.clear();
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
			areas.expire(tick);
			if (active) captureAreaSummary();
			kills.expire(tick);
			stacks.retainRoots(kills::tracks);
			if (!active) shears.clear();
			else if (!shears.isEmpty()) checkShears(mc);
			if (!active) milkings.clear();
			else if (!milkings.isEmpty()) checkMilk(mc);
			if (active) pollActionBar(mc); // before expiry: a line that just arrived still names a waiting removal
			if (removals.awaitingLoot()) {
				for (RemovalKills.Removed r : removals.expire(System.currentTimeMillis())) {
					Optional<String> bottle = removals.fallback(r.entityId());
					if (bottle.isPresent()) {
						creditLoot(r, List.of(bottle.get()), "held item");
						continue;
					}
					killCounted();
					capture("kill", r.typeId(), r.name(), "removal, method d (no name tag, no loot line within "
							+ RemovalKills.LOOT_WAIT_MS + " ms" + (r.modelHitbox() ? "; model hitbox: generic" : "") + "), local hit",
							r.world(), genericKill(r));
				}
			}
			expireLootKills(mc); // after the removals: a kill counted this tick still claims its line
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

	/**
	 * A local hit on one hitbox of a custom-model mob is a hit on the model: its other hitboxes' hits are refreshed,
	 * so the interaction hit once and then left for the slimes is still ours when it is removed.
	 */
	private static void hitModel(int entityId, int playerId) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !hitboxShape(level.getEntity(entityId))) return;
		long now = System.currentTimeMillis();
		for (int sibling : removals.siblings(entityId)) {
			// Only hitboxes: two plain mobs by the same hologram are not one model, and a refresh would credit a death.
			if (!hitboxShape(level.getEntity(sibling))) continue;
			kills.onDamage(sibling, playerId, tick);
			removals.hit(sibling, now); // attacked through the model
		}
	}

	/** Siblings the local player hit within the hitbox window count with a claimed removal (see {@link RemovalKills#claim}). */
	private static java.util.function.IntPredicate inHitboxWindow(int playerId) {
		return sibling -> kills.hitByWithin(sibling, playerId, tick, RemovalKills.HITBOX_WINDOW_TICKS);
	}

	/** An interaction or an invisible entity: what a custom model's hitbox looks like (it gets the longer window). */
	private static boolean hitboxShape(Entity e) {
		return e != null && (ModelHitbox.INTERACTION.equals(e.typeHolder().getRegisteredName()) || e.isInvisible());
	}

	/** Projectiles and vehicles: their removal is never a kill, whatever was hit. */
	private static boolean neverAKill(Entity e) {
		return e instanceof Projectile || e instanceof VehicleEntity || ModelHitbox.neverAKill(e.typeHolder().getRegisteredName());
	}

	/** A kill was counted locally (any path): it claims its action-bar loot line (see {@link LootKills}). */
	private static void killCounted() {
		lootKills.counted(System.currentTimeMillis());
	}

	/**
	 * Loot lines no local kill claimed within {@link LootKills#GRACE_MS}: kills by an ability weapon (Phoenix Staff,
	 * magic book), which the client never sees as ours. Each counts for the rules its drops name plus the generic
	 * monster rules, like method c. Held lines are dropped while kills are not counted.
	 */
	private static void expireLootKills(Minecraft mc) {
		try {
			if (!lootOn()) { // also off outside ManaCube Survival or with tracker.local disabled (see enabled)
				lootKills.clear();
				return;
			}
			long now = System.currentTimeMillis();
			for (LootKills.Kill k : lootKills.expire(now)) {
				if (!survivalMode(mc.player)) continue;
				LootKills.Credited c = LootKills.credit(k, CubeWheelClient.tracker(), local().worlds, now);
				if (!c.added().isEmpty()) saveThrottle.markDirty();
				capture("kill", null, c.target().orElse(null), "loot line only (ability kill): " + k.loot()
						+ c.target().map(t -> " -> " + t).orElse(" names no kill objective; generic"), k.world(), c.added());
			}
		} catch (Throwable t) {
			fail(Hook.LOOT, t);
		}
	}

	/** Method d for a model hitbox: a monster kill nothing names counts for the generic kill objectives only. */
	private static List<LocalCounter.Contribution> genericKill(RemovalKills.Removed r) {
		TrackerStore store = CubeWheelClient.tracker();
		if (!r.modelHitbox() || store == null) return List.of();
		List<LocalCounter.Contribution> added = LocalCounter.genericKill(r.world(), store, local().worlds,
				System.currentTimeMillis(), Set.of());
		if (!added.isEmpty()) saveThrottle.markDirty();
		return added;
	}

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

	/**
	 * Capture only: an own block break that counted nowhere ("break" with matched []), at most one line per
	 * block id per {@link #UNMATCHED_BREAK_WINDOW_MS}, so a capture shows whether a missed objective's
	 * blocks reached the client at all. None at all while the server's counter rises means the blocks were
	 * broken server-side (area/harvester tools), which local counting cannot see.
	 */
	private static void captureUnmatched(String id, String name, String detail) {
		CaptureLog capture = CubeWheelClient.capture();
		if (capture == null || !capture.enabled()) return;
		long now = System.currentTimeMillis();
		if (!unmatchedBreaks.allow(id, now)) return;
		capture.local("break", id, name, detail, new ArrayList<>(world().tokens()), List.of(), 0, now);
	}

	/** Capture only, at most every few seconds: what area windows saw (see {@link AreaBreaks#summary}). */
	private static void captureAreaSummary() {
		CaptureLog capture = CubeWheelClient.capture();
		if (capture == null || !capture.enabled()) return;
		long now = System.currentTimeMillis();
		areas.summary(now).ifPresent(s -> capture.local("area", null, null, s, new ArrayList<>(world().tokens()),
				List.of(), 0, now));
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
