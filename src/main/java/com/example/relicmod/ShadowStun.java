package com.example.relicmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Ultimate da Sombra: ao ativar, o próximo golpe do usuário (jogador ou mob) paralisa o alvo
 * por 3 s (sem andar, sem pular, sem enxergar) e aplica Amaldiçoado por 20 s.
 */
public class ShadowStun {
    public static final int ARM_TICKS = 20 * 15;    // tempo para acertar o golpe depois de ativar
    public static final int STUN_TICKS = 20 * 3;    // paralisia
    public static final int CURSE_TICKS = 20 * 20;  // Amaldiçoado

    private static final Identifier STUN_ID = Identifier.of(RelicMod.MOD_ID, "shadow_stun");

    private static class Stun {
        int ticksLeft;
        final boolean hadNoAi;
        Stun(int ticksLeft, boolean hadNoAi) { this.ticksLeft = ticksLeft; this.hadNoAi = hadNoAi; }
    }

    /** jogador -> tick em que a sombra se dissipa */
    private static final Map<UUID, Integer> ARMED = new HashMap<>();
    /** alvo -> paralisia em andamento */
    private static final Map<UUID, Stun> STUNNED = new HashMap<>();

    /** Ativa a ultimate: o próximo golpe será o golpe sombrio. */
    public static boolean arm(ServerPlayerEntity p, ServerWorld w) {
        if (ARMED.containsKey(p.getUuid())) {
            p.sendMessage(Text.literal("Sua sombra já está armada."), true);
            return false;
        }
        ARMED.put(p.getUuid(), w.getServer().getTicks() + ARM_TICKS);
        w.spawnParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.8f, 1.0f);
        p.sendMessage(Text.literal("Sombra armada: seu próximo golpe paralisa e amaldiçoa (15 s)."), true);
        return true;
    }

    /** Ligado ao evento de ataque do Fabric. Não cancela o golpe, só reage a ele. */
    public static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hit) {
        if (!(player instanceof ServerPlayerEntity attacker)) return ActionResult.PASS;
        if (!ARMED.containsKey(attacker.getUuid())) return ActionResult.PASS;
        if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;
        if (!target.isAlive() || target.isSpectator()) return ActionResult.PASS;

        ARMED.remove(attacker.getUuid());
        stun(attacker, target);
        return ActionResult.PASS;
    }

    /** Paraliza: não anda e não pula (mobs ficam sem IA). Opcionalmente cega. Usado também pela Tempestade. */
    public static void paralyze(LivingEntity target, int ticks, boolean blind) {
        Stun existing = STUNNED.get(target.getUuid());
        if (existing != null) {
            existing.ticksLeft = Math.max(existing.ticksLeft, ticks);
        } else {
            boolean noAi = target instanceof MobEntity m && m.isAiDisabled();
            STUNNED.put(target.getUuid(), new Stun(ticks, noAi));
            if (target instanceof MobEntity m) m.setAiDisabled(true);
            addMod(target, EntityAttributes.MOVEMENT_SPEED);
            addMod(target, EntityAttributes.JUMP_STRENGTH);
        }
        if (blind) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, ticks, 0, false, false));
        }
    }

    private static void stun(ServerPlayerEntity attacker, LivingEntity target) {
        paralyze(target, STUN_TICKS, true);
        // maldição: -3 corações de vida máxima por 20 s
        target.addStatusEffect(new StatusEffectInstance(ModEffects.CURSED, CURSE_TICKS, 0, false, true));
        if (target.getHealth() > target.getMaxHealth()) target.setHealth(target.getMaxHealth());

        if (target.getEntityWorld() instanceof ServerWorld tw) {
            tw.spawnParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1, target.getZ(), 30, 0.4, 0.7, 0.4, 0.05);
            tw.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 1.0f, 0.8f);
        }
        if (target instanceof ServerPlayerEntity tp) {
            tp.sendMessage(Text.literal("Você foi paralisado e amaldiçoado!"), true);
        }
        attacker.sendMessage(Text.literal("Golpe sombrio acertou!"), true);
    }

    /** Todo tick do servidor. */
    public static void tick(MinecraftServer server) {
        int now = server.getTicks();

        if (!ARMED.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = ARMED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
                if (p == null) { it.remove(); continue; }
                if (now >= entry.getValue()) {
                    it.remove();
                    p.sendMessage(Text.literal("A sombra se dissipou."), true);
                    continue;
                }
                if (now % 10 == 0 && p.getEntityWorld() instanceof ServerWorld w) {
                    w.spawnParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.6, 0.3, 0.01);
                }
            }
        }

        if (!STUNNED.isEmpty()) {
            Iterator<Map.Entry<UUID, Stun>> it = STUNNED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Stun> entry = it.next();
                Stun s = entry.getValue();
                s.ticksLeft--;
                Entity e = findEntity(server, entry.getKey());
                if (e == null) { it.remove(); continue; }
                if (s.ticksLeft <= 0 || !e.isAlive()) {
                    restore(e, s);
                    it.remove();
                }
            }
        }
    }

    /** Jogador saiu do jogo: cancela a sombra armada. */
    public static void forget(ServerPlayerEntity p) {
        ARMED.remove(p.getUuid());
    }

    /** Entidade descarregada: garante que ela não fique paralisada para sempre. */
    public static void release(Entity e) {
        Stun s = STUNNED.remove(e.getUuid());
        if (s != null) restore(e, s);
    }

    /** Servidor fechando: solta todo mundo antes de salvar o mundo. */
    public static void releaseAll(MinecraftServer server) {
        for (Map.Entry<UUID, Stun> entry : STUNNED.entrySet()) {
            Entity e = findEntity(server, entry.getKey());
            if (e != null) restore(e, entry.getValue());
        }
        STUNNED.clear();
        ARMED.clear();
    }

    private static void restore(Entity e, Stun s) {
        if (e instanceof MobEntity m && !s.hadNoAi) m.setAiDisabled(false);
        if (e instanceof LivingEntity le) {
            removeMod(le, EntityAttributes.MOVEMENT_SPEED);
            removeMod(le, EntityAttributes.JUMP_STRENGTH);
        }
    }

    private static void addMod(LivingEntity e, RegistryEntry<EntityAttribute> attribute) {
        EntityAttributeInstance inst = e.getAttributeInstance(attribute);
        if (inst != null && !inst.hasModifier(STUN_ID)) {
            inst.addTemporaryModifier(new EntityAttributeModifier(STUN_ID, -1.0,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeMod(LivingEntity e, RegistryEntry<EntityAttribute> attribute) {
        EntityAttributeInstance inst = e.getAttributeInstance(attribute);
        if (inst != null) inst.removeModifier(STUN_ID);
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }
}
