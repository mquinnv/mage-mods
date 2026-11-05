package com.github.nyuppo.config;

public abstract class HotbarCycleConfig {
    /**
     * The mage fork's default item groups (see com.github.nyuppo.group.ItemGroups): tools and weapons swap with
     * each other, and the consumables (Michael 2026-11-04: "the candy apple and the nectar ... rotate together") with
     * each other. ManaCube's staves are netherite hoes, its rods fishing rods; the Zombie Hand is paper.
     */
    public static final java.util.List<String> DEFAULT_ITEM_GROUPS = java.util.List.of(
            "@tool | @weapon | minecraft:bow | minecraft:crossbow | minecraft:trident | minecraft:mace | minecraft:fishing_rod | name:(?i)^zombie hand$ | name:(?i)wizards? book | name:(?i)magical book",
            "name:(?i)^candy apple$ | name:(?i)^bottle of nectar$ | name:(?i)^champion potion$");

    public abstract boolean getPlaySound();
    public abstract boolean getReverseCycleDirection();
    public abstract boolean getHoldAndScroll();
    public abstract boolean getRepeatSlotToCycle();
    public abstract boolean getCycleWhenPickingBlock();
    public abstract boolean getPickCyclesWholeHotbar();
    public abstract boolean getCycleByGroup();
    public abstract java.util.List<String> getItemGroups();

    public abstract boolean getEnableRow1();
    public abstract boolean getEnableRow2();
    public abstract boolean getEnableRow3();

    public abstract boolean getEnableColumn0();
    public abstract boolean getEnableColumn1();
    public abstract boolean getEnableColumn2();
    public abstract boolean getEnableColumn3();
    public abstract boolean getEnableColumn4();
    public abstract boolean getEnableColumn5();
    public abstract boolean getEnableColumn6();
    public abstract boolean getEnableColumn7();
    public abstract boolean getEnableColumn8();

    public final int getCycleDirection(){
        return this.getReverseCycleDirection() ? -1 : 1;
    }
}
