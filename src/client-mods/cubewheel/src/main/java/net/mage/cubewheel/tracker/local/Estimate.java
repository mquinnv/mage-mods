package net.mage.cubewheel.tracker.local;

/**
 * Local progress counted since the last authoritative read of one tracked entry. {@code count} is in
 * objective units (blocks, kills, fish); {@code baseline} is the entry's current value when counting
 * started; {@code since}/{@code lastAt} are epoch millis. Pure: no Minecraft/Fabric imports.
 */
public record Estimate(long count, double baseline, long since, long lastAt) {}
