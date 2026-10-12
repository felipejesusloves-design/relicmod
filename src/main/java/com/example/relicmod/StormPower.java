package com.example.relicmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Poderes da Tempestade.
 *  Passiva: 3 criticos seguidos -> raios em cada golpe.
 *  Padrao: raio vertical onde voce mira.
 *  Ultimate: canaliza 2 s e solta um RELAMPAGO HORIZONTAL serrilhado
 *            (onda sonica do Warden fundida com arco eletrico).
 */
public class StormPower {
    public static final int COMBO_TIMEOUT = 20 * 6;
    public static final int CHANNEL_TICKS = 20 * 2;
    public static final double BEAM_RANGE = 30;

    public static final int CRITS_NEEDED = 3;
    private static final float PASSIVE_DAMAGE = 2.0f;
    private static final float STANDARD_DAMAGE = 6.0f;
    private static final float BEAM_DAMAGE = 33.0f;

    private static class Combo {
        int crits;
        boolean active;
        int lastHit;
    }

    private static class Channel {
        int ticksLeft = CHANNEL_TICKS;
    }

    private static final Map<UUID, Combo> COMBOS = new HashMap<>();
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

    // ------------------------------------------------------------------ PASSIVA

    public static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hit) {
        if (!(player instanceof ServerPlayerEntity p)) return ActionResult.PASS;
        if (!(RelicData.getRelicItem(p) instanceof Relics.Storm)) return ActionResult.PASS;
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isSpectator()) return ActionResult.PASS;
        if (p.getAttackCooldownProgress(0.5f) <= 0.9f) return ActionResult.PASS;

        ServerWorld w = p.getEntityWorld();
        Combo c = COMBOS.computeIfAbsent(p.getUuid(), k -> new Combo());
        c.lastHit = w.getServer().getTicks();

        if (c.active) {
            bolt(w, target.getX(), target.getY(), target.getZ());
            VFX.spawnLightningArc(w, p, target);
            ModDamage.hit(w, target, ModDamage.STORM, p, PASSIVE_DAMAGE);
        } else if (isCritical(p)) {
            c.crits++;
            if (c.crits >= CRITS_NEEDED) {
                c.active = true;
                c.crits = 0;
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 40, 0.8, 1.0, 0.8, 0.4);
                w.spawnParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.6f, 1.5f);
                p.sendMessage(Text.literal("Tempestade carregada: seus golpes invocam raios!"), true);
            }
        } else {
            c.crits = 0;
        }
        return ActionResult.PASS;
    }

    private static boolean isCritical(ServerPlayerEntity p) {
        return p.fallDistance > 0 && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && !p.isSprinting();
    }

    public static void onDamaged(ServerPlayerEntity p) {
        Combo c = COMBOS.get(p.getUuid());
        if (c == null) return;
        boolean wasActive = c.active;
        c.active = false;
        c.crits = 0;
        if (wasActive) p.sendMessage(Text.literal("A tempestade se desfez."), true);
    }

    public static void forget(ServerPlayerEntity p) {
        COMBOS.remove(p.getUuid());
        CHANNELS.remove(p.getUuid());
    }

    // ------------------------------------------------------------------ PADRAO

    public static boolean standard(ServerPlayerEntity p, ServerWorld w, int energy) {
        Vec3d pos = aimPoint(p, w, 40);
        bolt(w, pos.x, pos.y, pos.z);

        w.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);
        for (int i = 0; i < 16; i++) {
            double angle = i * (Math.PI * 2 / 16);
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.x + Math.cos(angle) * 2.0, pos.y + 0.5, pos.z + Math.sin(angle) * 2.0,
                    3, 0.1, 0.3, 0.1, 0.1);
        }

        int amp = energy >= 4 ? 1 : 0;
        Box area = new Box(pos.x - 3, pos.y - 3, pos.z - 3, pos.x + 3, pos.y + 3, pos.z + 3);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, area, ent -> ent.isAlive() && !ent.isSpectator())) {
            if (!e.getBoundingBox().expand(2.0, 1.0, 2.0).contains(pos)) continue;
            if (e == p) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, amp, false, true));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, amp, false, true));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, amp, false, true));
            } else {
                ModDamage.hit(w, e, ModDamage.STORM, p, STANDARD_DAMAGE);
                VFX.spawnLightningArc(w, p, e);
                ShadowStun.paralyze(e, 20, false);
            }
        }
        return true;
    }

    private static Vec3d aimPoint(ServerPlayerEntity p, ServerWorld w, double range) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f);
        LivingEntity best = null;
        double bestDot = 0.985;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(range),
                ent -> ent != p && ent.isAlive() && !ent.isSpectator())) {
            Vec3d to = new Vec3d(e.getX(), e.getEyeY(), e.getZ()).subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.5) continue;
            double dot = to.normalize().dotProduct(look);
            if (dot > bestDot) {
                BlockHitResult bHit = w.raycast(new RaycastContext(eye, e.getEyePos(),
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
                if (bHit.getType() == HitResult.Type.MISS) {
                    best = e;
                    bestDot = dot;
                }
            }
        }
        if (best != null) return new Vec3d(best.getX(), best.getY(), best.getZ());
        return p.raycast(range, 1.0f, false).getPos();
    }

    // ------------------------------------------------------------------ ULTIMATE

    public static boolean channel(ServerPlayerEntity p, ServerWorld w) {
        if (CHANNELS.containsKey(p.getUuid())) return false;
        CHANNELS.put(p.getUuid(), new Channel());
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.PLAYERS, 1.5f, 1.0f);
        p.sendMessage(Text.literal("Canalizando a tempestade... 2 segundos."), true);
        return true;
    }

    public static void tick(MinecraftServer server) {
        int now = server.getTicks();

        for (Map.Entry<UUID, Combo> entry : COMBOS.entrySet()) {
            Combo c = entry.getValue();
            if ((c.active || c.crits > 0) && now - c.lastHit > COMBO_TIMEOUT) {
                if (c.active) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
                    if (p != null) p.sendMessage(Text.literal("A tempestade se dissipou."), true);
                }
                c.active = false;
                c.crits = 0;
            }
        }

        if (CHANNELS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Channel>> it = CHANNELS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Channel> entry = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
            Channel ch = entry.getValue();
            if (p == null || !p.isAlive() || p.isSpectator()) { it.remove(); continue; }

            ch.ticksLeft--;
            if (ch.ticksLeft <= 0) {
                it.remove();
                fireBeam(p, p.getEntityWorld());
                continue;
            }
            if (ch.ticksLeft % 5 == 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 2, true, false));
            }
            channelParticles(p, p.getEntityWorld(), ch);
        }
    }

    private static void channelParticles(ServerPlayerEntity p, ServerWorld w, Channel ch) {
        double angle = ch.ticksLeft * 0.6;
        double radius = 1.3;
        double height = 0.2 + (CHANNEL_TICKS - ch.ticksLeft) / (double) CHANNEL_TICKS * 1.4;
        for (int k = 0; k < 2; k++) {
            double a = angle + k * Math.PI;
            w.spawnParticles(ParticleTypes.SCULK_SOUL,
                    p.getX() + Math.cos(a) * radius, p.getY() + height, p.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0.0);
        }
        if (ch.ticksLeft % 2 == 0) {
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.6, 0.8, 0.6, 0.3);
        }
    }

    /**
     * RELAMPAGO HORIZONTAL: 3 fios serrilhados de arco eletrico + ondas sonicas
     * atravessam a linha ate o primeiro bloco. Sem raio vertical no final.
     */
    private static void fireBeam(ServerPlayerEntity p, ServerWorld w) {
        Vec3d look = p.getRotationVec(1.0f).normalize();
        Vec3d start = p.getEyePos().add(look.multiply(0.5)).subtract(0, 0.2, 0);

        double len = BEAM_RANGE;
        for (double d = 0.5; d <= BEAM_RANGE; d += 0.5) {
            Vec3d pt = start.add(look.multiply(d));
            BlockPos bp = BlockPos.ofFloored(pt.x, pt.y, pt.z);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) {
                len = Math.max(0, d - 0.5);
                break;
            }
        }
        Vec3d end = start.add(look.multiply(len));

        // direcoes perpendiculares para serrilhar o raio
        Vec3d side = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
        if (side.lengthSquared() < 0.01) side = new Vec3d(1, 0, 0);
        Vec3d upv = side.crossProduct(look).normalize();

        // 3 fios de relampago horizontal serrilhado
        for (int strand = 0; strand < 3; strand++) {
            for (double d = 0; d <= len; d += 0.5) {
                double j1 = (w.random.nextDouble() - 0.5) * 0.8;
                double j2 = (w.random.nextDouble() - 0.5) * 0.8;
                Vec3d pt = start.add(look.multiply(d)).add(side.multiply(j1)).add(upv.multiply(j2));
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pt.x, pt.y, pt.z, 2, 0, 0, 0, 0);
                if ((int) d % 2 == 0) {
                    w.spawnParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                }
            }
        }

        // impacto no fim: anel eletrico + explosao (SEM raio vertical)
        w.spawnParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 4, 0.4, 0.4, 0.4, 0.1);
        w.spawnParticles(ParticleTypes.FIREWORK, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        for (int i = 0; i < 20; i++) {
            double a = i * (Math.PI * 2 / 20);
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    end.x + Math.cos(a) * 1.5, end.y, end.z + Math.sin(a) * 1.5,
                    2, 0.1, 0.2, 0.1, 0.15);
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.0f, 1.0f);
        w.playSound(null, end.x, end.y, end.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.5f, 1.2f);

        // dano: atravessa todos os seres na linha
        Box sweep = new Box(start, end).expand(3.0);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, sweep,
                ent -> ent != p && ent.isAlive() && !ent.isSpectator())) {
            Box bb = e.getBoundingBox().expand(1.0);
            if (bb.contains(start) || bb.raycast(start, end).isPresent()) {
                ModDamage.hit(w, e, ModDamage.STORM_BEAM, p, BEAM_DAMAGE);
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static void bolt(ServerWorld w, double x, double y, double z) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w, SpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.setPosition(x, y, z);
        bolt.setCosmetic(true);
        w.spawnEntity(bolt);
    }
}
