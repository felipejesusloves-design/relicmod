package com.example.relicmod;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class VFX {
    public static void spawnTeleportVortex(ServerWorld world, BlockPos pos) {
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.5, 1.0, 0.5, 0.1);
        world.spawnParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
        world.playSound(null, pos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    public static void spawnLightningArc(ServerWorld world, Entity source, Entity target) {
        Vec3d start = new Vec3d(source.getX(), source.getY(), source.getZ());
        Vec3d end = new Vec3d(target.getX(), target.getY(), target.getZ());
        int steps = 20;
        for (int i = 0; i <= steps; i++) {
            double ratio = (double) i / steps;
            double x = start.x + (end.x - start.x) * ratio + (world.random.nextDouble() - 0.5) * 0.5;
            double y = start.y + (end.y - start.y) * ratio + (world.random.nextDouble() - 0.5) * 0.5 + 1.0;
            double z = start.z + (end.z - start.z) * ratio + (world.random.nextDouble() - 0.5) * 0.5;
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2, 0.0, 0.0, 0.0, 0.0);
            if (i % 3 == 0) world.spawnParticles(ParticleTypes.FIREWORK, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    public static void spawnVolcanoEruption(ServerWorld world, BlockPos pos) {
        for (int y = 0; y < 5; y++) {
            world.spawnParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5, 5, 0.2, 0.1, 0.2, 0.05);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.02);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(null, pos, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.7f);
    }

    public static void spawnBlizzardUltimate(ServerWorld world, BlockPos center, int radius) {
        for (int i = 0; i < 100; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * radius;
            double x = center.getX() + Math.cos(angle) * r;
            double z = center.getZ() + Math.sin(angle) * r;
            double y = center.getY() + world.random.nextDouble() * 4;
            world.spawnParticles(ParticleTypes.SNOWFLAKE, x, y, z, 1, 0.0, -0.1, 0.0, 0.05);
        }
        world.playSound(null, center, SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.PLAYERS, 1.0f, 0.5f);
    }

    public static void spawnShadowCurse(ServerWorld world, Entity target) {
        BlockPos pos = target.getBlockPos();
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.3, 0.5, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.2, 0.2, 0.2, 0.05);
        world.playSound(null, pos, SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 0.6f, 0.8f);
    }
}
