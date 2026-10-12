package com.example.relicmod;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/** Efeito "Congelamento": lentidão que cresce com o nível (a redução é definida em ModEffects). */
public class FrozenEffect extends StatusEffect {
    public FrozenEffect() {
        super(StatusEffectCategory.HARMFUL, 0x8FD8FF);
    }
}
