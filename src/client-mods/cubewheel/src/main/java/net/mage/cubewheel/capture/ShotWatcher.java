package net.mage.cubewheel.capture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.mage.cubewheel.CubeWheelClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capture only: a right-click with a staff and what the server sends back for it. Michael (2026-10-12): "the
 * phoenix staff aiming is whack. i feel like i have to aim below and to the right of a target to hit it", on
 * nothing else. The client never sees how the server launches the shot, so this records enough to work it out
 * from a few shots: at the click, the eye position, yaw/pitch and the block the crosshair ray meets (a "shot"
 * line); then, for the next {@link #WINDOW_TICKS}, every entity that appears near the player (no players), where
 * it appears relative to the eye and the look frame, how it moves, and where it is when it goes or the window
 * ends ("projectile" lines, see {@link ShotGeometry.View}). A fake shot drawn with particles only shows as a
 * "shot" line with no projectile after it, which is an answer too. Nothing is sent and nothing is drawn.
 */
public final class ShotWatcher {
	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	private static final int WINDOW_TICKS = 40;
	private static final double RANGE = 64;
	private static final double RAY = 96;
	private static final int MAX_FAILURES = 5;
	/** Ticks after first sight at which a tracked entity is logged again, to show its path. */
	private static final Set<Integer> CHECKPOINTS = Set.of(2, 5, 10, 20);

	private record Tracked(String type, String name, int firstTick, Vec3 first, Vec3 last) {
		Tracked moved(Vec3 pos) { return new Tracked(type, name, firstTick, first, pos); }
	}

	private static int failures;
	private static int ticksLeft;
	private static int tick;
	private static long shotAt;
	private static String shotItem;
	private static Vec3 eye;
	private static float yaw, pitch;
	private static Set<Integer> before = Set.of();
	private static final Map<Integer, Tracked> tracked = new HashMap<>();
	private static long lastUseAt;

	private ShotWatcher() {}

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
		ClientTickEvents.END_CLIENT_TICK.register(ShotWatcher::onEndTick);
	}

	/** Whether a use of {@code name} is a shot worth recording: any staff. */
	static boolean shoots(String name) {
		return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("staff");
	}

	private static void onUse(Player player, Level level, InteractionHand hand) {
		try {
			if (failures >= MAX_FAILURES || !level.isClientSide() || player != Minecraft.getInstance().player) return;
			CaptureLog capture = CubeWheelClient.capture();
			if (capture == null || !capture.enabled()) return;
			ItemStack stack = player.getItemInHand(hand);
			String name = stack.isEmpty() ? null : stack.getHoverName().getString().replaceAll("§.", "").trim();
			if (!shoots(name)) return;
			long now = System.currentTimeMillis();
			// The block and item hooks both fire for one click: one shot per 200 ms.
			if (now - lastUseAt < 200) return;
			lastUseAt = now;
			LocalPlayer p = (LocalPlayer) player;
			eye = p.getEyePosition();
			yaw = p.getYRot();
			pitch = p.getXRot();
			shotAt = now;
			shotItem = name;
			tick = 0;
			ticksLeft = WINDOW_TICKS;
			tracked.clear();
			before = new HashSet<>();
			for (Entity e : level.getEntities(p, new AABB(eye, eye).inflate(RANGE))) before.add(e.getId());

			JsonObject o = new JsonObject();
			o.addProperty("item", name);
			o.addProperty("hand", hand == InteractionHand.MAIN_HAND ? "main" : "off");
			o.add("eye", vec(eye));
			o.addProperty("yaw", yaw);
			o.addProperty("pitch", pitch);
			o.add("look", vec(p.getLookAngle()));
			o.addProperty("sneaking", p.isShiftKeyDown());
			o.addProperty("moving", p.getDeltaMovement().horizontalDistanceSqr() > 1e-6);
			// Where the crosshair ray meets terrain (any distance), and what vanilla picked within reach.
			BlockHitResult ray = level.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(RAY)),
					ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
			if (ray.getType() == HitResult.Type.BLOCK) {
				o.add("rayBlock", vec(ray.getLocation()));
				o.addProperty("rayDistance", round(ray.getLocation().distanceTo(eye)));
			}
			HitResult pick = Minecraft.getInstance().hitResult;
			if (pick instanceof EntityHitResult eh) {
				Entity t = eh.getEntity();
				o.addProperty("pickEntity", t.typeHolder().getRegisteredName());
				o.addProperty("pickName", t.getName().getString());
				o.addProperty("pickDistance", round(eh.getLocation().distanceTo(eye)));
			} else if (pick instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
				o.addProperty("pickDistance", round(bh.getLocation().distanceTo(eye)));
			}
			capture.event("shot", o, now);
		} catch (RuntimeException e) {
			fail(e);
		}
	}

	private static void onEndTick(Minecraft mc) {
		if (ticksLeft <= 0) return;
		try {
			CaptureLog capture = CubeWheelClient.capture();
			LocalPlayer p = mc.player;
			if (capture == null || !capture.enabled() || p == null || mc.level == null) {
				ticksLeft = 0;
				return;
			}
			tick++;
			ticksLeft--;
			long now = System.currentTimeMillis();
			Set<Integer> seen = new HashSet<>();
			for (Entity e : mc.level.getEntities(p, new AABB(eye, eye).inflate(RANGE))) {
				if (e instanceof Player || before.contains(e.getId())) continue;
				seen.add(e.getId());
				Tracked t = tracked.get(e.getId());
				Vec3 pos = e.position();
				if (t == null) {
					t = new Tracked(e.typeHolder().getRegisteredName(), e.hasCustomName() ? e.getCustomName().getString() : null,
							tick, pos, pos);
					tracked.put(e.getId(), t);
					log(capture, e.getId(), t, pos, e.getDeltaMovement(), "spawn", now);
				} else {
					tracked.put(e.getId(), t.moved(pos));
					if (CHECKPOINTS.contains(tick - t.firstTick())) {
						log(capture, e.getId(), t, pos, e.getDeltaMovement(), "path", now);
					}
				}
			}
			List<Integer> gone = new ArrayList<>();
			for (Map.Entry<Integer, Tracked> en : tracked.entrySet()) {
				if (!seen.contains(en.getKey())) gone.add(en.getKey());
			}
			for (Integer id : gone) {
				Tracked t = tracked.remove(id);
				log(capture, id, t, t.last(), null, "gone", now);
			}
			if (ticksLeft == 0) {
				for (Map.Entry<Integer, Tracked> en : tracked.entrySet()) {
					log(capture, en.getKey(), en.getValue(), en.getValue().last(), null, "end", now);
				}
				tracked.clear();
			}
		} catch (RuntimeException e) {
			ticksLeft = 0;
			fail(e);
		}
	}

	/** One "projectile" line: an entity's place and motion relative to the shot's eye and look. */
	private static void log(CaptureLog capture, int id, Tracked t, Vec3 pos, Vec3 velocity, String stage, long now) {
		JsonObject o = new JsonObject();
		o.addProperty("stage", stage);
		o.addProperty("item", shotItem);
		o.addProperty("shotT", shotAt);
		o.addProperty("ageTicks", tick - t.firstTick());
		o.addProperty("sinceShotTicks", tick);
		o.addProperty("entity", id);
		o.addProperty("type", t.type());
		if (t.name() != null) o.addProperty("name", t.name());
		o.add("pos", vec(pos));
		ShotGeometry.View at = ShotGeometry.view(new double[] {eye.x, eye.y, eye.z}, yaw, pitch,
				new double[] {pos.x, pos.y, pos.z});
		o.add("fromEye", view(at));
		if (velocity != null && velocity.lengthSqr() > 1e-8) {
			o.add("velocity", vec(velocity));
			o.add("heading", view(ShotGeometry.direction(yaw, pitch, velocity.x, velocity.y, velocity.z)));
		}
		if (!pos.equals(t.first())) {
			Vec3 d = pos.subtract(t.first());
			o.add("travel", view(ShotGeometry.direction(yaw, pitch, d.x, d.y, d.z)));
		}
		capture.event("projectile", o, now);
	}

	private static JsonObject view(ShotGeometry.View v) {
		JsonObject o = new JsonObject();
		o.addProperty("forward", round(v.forward()));
		o.addProperty("right", round(v.right()));
		o.addProperty("up", round(v.up()));
		o.addProperty("dyaw", round(v.dyaw()));
		o.addProperty("dpitch", round(v.dpitch()));
		o.addProperty("distance", round(v.distance()));
		return o;
	}

	private static JsonArray vec(Vec3 v) {
		JsonArray a = new JsonArray();
		a.add(round(v.x));
		a.add(round(v.y));
		a.add(round(v.z));
		return a;
	}

	private static double round(double d) {
		return Math.round(d * 1000) / 1000.0;
	}

	private static void fail(RuntimeException e) {
		failures++;
		LOG.warn("[cubewheel] shot capture failed ({}/{}): {}", failures, MAX_FAILURES, e.toString());
	}
}
