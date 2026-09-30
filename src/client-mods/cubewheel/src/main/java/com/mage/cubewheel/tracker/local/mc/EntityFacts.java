package com.mage.cubewheel.tracker.local.mc;

import com.mage.cubewheel.tracker.local.Signal;
import com.mage.cubewheel.tracker.local.StackName;
import com.mage.cubewheel.tracker.local.WorldInfo;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** Minecraft adapter: a killed entity's type id, bare name and groups (mob, monster, player). */
final class EntityFacts {
	private EntityFacts() {}

	static Signal.MobKilled of(Entity e, WorldInfo world) {
		return of(e, world, e.getName().getString(), 1);
	}

	/** {@code count} mobs of {@code e} killed, named {@code rawName} (a stack's name tag may be a passenger's). */
	static Signal.MobKilled of(Entity e, WorldInfo world, String rawName, int count) {
		String id = e.typeHolder().getRegisteredName();
		String name = StackName.parse(rawName).map(StackName.Parsed::name).orElse("");
		Set<String> groups = new HashSet<>();
		if (e instanceof Player) groups.add("player");
		if (e instanceof Mob) groups.add("mob");
		// Custom-named mobs are ManaCube's world mobs ("Mana Wolf", "Mana Slime").
		if (e instanceof Enemy || e instanceof NeutralMob || (e instanceof Mob && e.hasCustomName())) groups.add("monster");
		return new Signal.MobKilled(id, name, groups, count, world);
	}
}
