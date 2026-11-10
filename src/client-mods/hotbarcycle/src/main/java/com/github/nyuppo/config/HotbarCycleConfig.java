package com.github.nyuppo.config;

public abstract class HotbarCycleConfig {
    /**
     * The mage fork's default rules (see com.github.nyuppo.group.ItemGroups), first match wins: groups melee, ranged,
     * tools, utility and consumables, plus "none" for what is never cycled (Michael 2026-11-04: tools and weapons
     * apart, "weapon axes from farming axes ... scythes too", "melee and ranged weapons separate", tridents melee,
     * Hermes Caduceus and Photon Barrier "kinda weirdos"). Tuned against ManaCube's whole custom-item catalog
     * (SVAS, ~1,250 items, 2026-11-04), not just Michael's items:
     * <ul>
     *   <li>armor, shields, pendants, off-hand and one-time items, and talismans (passive: "Automatically sells
     *       monster drops", Michael 2026-11-07): never cycled;</li>
     *   <li>axes that drop mob heads (Decapitation, the Bone Axe's "4x mob heads"): melee, before the tool-axe rule
     *       whose "haste" also matches a Haste enchant line (Michael 2026-11-07: "bone ax ... is a weapon");</li>
     *   <li>multitools ("Automatically changes into the best tool for the job"): tools, checked before the melee
     *       rules because ManaCube swaps their base item to a sword or axe mid-use (Michael 2026-11-05);</li>
     *   <li>swords, spears, maces, tridents, unarmed weapons: melee; bows, crossbows: ranged;</li>
     *   <li>pickaxes, shovels, rods, shears, and axes/hoes/wands whose lore names tool work (woodcutting, herbalism,
     *       harvesting, vein mining, selling, growing crops): tools;</li>
     *   <li>hoes and wands that teleport, glide, shield or pull, and mounts: utility;</li>
     *   <li>hoes, blaze rods, sticks and books that shoot or deal damage, and axes that shoot: ranged;</li>
     *   <li>any other axe with lore (on ManaCube nearly all are combat items): melee;</li>
     *   <li>reusable food and drinks (Infinite Uses, (Consume)): consumables.</li>
     * </ul>
     */
    public static final java.util.List<String> DEFAULT_ITEM_GROUPS = java.util.List.of(
            "none: id:_(helmet|chestplate|leggings|boots)$ | minecraft:carved_pumpkin | minecraft:player_head | minecraft:shield | minecraft:elytra | minecraft:totem_of_undying | lore:(?i)pendant slot|\\(off hand\\)|one time use | name:(?i)\\btalisman\\b | lore:(?i)^automatically sells",
            "tools: lore:(?i)best tool for the job|works on every block | name:(?i)multi-?tool",
            "melee: id:_sword$ | id:_spear$ | minecraft:mace | minecraft:trident | lore:(?i)unarmed weapon",
            "ranged: minecraft:bow | minecraft:crossbow",
            "tools: id:_(pickaxe|shovel)$ | minecraft:fishing_rod | minecraft:shears",
            "melee: id:_axe$ & lore:(?i)mob heads?|decapitation",
            "tools: id:_(axe|hoe)$ & lore:(?i)woodcutting|herbalism|excavation|mining|\\bcrops?\\b|harvests|replant|\\btill|vein mine|trench|tunnel|break block|\\blogs?\\b|sell mode|growth|builder|places? .*block",
            "tools: id:blaze_rod$|:stick$ & lore:(?i)\\bsells?\\b|sell wand|\\bcrops?\\b|\\bgrow",
            "utility: id:_hoe$|blaze_rod$|:stick$ & lore:(?i)teleport|glide|slow ?fall|shield|barrier|pull all | minecraft:carrot_on_a_stick",
            "ranged: id:_hoe$|blaze_rod$|:stick$|:book$|:snowball$ & lore:(?i)damage|shoot|\\bfires?\\b|magic|blast|launch|throw|bounce|drown|levitation|portal|randomi",
            "ranged: id:_axe$ & lore:(?i)\\bshoots?\\b|to shoot",
            "melee: id:_axe$ & lore:\\S",
            "utility: id:_hoe$|blaze_rod$|:stick$ & lore:\\S",
            "tools: @tool | @weapon",
            "consumables: name:(?i)^candy apple$ | name:(?i)^bottle of nectar$ | name:(?i)^champion potion$ | id:golden_apple$|honey_bottle$|:potion$|:cookie$ & lore:(?i)infinite uses|^unbreakable$|\\(consume\\)|when consumed|when eaten");

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
