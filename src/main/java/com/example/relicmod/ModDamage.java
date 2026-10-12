package com.example.relicmod;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/**
 * Tipos de dano da Ígnea (definidos em data/relicmod/damage_type e nas tags em data/minecraft/tags).
 * Ignoram armadura, escudo, Resistência, encantamentos e o tempo de invulnerabilidade pós-golpe.
 */
public class ModDamage {
    public static final RegistryKey<DamageType> FIREBALL =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(RelicMod.MOD_ID, "fireball"));
    public static final RegistryKey<DamageType> VOLCANO =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(RelicMod.MOD_ID, "volcano"));

    public static final RegistryKey<DamageType> STORM =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(RelicMod.MOD_ID, "storm"));
    public static final RegistryKey<DamageType> STORM_BEAM =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(RelicMod.MOD_ID, "storm_beam"));

    public static DamageSource of(ServerWorld world, RegistryKey<DamageType> key, ServerPlayerEntity owner) {
        RegistryEntry<DamageType> type = world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(key);
        return new DamageSource(type, owner, owner);
    }

    public static void hit(ServerWorld world, LivingEntity target, RegistryKey<DamageType> key,
                           ServerPlayerEntity owner, float amount) {
        target.damage(world, of(world, key, owner), amount);
    }
}
