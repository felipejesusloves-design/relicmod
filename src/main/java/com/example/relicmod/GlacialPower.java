package com.example.relicmod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Poderes da Glacial.
 *  Passiva: a cada 2 críticos o alvo ganha +1 nível de Congelamento (1 a 4). Sem novas aplicações em 2,5 s,
 *           o nível cai 1. Dano por segundo: 0,5 coração x nível.
 *  Padrão:  congela (cubo de gelo visual por 2 s) todos os seres com Congelamento ao alcance.
 *           Dano: 1,5 coração x nível, ignorando só encantamentos. Dano de um jogador descongela.
 *  Ultimate: nevasca que acompanha o usuário por 15 s. Quem está dentro ganha +1 nível por segundo
 *           e só enxerga névoa branca.
 */
public class GlacialPower {
    public static final int MAX_LEVEL = 4;
    public static final int CRITS_PER_LEVEL = 2;
    public static final int DECAY_TICKS = 50;               // 2,5 s
    public static final double FREEZE_RANGE = 15;
    public static final int ICE_TICKS = 40;                 // 2 s
    public static final int BLIZZARD_TICKS = 20 * 15;
    public static final double BLIZZARD_RADIUS = 8.5;       // parecido com o vulcão
    public static final double BLIZZARD_HALF_HEIGHT = 6;

    private static class Frost {
        int level;          // 0 = só contando críticos
        int crits;
        int timer = DECAY_TICKS;
        UUID attacker;
    }

    private static class Ice {
        final ServerWorld world;
        int ticksLeft = ICE_TICKS;
        final List<BlockPos> fake = new ArrayList<>();
        Ice(ServerWorld world) { this.world = world; }
    }

    private static class Blizzard {
        int ticksLeft = BLIZZARD_TICKS;
    }

    private static final Map<UUID, Frost> FROST = new HashMap<>();
    private static final Map<UUID, Ice> ICE = new HashMap<>();
    private static final Map<UUID, Blizzard> BLIZZARDS = new HashMap<>();
    private static final Map<UUID, Integer> HUD_STATE = new HashMap<>();

    // ------------------------------------------------------------------ PASSIVA

