package com.example.relicmod;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Bola de fogo da Ígnea. É controlada pelo servidor (não é uma entidade), por isso não pode ser rebatida.
 * Ao bater em um ser vivo ou bloco, explode SEM destruir blocos:
 *   acerto direto = dano cheio; seres em volta (raio 3) = 50% do dano.
 */
public class Fireballs {
    private static final double SPEED = 1.2;          // blocos por tick
    private static final double MAX_DISTANCE = 60;
    private static final double RADIUS = 3.0;         // raio da explosão
    private static final float SPLASH_FACTOR = 0.5f;  // dano em área

    private static class Bolt {
        ServerWorld world;
        double x, y, z;
        Vec3d dir;
        UUID owner;
        float damage;       // em pontos de vida (2 = 1 coração)
        double traveled;
    }

    private static final List<Bolt> BOLTS = new ArrayList<>();

    /** Dano direto: 4 corações (energia 0 a +3) ou 5 corações (+4 e +5). */
    public static float damageFor(int energy) {
        return energy >= 4 ? 10.0f : 8.0f;
    }

    public static boolean launch(ServerPlayerEntity p, ServerWorld w, int energy) {
        Vec3d look = p.getRotationVec(1.0f).normalize();
        Vec3d eye = p.getEyePos();
        Bolt b = new Bolt();
        b.world = w;
        b.x = eye.x + look.x;
        b.y = eye.y + look.y - 0.2;
        b.z = eye.z + look.z;
        b.dir = look;
        b.owner = p.getUuid();
        b.damage = damageFor(energy);
        BOLTS.add(b);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (BOLTS.isEmpty()) return;
        Iterator<Bolt> it = BOLTS.iterator();
        while (it.hasNext()) {
            Bolt b = it.next();
            if (!step(server, b)) it.remove();
        }
    }

    /** Avança a bola. Devolve false quando ela terminou (explodiu ou acabou o alcance). */
    private static boolean step(MinecraftServer server, Bolt b) {
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(b.owner);
        int sub = 3;
        double stepLen = SPEED / sub;
        for (int i = 0; i < sub; i++) {
            double nx = b.x + b.dir.x * stepLen;
            double ny = b.y + b.dir.y * stepLen;
            double nz = b.z + b.dir.z * stepLen;

            BlockPos bp = BlockPos.ofFloored(nx, ny, nz);
            BlockState st = b.world.getBlockState(bp);
            if (!st.getCollisionShape(b.world, bp).isEmpty()) {
                explode(b, owner, b.x, b.y, b.z, null);
                return false;
            }

            Box box = new Box(nx - 0.5, ny - 0.5, nz - 0.5, nx + 0.5, ny + 0.5, nz + 0.5);
            List<LivingEntity> hit = b.world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(b.owner));
            if (!hit.isEmpty()) {
                explode(b, owner, nx, ny, nz, hit.get(0));
                return false;
            }

            b.x = nx; b.y = ny; b.z = nz;
            b.traveled += stepLen;
            if (b.traveled >= MAX_DISTANCE) return false;
        }
        b.world.spawnParticles(ParticleTypes.FLAME, b.x, b.y, b.z, 8, 0.12, 0.12, 0.12, 0.01);
        b.world.spawnParticles(ParticleTypes.SMOKE, b.x, b.y, b.z, 2, 0.1, 0.1, 0.1, 0.01);
        return true;
    }

    private static void explode(Bolt b, ServerPlayerEntity owner, double x, double y, double z, LivingEntity direct) {
        ServerWorld w = b.world;
        float dmg = b.damage;
        if (owner != null && Volcano.isBuffed(owner)) dmg *= 1.5f;   // bônus da ultimate

        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.FLAME, x, y, z, 60, 1.2, 1.2, 1.2, 0.08);
        w.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.0f);

        if (direct != null) {
            ModDamage.hit(w, direct, ModDamage.FIREBALL, owner, dmg);
            direct.setOnFireFor(4);
        }

        Box area = new Box(x - RADIUS, y - RADIUS, z - RADIUS, x + RADIUS, y + RADIUS, z + RADIUS);
        final float splash = dmg * SPLASH_FACTOR;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, area,
                e -> e.isAlive() && !e.isSpectator() && e != direct && !e.getUuid().equals(b.owner))) {
            if (e.squaredDistanceTo(x, y, z) <= RADIUS * RADIUS) {
                ModDamage.hit(w, e, ModDamage.FIREBALL, owner, splash);
            }
        }
    }
}
