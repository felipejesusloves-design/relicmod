package com.example.relicmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Random;

public class RelicMod implements ModInitializer {
    public static final String MOD_ID = "relicmod";
    private static final Random RANDOM = new Random();

    @Override
    public void onInitialize() {
        ModItems.init();
        RelicData.init();
        EnergyData.init();
        ModEffects.init();
        RelicCommand.register();

        // Rede: cliente -> servidor (apertou a tecla)
        PayloadTypeRegistry.playC2S().register(UseAbilityPayload.ID, UseAbilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GlacialHudPayload.ID, GlacialHudPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UseAbilityPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (payload.ultimate()) RelicData.useUltimate(player);
            else RelicData.useStandard(player);
        });

        // Primeiro login: sorteia a relíquia
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (RelicData.getRelicId(player) == null) {
                List<RelicItem> all = ModItems.RELICS;
                RelicItem chosen = all.get(RANDOM.nextInt(all.size()));
                RelicData.setRelicId(player, Registries.ITEM.getId(chosen).toString());
            }
            RelicData.ensureRelic(player);
        });

        // Ultimate da Sombra: o primeiro golpe depois de ativar paralisa e amaldiçoa
        AttackEntityCallback.EVENT.register(ShadowStun::onAttack);
        AttackEntityCallback.EVENT.register(StormPower::onAttack);
        AttackEntityCallback.EVENT.register(GlacialPower::onAttack);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ShadowStun.forget(handler.player);
            StormPower.forget(handler.player);
            GlacialPower.forget(handler.player);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            ShadowStun.release(entity);
            GlacialPower.release(entity);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ShadowStun.releaseAll(server);
            Volcano.restoreAll(server);
            GlacialPower.releaseAll(server);
        });

        // Vulcão da Ígnea: ninguém quebra nem coloca blocos dentro da área
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !Volcano.isProtected(world, pos));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Item held = player.getStackInHand(hand).getItem();
            if (held instanceof BlockItem || held instanceof BucketItem) {
                BlockPos clicked = hit.getBlockPos();
                BlockPos placed = clicked.offset(hit.getSide());
                if (Volcano.isProtected(world, clicked) || Volcano.isProtected(world, placed)) return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // Vulcão: o usuário causa +50% de dano de todas as fontes
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (blocked || damageTaken <= 0) return;
            if (entity instanceof ServerPlayerEntity hurt) StormPower.onDamaged(hurt);
            GlacialPower.onDamaged(entity, source);
            if (source.isOf(ModDamage.FIREBALL) || source.isOf(ModDamage.VOLCANO)
                    || source.isOf(ModDamage.STORM) || source.isOf(ModDamage.STORM_BEAM)) return;
            if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != entity
                    && Volcano.isBuffed(attacker) && entity.getEntityWorld() instanceof ServerWorld w) {
                ModDamage.hit(w, entity, ModDamage.VOLCANO, attacker, damageTaken * 0.5f);
            }
        });

        // Relíquia largada no chão é apagada na hora (a verdadeira volta ao inventário)
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity itemEntity
                    && itemEntity.getStack().getItem() instanceof RelicItem) {
                world.getServer().execute(itemEntity::discard);
            }
        });

        // Soulbound: tira do inventário ao morrer e devolve no respawn
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) RelicData.removeRelics(player);
            return true;
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> RelicData.ensureRelic(newPlayer));

        // Energia: morrer = -1, matar outro jogador = +1
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity victim) {
                EnergyData.add(victim, -1);
                if (source.getAttacker() instanceof ServerPlayerEntity killer && killer != victim) {
                    EnergyData.add(killer, 1);
                }
            }
        });

        // Todo tick: possessão. A cada 5 ticks: garante a relíquia. A cada 20 ticks: passiva e energia.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ShadowStun.tick(server);
            Fireballs.tick(server);
            Volcano.tick(server);
            StormPower.tick(server);
            GlacialPower.tick(server);
            int tick = server.getTicks();

            // nado na lava (Ígnea)
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isInLava() && RelicData.getRelicItem(player) instanceof Relics.Igneous) {
                    Relics.Igneous.lavaSwim(player);
                }
            }

            if (tick % 5 != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!player.isAlive()) continue;
                RelicData.ensureRelic(player);
                if (tick % 20 == 0) {
                    Item item = RelicData.getRelicItem(player);
                    if (item instanceof RelicItem relic) relic.applyPassive(player, EnergyData.get(player));
                    EnergyData.show(player);
                }
            }
        });
    }
}
