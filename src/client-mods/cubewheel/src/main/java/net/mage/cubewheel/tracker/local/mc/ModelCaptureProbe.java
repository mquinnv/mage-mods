package net.mage.cubewheel.tracker.local.mc;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.mage.cubewheel.CubeWheelClient;
import net.mage.cubewheel.capture.CaptureLog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minecraft adapter, capture mode only (in a special world): records the shape of the custom-model mob the
 * crosshair hits ({@code model_shape}: the entity Jade would name, plus every entity within
 * {@link NearbyProbe#CAPTURE_RANGE} blocks of it) and every sound the client plays ({@code sound}), so mana-mob
 * names can be taken from what the client really has. Read-only; a failure is logged once and switches this off.
 */
public final class ModelCaptureProbe {
	private static final Logger LOG = LoggerFactory.getLogger("cubewheel");
	/** Entities within this many blocks of a sound count as its possible source. */
	static final double SOUND_RANGE = 3;
	private static final int MAX_PARTS = 96;
	private static final int MAX_SOUND_NEAR = 24;

	private static int lastHit = Integer.MIN_VALUE;
	private static boolean broken;

	private ModelCaptureProbe() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ModelCaptureProbe::onTick);
	}

	/** True while capture is on, in a special (mana) world, and nothing here has failed. */
	private static boolean active() {
		if (broken) return false;
		CaptureLog capture = CubeWheelClient.capture();
		return capture != null && capture.enabled() && LocalSignals.currentWorld().special();
	}

	private static void onTick(Minecraft mc) {
		try {
			if (mc.level == null || !active()) {
				lastHit = Integer.MIN_VALUE;
				return;
			}
			Entity hit = mc.crosshairPickEntity;
			if (hit == null || hit instanceof Player) {
				lastHit = Integer.MIN_VALUE;
				return;
			}
			if (hit.getId() == lastHit) return;
			lastHit = hit.getId();
			List<CaptureLog.Part> parts = new ArrayList<>();
			AABB box = hit.getBoundingBox().inflate(NearbyProbe.CAPTURE_RANGE);
			parts.add(part(hit));
			for (Entity e : mc.level.getEntities(hit, box, p -> !(p instanceof Player))) {
				if (parts.size() >= MAX_PARTS) break;
				parts.add(part(e));
			}
			CubeWheelClient.capture().modelShape(hit.getId(), hit.typeHolder().getRegisteredName(), parts,
					System.currentTimeMillis());
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			fail(t);
		}
	}

	/** Called from the sound mixin at the head of {@code SoundManager.play}. */
	public static void onSound(SoundManager manager, SoundInstance sound) {
		try {
			if (!active()) return;
			Minecraft mc = Minecraft.getInstance();
			double x = sound.getX();
			double y = sound.getY();
			double z = sound.getZ();
			List<CaptureLog.Nearby> near = new ArrayList<>();
			if (mc.level != null) {
				AABB box = new AABB(x - SOUND_RANGE, y - SOUND_RANGE, z - SOUND_RANGE, x + SOUND_RANGE, y + SOUND_RANGE, z + SOUND_RANGE);
				for (Entity e : mc.level.getEntities((Entity) null, box, p -> !(p instanceof Player))) {
					if (near.size() >= MAX_SOUND_NEAR) break;
					near.add(NearbyProbe.describe(e, Math.sqrt(e.distanceToSqr(x, y, z))));
				}
			}
			CubeWheelClient.capture().sound(sound.getIdentifier().toString(), subtitleKey(manager, sound),
					sound.getSource().getName(), x, y, z, near, System.currentTimeMillis());
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			fail(t);
		}
	}

	/** The sound's subtitle translation key, or null when it has none (or it is not a translatable component). */
	private static String subtitleKey(SoundManager manager, SoundInstance sound) {
		WeighedSoundEvents events = manager.getSoundEvent(sound.getIdentifier());
		Component subtitle = events == null ? null : events.getSubtitle();
		return subtitle != null && subtitle.getContents() instanceof TranslatableContents tc ? tc.getKey() : null;
	}

	private static CaptureLog.Part part(Entity e) {
		List<Integer> passengers = new ArrayList<>();
		for (Entity p : e.getPassengers()) passengers.add(p.getId());
		Entity v = e.getVehicle();
		CaptureLog.Nearby d = NearbyProbe.describe(e, 0);
		return new CaptureLog.Part(e.getId(), d.type(), v == null ? null : v.getId(), passengers, e.isInvisible(),
				e instanceof Mob, d.customName(), d.text(), e.getX(), e.getY(), e.getZ());
	}

	private static void fail(Throwable t) {
		broken = true;
		LOG.error("[cubewheel] model/sound capture failed; switched off until restart", t);
	}
}
