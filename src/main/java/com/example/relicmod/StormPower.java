package com.example.relicmod;

import net.minecraft.block.BlockState;
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
 *  Passiva: 2 golpes criticos seguidos (golpes com a barra cheia, sem levar dano) liberam raios em cada golpe.
 *  Padrao: raio onde voce mira (dano + paralisia, ou buffs se cair em voce).
 *  Ultimate: canaliza 2 s e solta um feixe horizontal (estrondo do Warden + raio de tempestade).
 * Todos os raios sao puramente visuais/sonoros sem gerar fogo no chao.
 */
public class StormPower {
    public static final int COMBO_TIMEOUT = 20 * 6;     // sem acertar ninguem por 6 s: o estado acaba
    public static final int CHANNEL_TICKS = 20 * 2;     // canalizacao da ultimate
    public static final double BEAM_RANGE = 30;         // alcance do feixe

    private static final float PASSIVE_DAMAGE = 4.0f;   // 2 coracoes
    private static final float STANDARD_DAMAGE = 6.0f;  // 3 coracoes
    private static final float BEAM_DAMAGE = 20.0f;     // 10 coracoes

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

    /** Ligado ao evento de ataque do Fabric. So reage ao golpe, nao o cancela. */
    public static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hit) {
        if (!(player instanceof ServerPlayerEntity p)) return ActionResult.PASS;
        if (!(RelicData.getRelicItem(p) instanceof Relics.Storm)) return ActionResult.PASS;
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isSpectator()) return ActionResult.PASS;
        if (p.getAttackCooldownProgress(0.5f) <= 0.9f) return ActionResult.PASS; // golpe fraco nao conta

        ServerWorld w = p.getEntityWorld();
        Combo c = COMBOS.computeIfAbsent(p.getUuid(), k -> new Combo());
        c.lastHit = w.getServer().getTicks();

        if (c.active) {
            bolt(w, target.getX(), target.getY(), target.getZ());
            ModDamage.hit(w, target, ModDamage.STORM, p, PASSIVE_DAMAGE);
        } else if (isCritical(p)) {
            c.crits++;
            if (c.crits >= 2) {
                c.active = true;
                c.crits = 0;
                p.sendMessage(Text.literal("Tempestade carregada: seus golpes invocam raios!"), true);
            }
        } else {
            c.crits = 0; // golpe cheio sem ser critico quebra o combo
        }
        return ActionResult.PASS;
    }

    /** Mesmas regras do critico do jogo (golpe caindo, sem correr). */
    private static boolean isCritical(ServerPlayerEntity p) {
        return p.fallDistance > 0 && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && !p.isSprinting();
    }

    /** O usuario levou dano: o combo e o estado acabam. */
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

    /** Raio onde voce mira. Dano e paralisia nos outros; buffs se cair em voce. */
    public static boolean standard(ServerPlayerEntity p, ServerWorld w, int energy) {
        Vec3d pos = aimPoint(p, w, 40);
        bolt(w, pos.x, pos.y, pos.z);

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
                ShadowStun.paralyze(e, 20, false);
            }
        }
        return true;
    }

    /** Ser na sua mira (ate o alcance); se nao houver, o ponto do bloco/chao. */
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
                // Checa se ha bloco bloqueando a visao
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

    /** Comeca a canalizar. O feixe sai quando os 2 segundos acabam. */
    public static boolean channel(ServerPlayerEntity p, ServerWorld w) {
        if (CHANNELS.containsKey(p.getUuid())) return false;
        CHANNELS.put(p.getUuid(), new Channel());
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.PLAYERS, 1.5f, 1.0f);
        p.sendMessage(Text.literal("Canalizando a tempestade... 2 segundos."), true);
        return true;
    }

    /** Todo tick do servidor. */
    public static void tick(MinecraftServer server) {
        int now = server.getTicks();

        // estado da passiva expira se ficar 6 s sem acertar ninguem
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

    /** Almas de sculk girando em volta de voce + faiscas eletricas, cada vez mais intensas. */
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

    /** Feixe horizontal: atravessa todos os seres da linha e para no primeiro bloco. */
    private static void fireBeam(ServerPlayerEntity p, ServerWorld w) {
        Vec3d look = p.getRotationVec(1.0f).normalize();
        Vec3d start = p.getEyePos().add(look.multiply(0.5)).subtract(0, 0.2, 0);

        // ate onde o feixe vai (para no primeiro bloco)
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

        // visual: ondas de estrondo + faiscas eletricas ao longo do feixe
        for (double d = 0; d <= len; d += 0.5) {
            Vec3d pt = start.add(look.multiply(d));
            if (d % 1.5 == 0) {
                w.spawnParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            }
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pt.x, pt.y, pt.z, 4, 0.25, 0.25, 0.25, 0.2);
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.0f, 1.0f);
        bolt(w, end.x, end.y, end.z); // raio de tempestade no fim do feixe

        // dano: 10 coracoes, ignora so o efeito dos encantamentos de protecao
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

    /** Raio que nao gera fogo: feito via particulas de flash/eletricidade e sons estrondosos de raio. */
    private static void bolt(ServerWorld w, double x, double y, double z) {
        // Particulas volumosas de raio vertical
        for (double dy = 0; dy <= 20; dy += 1.0) {
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + dy, z, 5, 0.3, 0.5, 0.3, 0.15);
        }
        w.spawnParticles(ParticleTypes.FLASH, x, y + 1.0, z, 2, 0.5, 0.5, 0.5, 0.0);
        w.playSound(null, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 2.0f, 1.0f);
        w.playSound(null, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.5f, 1.2f);
    }
}