package com.example.relicmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Ultimate da Sombra (versão 1).
 * O corpo do usuário vira espectador (intangível e invulnerável) e a câmera passa para o alvo.
 * Alvo recebe os efeitos do usuário. Tudo volta ao normal após 10 segundos.
 */
public class PossessionManager {
    public static final int DURATION = 20 * 10;

    private static class State {
        final UUID target;
        final double x, y, z;
        final GameMode mode;
        int ticksLeft = DURATION;

        State(UUID target, double x, double y, double z, GameMode mode) {
            this.target = target;
            this.x = x; this.y = y; this.z = z;
            this.mode = mode;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    public static boolean isPossessing(ServerPlayerEntity p) {
        return ACTIVE.containsKey(p.getUuid());
    }

    public static boolean start(ServerPlayerEntity p, ServerWorld w, int energy) {
        if (isPossessing(p)) return false;
        LivingEntity target = findTarget(p, w);
        if (target == null) {
            p.sendMessage(Text.literal("Nenhum alvo no seu campo de visão."), true);
            return false;
        }

        State s = new State(target.getUuid(), p.getX(), p.getY(), p.getZ(), p.interactionManager.getGameMode());

        // efeitos do usuário passam para o corpo possuído (duram no máximo 10 s)
        for (StatusEffectInstance inst : p.getStatusEffects()) {
            target.addStatusEffect(new StatusEffectInstance(inst.getEffectType(),
                    Math.min(inst.getDuration(), DURATION), inst.getAmplifier(),
                    inst.isAmbient(), inst.shouldShowParticles()));
        }
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, DURATION,
                Relics.Shadow.speedAmp(energy), true, false));

        if (target instanceof MobEntity mob) mob.setAiDisabled(true);
        if (target instanceof ServerPlayerEntity tp) {
            tp.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, DURATION, 6, true, false));
            tp.sendMessage(Text.literal("Você está sendo possuído!"), true);
        }

        ACTIVE.put(p.getUuid(), s);
        w.spawnParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.8f, 1.0f);

        p.changeGameMode(GameMode.SPECTATOR);
        p.setCameraEntity(target);
        p.sendMessage(Text.literal("Possessão iniciada: 10 segundos."), true);
        return true;
    }

    /** Chamado todo tick do servidor. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, State>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> entry = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
            State s = entry.getValue();
            if (p == null) { it.remove(); continue; }

            Entity target = findEntity(server, s.target);
            s.ticksLeft--;
            if (target == null || !target.isAlive() || s.ticksLeft <= 0) {
                finish(p, s, target);
                it.remove();
                continue;
            }
            if (p.getCameraEntity() != target) p.setCameraEntity(target);
        }
    }

    /** Jogador saiu do jogo no meio da possessão. */
    public static void abort(ServerPlayerEntity p) {
        State s = ACTIVE.remove(p.getUuid());
        if (s != null) {
            finish(p, s, findEntity(p.getEntityWorld().getServer(), s.target));
        }
    }

    private static void finish(ServerPlayerEntity p, State s, Entity target) {
        if (target instanceof MobEntity mob) mob.setAiDisabled(false);
        if (target instanceof ServerPlayerEntity tp) tp.removeStatusEffect(StatusEffects.SLOWNESS);
        p.setCameraEntity(p);
        p.changeGameMode(s.mode);
        p.requestTeleport(s.x, s.y, s.z);
        p.sendMessage(Text.literal("A possessão terminou."), true);
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    /** Alvo mais centralizado no campo de visão, até 30 blocos, sem obstáculo no meio. */
    private static LivingEntity findTarget(ServerPlayerEntity p, ServerWorld w) {
        double range = 30;
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f);
        LivingEntity best = null;
        double bestDot = 0.97;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(range),
                e -> e != p && e.isAlive() && !e.isSpectator())) {
            Vec3d to = new Vec3d(e.getX(), e.getEyeY(), e.getZ()).subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.5) continue;
            double dot = to.normalize().dotProduct(look);
            if (dot > bestDot && p.canSee(e)) {
                best = e;
                bestDot = dot;
            }
        }
        return best;
    }
}
