package com.example.relicmod;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/** Efeito "Amaldiçoado": a vida máxima do alvo cai 3 corações (a redução é definida em ModEffects). */
public class CursedEffect extends StatusEffect {
    public CursedEffect() {
        super(StatusEffectCategory.HARMFUL, 0x3A1A5E);
    }
}
