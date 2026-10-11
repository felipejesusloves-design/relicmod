package com.example.relicmod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Ultimate da Ígnea: o terreno natural em volta vira netherrack/magma (com fogo) por 15 s.
 * Dentro da área ninguém coloca ou quebra blocos, os seres (menos o usuário) tomam 1 coração por segundo
 * e o usuário causa 50% a mais de dano. No fim, o terreno original volta.
 */
public class Volcano {
    public static final int RADIUS = 8;
    public static final int HALF_HEIGHT = 6;
    public static final int DURATION = 20 * 15;

    private static final Random RANDOM = new Random();

    private static class Instance {
        final ServerWorld world;
        final BlockPos center;
        final UUID owner;
        int ticksLeft = DURATION;
        final Map<BlockPos, BlockState> original = new HashMap<>();

        Instance(ServerWorld world, BlockPos center, UUID owner) {
            this.world = world;
            this.center = center;
            this.owner = owner;
        }
    }

    private static final List<Instance> ACTIVE = new ArrayList<>();

    // ---------- consultas ----------

    /** O usuário está com a ultimate ativa (bônus de 50% de dano)? */
    public static boolean isBuffed(ServerPlayerEntity p) {
        for (Instance v : ACTIVE) if (v.owner.equals(p.getUuid())) return true;
        return false;
    }

    /** O ser está dentro de algum vulcão? */
    public static boolean contains(LivingEntity e) {
        for (Instance v : ACTIVE) {
            if (v.world == e.getEntityWorld() && inside(v, e.getX(), e.getY(), e.getZ())) return true;
        }
        return false;
    }

    /** Esse bloco está dentro de um vulcão (não pode ser colocado nem quebrado)? */
    public static boolean isProtected(World world, BlockPos pos) {
        for (Instance v : ACTIVE) {
            if (v.world == world && inside(v, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)) return true;
        }
        return false;
    }

    private static boolean inside(Instance v, double x, double y, double z) {
        double dx = x - (v.center.getX() + 0.5);
        double dz = z - (v.center.getZ() + 0.5);
        double r = RADIUS + 0.5;
        return dx * dx + dz * dz <= r * r && Math.abs(y - v.center.getY()) <= HALF_HEIGHT;
    }

    // ---------- ativação ----------

    public static boolean activate(ServerPlayerEntity p, ServerWorld w) {
        if (isBuffed(p)) {
            p.sendMessage(Text.literal("Seu vulcão já está ativo."), true);
            return false;
        }
        Instance v = new Instance(w, p.getBlockPos(), p.getUuid());
        transform(v);
        ACTIVE.add(v);

        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 0.5f);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0, 0, 0, 0);
        p.sendMessage(Text.literal("Vulcão ativado: 15 segundos."), true);
        return true;
    }

    private static void transform(Instance v) {
        ServerWorld w = v.world;
        int cx = v.center.getX(), cy = v.center.getY(), cz = v.center.getZ();

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                int x = cx + dx, z = cz + dz;

                // procura o primeiro bloco sólido da coluna, de cima para baixo
                for (int y = cy + 3; y >= cy - HALF_HEIGHT; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState st = w.getBlockState(pos);
                    if (!st.getFluidState().isEmpty()) break;                     // água/lava: não mexe
                    if (st.getCollisionShape(w, pos).isEmpty()) continue;         // ar, plantas...

                    BlockPos above = pos.up();
                    if (isNatural(st) && w.getBlockState(above).getCollisionShape(w, above).isEmpty()) {
                        convert(v, pos);
                        BlockPos below = pos.down();
                        if (isNatural(w.getBlockState(below))) convert(v, below);

                        // fogo em ~20% do chão, longe de qualquer coisa inflamável
                        if (w.getBlockState(above).isAir() && RANDOM.nextInt(100) < 20 && noBurnableNear(w, above)) {
                            v.original.put(above, w.getBlockState(above));
                            w.setBlockState(above, Blocks.FIRE.getDefaultState(), Block.NOTIFY_LISTENERS);
                        }
                    }
                    break; // só o primeiro bloco sólido de cada coluna
                }
            }
        }
    }

    private static void convert(Instance v, BlockPos pos) {
        v.original.put(pos, v.world.getBlockState(pos));
        BlockState novo = RANDOM.nextInt(100) < 70 ? Blocks.NETHERRACK.getDefaultState() : Blocks.MAGMA_BLOCK.getDefaultState();
        v.world.setBlockState(pos, novo, Block.NOTIFY_LISTENERS);
    }

    /** Só terreno natural muda: terra, pedra, areia, cascalho e argila. Construções ficam intactas. */
    private static boolean isNatural(BlockState st) {
        return st.isIn(BlockTags.DIRT) || st.isIn(BlockTags.BASE_STONE_OVERWORLD) || st.isIn(BlockTags.SAND)
                || st.isOf(Blocks.GRAVEL) || st.isOf(Blocks.CLAY);
    }

    private static boolean noBurnableNear(ServerWorld w, BlockPos pos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 5; dy++) {
                    if (w.getBlockState(pos.add(dx, dy, dz)).isBurnable()) return false;
                }
            }
        }
        return true;
    }

    // ---------- passagem do tempo ----------

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Instance> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Instance v = it.next();
            v.ticksLeft--;
            if (v.ticksLeft <= 0) {
                restore(v);
                it.remove();
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(v.owner);
                if (owner != null) owner.sendMessage(Text.literal("O vulcão se apagou."), true);
                continue;
            }
            if (v.ticksLeft % 5 == 0) {
                v.world.spawnParticles(ParticleTypes.FLAME, v.center.getX() + 0.5, v.center.getY() + 1, v.center.getZ() + 0.5,
                        25, RADIUS * 0.6, 1.0, RADIUS * 0.6, 0.02);
                v.world.spawnParticles(ParticleTypes.LAVA, v.center.getX() + 0.5, v.center.getY() + 1, v.center.getZ() + 0.5,
                        4, RADIUS * 0.6, 0.5, RADIUS * 0.6, 0.0);
            }
            if (v.ticksLeft % 20 == 0) damageTick(server, v);
        }
    }

    /** 1 coração por segundo em todos os seres dentro, menos o usuário. Ignora resistências. */
    private static void damageTick(MinecraftServer server, Instance v) {
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(v.owner);
        double cx = v.center.getX(), cy = v.center.getY(), cz = v.center.getZ();
        Box box = new Box(cx - RADIUS, cy - HALF_HEIGHT, cz - RADIUS, cx + RADIUS + 1, cy + HALF_HEIGHT + 1, cz + RADIUS + 1);
        for (LivingEntity e : v.world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(v.owner))) {
            if (inside(v, e.getX(), e.getY(), e.getZ())) {
                ModDamage.hit(v.world, e, ModDamage.VOLCANO, owner, 2.0f);
            }
        }
    }

    private static void restore(Instance v) {
        for (Map.Entry<BlockPos, BlockState> entry : v.original.entrySet()) {
            v.world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_LISTENERS);
        }
        v.original.clear();
    }

    /** Servidor fechando: devolve o terreno antes de salvar. */
    public static void restoreAll(MinecraftServer server) {
        for (Instance v : ACTIVE) restore(v);
        ACTIVE.clear();
    }
}
