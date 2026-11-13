package net.mage.cubewheel.tracker.local;

import java.util.OptionalDouble;

/**
 * An axis-aligned box, used for a model mob's drawn extent: relative to its interaction hitbox in {@link ModelMob},
 * moved to the hitbox's live position when a look is ray-tested against it. Pure: no Minecraft/Fabric imports.
 */
public record ModelBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
	public static ModelBounds point(double x, double y, double z) {
		return new ModelBounds(x, y, z, x, y, z);
	}

	/**
	 * Where a display's translation (tx, ty, tz) can put its bone at any yaw: a display is turned by its entity's yaw
	 * before the translation applies, and the yaw changes as the mob turns, so this covers the whole circle of
	 * horizontal radius {@code hypot(tx, tz)} at height {@code ty}.
	 */
	public static ModelBounds aroundAxis(double tx, double ty, double tz) {
		double r = Math.hypot(tx, tz);
		return new ModelBounds(-r, ty, -r, r, ty, r);
	}

	public ModelBounds union(ModelBounds o) {
		return new ModelBounds(Math.min(minX, o.minX), Math.min(minY, o.minY), Math.min(minZ, o.minZ),
				Math.max(maxX, o.maxX), Math.max(maxY, o.maxY), Math.max(maxZ, o.maxZ));
	}

	public ModelBounds inflate(double d) {
		return new ModelBounds(minX - d, minY - d, minZ - d, maxX + d, maxY + d, maxZ + d);
	}

	public ModelBounds move(double dx, double dy, double dz) {
		return new ModelBounds(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
	}

	public boolean contains(ModelBounds o) {
		return minX <= o.minX && minY <= o.minY && minZ <= o.minZ && maxX >= o.maxX && maxY >= o.maxY && maxZ >= o.maxZ;
	}

	public boolean contains(double x, double y, double z) {
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}

	public ModelBounds withMinY(double y) {
		return new ModelBounds(minX, y, minZ, maxX, maxY, maxZ);
	}

	public double volume() {
		return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
	}

	/**
	 * The distance along the ray from (ox, oy, oz) in direction (dx, dy, dz) (any non-zero length) at which it enters
	 * this box, if that is within {@code maxDistance}; 0 when the origin is inside. Slab method.
	 */
	public OptionalDouble clip(double ox, double oy, double oz, double dx, double dy, double dz, double maxDistance) {
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len == 0 || Double.isNaN(len)) return OptionalDouble.empty();
		double[] o = {ox, oy, oz};
		double[] d = {dx / len, dy / len, dz / len};
		double[] lo = {minX, minY, minZ};
		double[] hi = {maxX, maxY, maxZ};
		double near = 0, far = maxDistance;
		for (int i = 0; i < 3; i++) {
			if (d[i] == 0) {
				if (o[i] < lo[i] || o[i] > hi[i]) return OptionalDouble.empty();
				continue;
			}
			double t1 = (lo[i] - o[i]) / d[i];
			double t2 = (hi[i] - o[i]) / d[i];
			near = Math.max(near, Math.min(t1, t2));
			far = Math.min(far, Math.max(t1, t2));
			if (near > far) return OptionalDouble.empty();
		}
		return OptionalDouble.of(near);
	}
}
