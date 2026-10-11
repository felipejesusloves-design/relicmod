package com.example.relicmod;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Todas as relíquias ficam aqui. */
public class Relics {

    private static void effect(ServerPlayerEntity p, RegistryEntry<StatusEffect> e, int ticks, int amp) {
        p.addStatusEffect(new StatusEffectInstance(e, ticks, amp, true, false));
    }

    // ---------- ÍGNEA (habilidades antigas, ainda sem ultimate) ----------
    public static class Igneous extends RelicItem {
        public Igneous(Settings s) { super(s, 20 * 15, 0); }

        @Override public void applyPassive(ServerPlayerEntity p, int energy) {
            effect(p, StatusEffects.FIRE_RESISTANCE, 60, 0);
        }

        @Override protected boolean useStandard(ServerPlayerEntity p, ServerWorld w, int energy) {
            Box box = p.getBoundingBox().expand(6);
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, e -> e != p)) {
                e.setOnFireFor(6);
            }
            w.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 120, 3, 0.5, 3, 0.05);
            return true;
        }
    }

    // ---------- GLACIAL (habilidades antigas, ainda sem ultimate) ----------
    public static class Glacial extends RelicItem {
        public Glacial(Settings s) { super(s, 20 * 20, 0); }

        @Override public void applyPassive(ServerPlayerEntity p, int energy) {
            ServerWorld w = p.getEntityWorld();
            for (HostileEntity m : w.getEntitiesByClass(HostileEntity.class, p.getBoundingBox().expand(6), e -> true)) {
                m.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0));
            }
            p.setFrozenTicks(0);
        }

        @Override protected boolean useStandard(ServerPlayerEntity p, ServerWorld w, int energy) {
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(7), e -> e != p)) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 6));
                e.setFrozenTicks(200);
            }
            w.spawnParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 150, 3.5, 1, 3.5, 0.02);
            return true;
        }
    }

    // ---------- SOMBRA ----------
    public static class Shadow extends RelicItem {
        public Shadow(Settings s) { super(s, 20 * 5, 20 * 90); }

        /** Velocidade I (-5 a +2), II (+3 e +4), III (+5). */
        public static int speedAmp(int energy) { return energy >= 5 ? 2 : (energy >= 3 ? 1 : 0); }

        /** Alcance do dash: 2, 4 ou 6 blocos. */
        public static double dashRange(int energy) { return energy >= 5 ? 6 : (energy >= 3 ? 4 : 2); }

        @Override public boolean hasUltimate() { return true; }

        @Override public void applyPassive(ServerPlayerEntity p, int energy) {
            effect(p, StatusEffects.SPEED, 60, speedAmp(energy));
        }

        @Override protected boolean useStandard(ServerPlayerEntity p, ServerWorld w, int energy) {
            Vec3d look = p.getRotationVec(1.0f);
            // no chão, dash olhando levemente para baixo continua reto
            if (p.isOnGround() && look.y < 0) look = new Vec3d(look.x, 0, look.z);
            if (look.lengthSquared() < 1.0E-4) {
                p.sendMessage(Text.literal("Sem espaço para o dash."), true);
                return false;
            }
            look = look.normalize();

            double max = dashRange(energy);
            double best = 0;
            for (double d = 0.25; d <= max + 1.0E-6; d += 0.25) {
                if (!isClear(w, p.getX() + look.x * d, p.getY() + look.y * d, p.getZ() + look.z * d)) break;
                best = d;
            }
            if (best < 0.5) {
                p.sendMessage(Text.literal("Sem espaço para o dash."), true);
                return false;
            }

            w.spawnParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.6, 0.3, 0.2);
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.4f);

            double tx = p.getX() + look.x * best;
            double ty = p.getY() + look.y * best;
            double tz = p.getZ() + look.z * best;
            p.requestTeleport(tx, ty, tz);

            // troca o movimento atual pelo impulso do dash
            p.setVelocity(look.multiply(0.5));
            p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
            // dash para cima anula a queda
            if (look.y > 0.3) p.fallDistance = 0;

            w.spawnParticles(ParticleTypes.PORTAL, tx, ty + 1, tz, 30, 0.3, 0.6, 0.3, 0.2);
            return true;
        }

        @Override protected boolean useUltimate(ServerPlayerEntity p, ServerWorld w, int energy) {
            return ShadowStun.arm(p, w);
        }

        /** O jogador cabe nessa posição? Confere pés, meio e cabeça. */
        private static boolean isClear(ServerWorld w, double x, double y, double z) {
            double[] heights = {0.05, 0.9, 1.7};
            for (double h : heights) {
                BlockPos pos = BlockPos.ofFloored(x, y + h, z);
                if (!w.getBlockState(pos).getCollisionShape(w, pos).isEmpty()) return false;
            }
            return true;
        }
    }

    // ---------- TEMPESTADE (habilidades antigas, ainda sem ultimate) ----------
    public static class Storm extends RelicItem {
        public Storm(Settings s) { super(s, 20 * 12, 0); }

        @Override public void applyPassive(ServerPlayerEntity p, int energy) {
            if (p.getEntityWorld().isRaining()) effect(p, StatusEffects.SPEED, 60, 1);
        }

        @Override protected boolean useStandard(ServerPlayerEntity p, ServerWorld w, int energy) {
            HitResult hit = p.raycast(30, 1.0f, false);
            Vec3d pos = hit.getPos();
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w, SpawnReason.TRIGGERED);
            if (bolt == null) return false;
            bolt.setPosition(pos);
            bolt.setChanneler(p);
            w.spawnEntity(bolt);
            return true;
        }
    }
}