    /** Ligado ao evento de ataque do Fabric. Só reage ao golpe, não o cancela. */
    public static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hit) {
        if (!(player instanceof ServerPlayerEntity p)) return ActionResult.PASS;
        if (!(RelicData.getRelicItem(p) instanceof Relics.Glacial)) return ActionResult.PASS;
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isSpectator()) return ActionResult.PASS;
        if (p.getAttackCooldownProgress(0.5f) <= 0.9f) return ActionResult.PASS; // golpe fraco não conta
        if (!isCritical(p)) return ActionResult.PASS;

        Frost f = FROST.get(target.getUuid());
        if (f == null) {
            f = new Frost();
            FROST.put(target.getUuid(), f);
        }
        f.attacker = p.getUuid();
        f.crits++;
        if (f.crits >= CRITS_PER_LEVEL) {
            f.crits = 0;
            applyLevel(target, f, Math.min(MAX_LEVEL, f.level + 1));
        }
        return ActionResult.PASS;
    }

    /** Mesmas regras do crítico do jogo (golpe caindo, sem correr). */
    private static boolean isCritical(ServerPlayerEntity p) {
        return p.fallDistance > 0 && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && !p.isSprinting();
    }

    /** Define o nível, reinicia a contagem de 2,5 s e atualiza o efeito. */
    private static void applyLevel(LivingEntity target, Frost f, int newLevel) {
        boolean up = newLevel > f.level;
        f.level = newLevel;
        f.timer = DECAY_TICKS;
        refreshEffect(target, f);
        if (up && target.getEntityWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                    15, 0.4, 0.6, 0.4, 0.05);
            w.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_GLASS_BREAK,
                    SoundCategory.PLAYERS, 0.5f, 1.8f);
        }
    }

    private static void refreshEffect(LivingEntity target, Frost f) {
        target.removeStatusEffect(ModEffects.FROZEN);
        if (f.level > 0) {
            target.addStatusEffect(new StatusEffectInstance(ModEffects.FROZEN, DECAY_TICKS + 30, f.level - 1, false, true, true));
        }
    }

    private static void clear(LivingEntity target) {
        target.removeStatusEffect(ModEffects.FROZEN);
        target.setFrozenTicks(0);
    }

    // ------------------------------------------------------------------ PADRÃO

    /** Seres com Congelamento ao alcance (e ainda não encaixotados em gelo). */
    private static List<LivingEntity> freezeTargets(ServerPlayerEntity p, ServerWorld w) {
        Box area = p.getBoundingBox().expand(FREEZE_RANGE);
        return w.getEntitiesByClass(LivingEntity.class, area, e -> {
            if (e == p || !e.isAlive() || e.isSpectator()) return false;
            if (ICE.containsKey(e.getUuid())) return false;
            Frost f = FROST.get(e.getUuid());
            return f != null && f.level > 0 && e.squaredDistanceTo(p) <= FREEZE_RANGE * FREEZE_RANGE;
        });
    }

    public static boolean standard(ServerPlayerEntity p, ServerWorld w) {
        List<LivingEntity> targets = freezeTargets(p, w);
        if (targets.isEmpty()) {
            p.sendMessage(Text.literal("Nenhum alvo com Congelamento ao alcance."), true);
            return false;
        }
        for (LivingEntity e : targets) freeze(p, w, e);
        return true;
    }

    private static void freeze(ServerPlayerEntity p, ServerWorld w, LivingEntity e) {
        Frost f = FROST.get(e.getUuid());
        if (f == null) return;
        // 1,5 coração x nível (3 de vida por nível), ignorando só encantamentos
        ModDamage.hit(w, e, ModDamage.FROST, p, 3.0f * f.level);
        if (!e.isAlive()) return;

        Ice ice = new Ice(w);
        showIce(w, e, ice);
        ICE.put(e.getUuid(), ice);
        ShadowStun.paralyze(e, ICE_TICKS, false);

        w.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f);
        w.spawnParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getHeight() / 2, e.getZ(), 40, 0.5, 0.7, 0.5, 0.1);
    }

    /** Blocos de gelo "falsos": só os jogadores veem, o mundo de verdade não muda. */
    private static void showIce(ServerWorld w, LivingEntity e, Ice ice) {
        Box b = e.getBoundingBox().expand(0.05);
        int x0 = MathHelper.floor(b.minX), x1 = MathHelper.floor(b.maxX);
        int y0 = MathHelper.floor(b.minY), y1 = MathHelper.floor(b.maxY);
        int z0 = MathHelper.floor(b.minZ), z1 = MathHelper.floor(b.maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (ice.fake.size() >= 48) break;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (w.getBlockState(pos).isAir()) ice.fake.add(pos);
                }
            }
        }
        sendBlocks(w, ice.fake, true);
    }

    private static void sendBlocks(ServerWorld w, List<BlockPos> list, boolean iceBlocks) {
        for (ServerPlayerEntity pl : w.getPlayers()) {
            for (BlockPos pos : list) {
                if (pl.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) > 64 * 64) continue;
                if (iceBlocks) pl.networkHandler.sendPacket(new BlockUpdateS2CPacket(pos, Blocks.ICE.getDefaultState()));
                else pl.networkHandler.sendPacket(new BlockUpdateS2CPacket(w, pos));
            }
        }
    }

    /** Desfaz o gelo: devolve os blocos reais, solta o alvo e quebra com som e partículas. */
    private static void thaw(Entity e, Ice ice) {
        sendBlocks(ice.world, ice.fake, false);
        if (e != null) {
            ShadowStun.release(e);
            ice.world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ICE.getDefaultState()),
                    e.getX(), e.getY() + e.getHeight() / 2, e.getZ(), 40, 0.4, 0.6, 0.4, 0.1);
            ice.world.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BLOCK_GLASS_BREAK,
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    private static void thawNow(Entity e) {
        Ice ice = ICE.remove(e.getUuid());
        if (ice != null) thaw(e, ice);
    }

    /** Dano de um jogador quebra o gelo (o dano da própria Glacial não conta). */
    public static void onDamaged(LivingEntity entity, DamageSource source) {
        if (ICE.isEmpty() || !ICE.containsKey(entity.getUuid())) return;
        if (source.isOf(ModDamage.FROST)) return;
        if (!(source.getAttacker() instanceof PlayerEntity)) return;
        thawNow(entity);
    }

    // ------------------------------------------------------------------ ULTIMATE

    public static boolean blizzard(ServerPlayerEntity p, ServerWorld w) {
        if (BLIZZARDS.containsKey(p.getUuid())) {
            p.sendMessage(Text.literal("Sua nevasca já está ativa."), true);
            return false;
        }
        BLIZZARDS.put(p.getUuid(), new Blizzard());
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.PLAYERS, 2.0f, 0.5f);
        w.spawnParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 150, 4, 1.5, 4, 0.1);
        p.sendMessage(Text.literal("Nevasca ativada: 15 segundos."), true);
        return true;
    }

    private static boolean inside(ServerPlayerEntity owner, LivingEntity e) {
        double dx = e.getX() - owner.getX();
        double dz = e.getZ() - owner.getZ();
        return dx * dx + dz * dz <= BLIZZARD_RADIUS * BLIZZARD_RADIUS
                && Math.abs(e.getY() - owner.getY()) <= BLIZZARD_HALF_HEIGHT;
    }

    private static boolean inFog(ServerPlayerEntity pl) {
        MinecraftServer server = pl.getEntityWorld().getServer();
        for (UUID ownerId : BLIZZARDS.keySet()) {
            if (ownerId.equals(pl.getUuid())) continue;
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
            if (owner != null && owner.getEntityWorld() == pl.getEntityWorld() && inside(owner, pl)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ tempo

    public static void tick(MinecraftServer server) {
        int now = server.getTicks();

        // ---- Congelamento: decaimento, dano por segundo e visual
        if (!FROST.isEmpty()) {
            Iterator<Map.Entry<UUID, Frost>> it = FROST.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Frost> entry = it.next();
                Frost f = entry.getValue();
                Entity e = findEntity(server, entry.getKey());
                if (!(e instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }

                f.timer--;
                if (f.timer <= 0) {
                    if (f.level > 1) {
                        f.level--;
                        f.crits = 0;
                        f.timer = DECAY_TICKS;
                        refreshEffect(le, f);
                    } else {
                        clear(le);
                        it.remove();
                        continue;
                    }
                }
                if (f.level > 0) {
                    le.setFrozenTicks(Math.min(139, 35 * f.level)); // tremor/azul sem causar dano de congelamento
                    if (now % 20 == 0 && le.getEntityWorld() instanceof ServerWorld lw) {
                        lw.spawnParticles(ParticleTypes.SNOWFLAKE, le.getX(), le.getY() + le.getHeight() / 2, le.getZ(),
                                3 * f.level, 0.3, 0.5, 0.3, 0.02);
                        // 0,5 coração x nível por segundo (= nível em pontos de vida)
                        ModDamage.hit(lw, le, ModDamage.FROST, player(server, f.attacker), (float) f.level);
                    }
                }
            }
        }

        // ---- Gelo: acaba em 2 s
        if (!ICE.isEmpty()) {
            Iterator<Map.Entry<UUID, Ice>> it = ICE.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Ice> entry = it.next();
                Ice ice = entry.getValue();
                Entity e = findEntity(server, entry.getKey());
                ice.ticksLeft--;
                if (e == null || !e.isAlive() || ice.ticksLeft <= 0) {
                    thaw(e != null && e.isAlive() ? e : null, ice);
                    it.remove();
                }
            }
        }

        // ---- Nevasca
        if (!BLIZZARDS.isEmpty()) {
            Iterator<Map.Entry<UUID, Blizzard>> it = BLIZZARDS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Blizzard> entry = it.next();
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.getKey());
                Blizzard bz = entry.getValue();
                if (owner == null || !owner.isAlive() || owner.isSpectator()) { it.remove(); continue; }

                bz.ticksLeft--;
                if (bz.ticksLeft <= 0) {
                    it.remove();
                    owner.sendMessage(Text.literal("A nevasca acabou."), true);
                    continue;
                }
                ServerWorld w = owner.getEntityWorld();
                if (bz.ticksLeft % 2 == 0) {
                    w.spawnParticles(ParticleTypes.SNOWFLAKE, owner.getX(), owner.getY() + 3, owner.getZ(),
                            40, BLIZZARD_RADIUS * 0.7, 2.5, BLIZZARD_RADIUS * 0.7, 0.05);
                }
                if (bz.ticksLeft % 20 == 0) {
                    Box box = new Box(owner.getX() - BLIZZARD_RADIUS, owner.getY() - BLIZZARD_HALF_HEIGHT, owner.getZ() - BLIZZARD_RADIUS,
                            owner.getX() + BLIZZARD_RADIUS, owner.getY() + BLIZZARD_HALF_HEIGHT, owner.getZ() + BLIZZARD_RADIUS);
                    for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box,
                            e -> e != owner && e.isAlive() && !e.isSpectator())) {
                        if (!inside(owner, e)) continue;
                        Frost f = FROST.get(e.getUuid());
                        if (f == null) {
                            f = new Frost();
                            FROST.put(e.getUuid(), f);
                        }
                        f.attacker = owner.getUuid();
                        applyLevel(e, f, Math.min(MAX_LEVEL, f.level + 1));
                        if (e instanceof MobEntity) {
                            e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false));
                        }
                    }
                }
            }
        }

        // ---- Ícone de gelo e névoa no cliente (a cada 5 ticks, só quando muda)
        if (now % 5 == 0) {
            for (ServerPlayerEntity pl : server.getPlayerManager().getPlayerList()) {
                boolean can = false;
                if (pl.isAlive() && RelicData.getRelicItem(pl) instanceof Relics.Glacial
                        && EnergyData.get(pl) >= 0 && RelicItem.standardReady(pl)) {
                    can = !freezeTargets(pl, pl.getEntityWorld()).isEmpty();
                }
                boolean fog = inFog(pl);
                int state = (can ? 1 : 0) | (fog ? 2 : 0);
                Integer last = HUD_STATE.get(pl.getUuid());
                int before = last == null ? 0 : last;
                if (state != before) {
                    ServerPlayNetworking.send(pl, new GlacialHudPayload(can, fog));
                    HUD_STATE.put(pl.getUuid(), state);
                }
            }
        }
    }

    // ------------------------------------------------------------------ limpeza

    /** Jogador saiu: encerra a nevasca dele. */
    public static void forget(ServerPlayerEntity p) {
        BLIZZARDS.remove(p.getUuid());
        HUD_STATE.remove(p.getUuid());
    }

    /** Entidade descarregada ou morta: descongela e esquece o Congelamento. */
    public static void release(Entity e) {
        thawNow(e);
        FROST.remove(e.getUuid());
    }

    /** Servidor fechando (os blocos de gelo são só visuais, nada fica salvo no mundo). */
    public static void releaseAll(MinecraftServer server) {
        FROST.clear();
        ICE.clear();
        BLIZZARDS.clear();
        HUD_STATE.clear();
    }

    private static ServerPlayerEntity player(MinecraftServer server, UUID id) {
        return id == null ? null : server.getPlayerManager().getPlayer(id);
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }
}
