package com.example.relicmod;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Todas as relíquias ficam aqui. Para criar uma nova: nova classe + registro em ModItems. */
public class Relics {

    private static void effect(ServerPlayerEntity p, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> e, int ticks, int amp) {
        p.addStatusEffect(new StatusEffectInstance(e, ticks, amp, true, false));
    }

    // ---------- ÍGNEA ----------
    public static class Igneous extends RelicItem {
        public Igneous(Settings s) { super(s, 20 * 15); }

        @Override public void applyPassive(ServerPlayerEntity p) {
            effect(p, StatusEffects.FIRE_RESISTANCE, 60, 0);
        }

        @Override protected void useAbility(ServerPlayerEntity p, ServerWorld w) {
            Box box = p.getBoundingBox().expand(6);
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, e -> e != p)) {
                e.setOnFireFor(6);
            }
            w.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 120, 3, 0.5, 3, 0.05);
        }
    }

    // ---------- GLACIAL ----------
    public static class Glacial extends RelicItem {
        public Glacial(Settings s) { super(s, 20 * 20); }

        @Override public void applyPassive(ServerPlayerEntity p) {
            // Aura: hostis próximos ficam lentos
            ServerWorld w = p.getEntityWorld();
            for (HostileEntity m : w.getEntitiesByClass(HostileEntity.class, p.getBoundingBox().expand(6), e -> true)) {
                m.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0));
            }
            p.setFrozenTicks(0); // imune a neve em pó
        }

        @Override protected void useAbility(ServerPlayerEntity p, ServerWorld w) {
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(7), e -> e != p)) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 6));
                e.setFrozenTicks(200);
            }
            w.spawnParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 150, 3.5, 1, 3.5, 0.02);
        }
    }

    // ---------- SOMBRIA ----------
    public static class Shadow extends RelicItem {
        public Shadow(Settings s) { super(s, 20 * 8); }

        @Override public void applyPassive(ServerPlayerEntity p) {
            effect(p, StatusEffects.NIGHT_VISION, 260, 0);
            if (p.getEntityWorld().getLightLevel(p.getBlockPos()) < 8) {
                effect(p, StatusEffects.SPEED, 60, 1);
            }
        }

        @Override protected void useAbility(ServerPlayerEntity p, ServerWorld w) {
            Vec3d start = p.getEyePos();
            Vec3d end = start.add(p.getRotationVec(1.0f).multiply(8));
            HitResult hit = p.raycast(8, 1.0f, false);
            Vec3d target = hit.getType() == HitResult.Type.MISS ? end : hit.getPos().subtract(p.getRotationVec(1.0f).multiply(0.6));
            w.spawnParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.3, 0.5, 0.3, 0.3);
            p.requestTeleport(target.x, target.y - p.getStandingEyeHeight(), target.z);
        }
    }

    // ---------- TEMPESTADE ----------
    public static class Storm extends RelicItem {
        public Storm(Settings s) { super(s, 20 * 12); }

        @Override public void applyPassive(ServerPlayerEntity p) {
            if (p.getEntityWorld().isRaining()) effect(p, StatusEffects.SPEED, 60, 1);
        }

        @Override protected void useAbility(ServerPlayerEntity p, ServerWorld w) {
            HitResult hit = p.raycast(30, 1.0f, false);
            Vec3d pos = hit.getPos();
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w, SpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setPosition(pos);
                bolt.setChanneler(p);
                w.spawnEntity(bolt);
            }
        }
    }
}
