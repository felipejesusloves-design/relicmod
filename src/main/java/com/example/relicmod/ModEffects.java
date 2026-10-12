package com.example.relicmod;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public class ModEffects {
    /** Amaldiçoado: -6 de vida máxima (3 corações). */
    public static final RegistryEntry<StatusEffect> CURSED = Registry.registerReference(
            Registries.STATUS_EFFECT,
            Identifier.of(RelicMod.MOD_ID, "cursed"),
            new CursedEffect().addAttributeModifier(
                    EntityAttributes.MAX_HEALTH,
                    Identifier.of(RelicMod.MOD_ID, "cursed_health"),
                    -6.0,
                    EntityAttributeModifier.Operation.ADD_VALUE));

    /** Congelamento: -20% de velocidade por nível (níveis 1 a 4). */
    public static final RegistryEntry<StatusEffect> FROZEN = Registry.registerReference(
            Registries.STATUS_EFFECT,
            Identifier.of(RelicMod.MOD_ID, "frozen"),
            new FrozenEffect().addAttributeModifier(
                    EntityAttributes.MOVEMENT_SPEED,
                    Identifier.of(RelicMod.MOD_ID, "frozen_slow"),
                    -0.2,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static void init() {}
}
