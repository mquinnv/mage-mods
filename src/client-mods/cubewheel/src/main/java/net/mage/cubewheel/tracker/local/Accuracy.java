package net.mage.cubewheel.tracker.local;

/**
 * How a snapped-back estimate compared with the next authoritative read: {@code counted} objective units
 * counted locally, {@code actual} units the menu says were done in the same time. Logged, never applied.
 * Pure: no Minecraft/Fabric imports.
 */
public record Accuracy(long counted, double actual) {}
