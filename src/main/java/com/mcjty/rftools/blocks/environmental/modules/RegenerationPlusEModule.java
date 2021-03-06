package com.mcjty.rftools.blocks.environmental.modules;

import net.minecraft.potion.Potion;

public class RegenerationPlusEModule extends PotionEffectModule {
    public static final float RFPERTICK = 0.02f;

    public RegenerationPlusEModule() {
        super(Potion.regeneration.getId(), 2);
    }

    @Override
    public float getRfPerTick() {
        return RFPERTICK;
    }
}
