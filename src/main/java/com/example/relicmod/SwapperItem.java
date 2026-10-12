package com.example.relicmod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Trocador: botão direito sorteia outra relíquia (diferente da atual) e gasta o item. */
public class SwapperItem extends Item {
    private static final Random RANDOM = new Random();

    public SwapperItem(Settings settings) {
        super(settings.maxCount(16));
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!(user instanceof ServerPlayerEntity player)) return ActionResult.SUCCESS;

        Item current = RelicData.getRelicItem(player);
        List<RelicItem> options = new ArrayList<>();
        for (RelicItem r : ModItems.RELICS) {
            if (r != current) options.add(r);
        }
        if (options.isEmpty()) return ActionResult.FAIL;

        RelicItem chosen = options.get(RANDOM.nextInt(options.size()));

        // VISUAL ANTES DA TROCA: vórtice de portal + anel dourado subindo em volta do jogador
        if (world instanceof ServerWorld sw) {
            VFX.spawnTeleportVortex(sw, player.getBlockPos());
            for (int i = 0; i < 24; i++) {
                double angle = i * (Math.PI * 2 / 24);
                sw.spawnParticles(ParticleTypes.ENCHANT,
                        player.getX() + Math.cos(angle) * 1.2,
                        player.getY() + 0.2 + (i % 4) * 0.5,
                        player.getZ() + Math.sin(angle) * 1.2,
                        1, 0, 0.05, 0, 0.02);
            }
        }

        RelicData.changeRelic(player, chosen);

        ItemStack stack = player.getStackInHand(hand);
        if (!player.isCreative()) stack.decrement(1);

        // VISUAL DEPOIS DA TROCA: flash branco + chuva de brilho + som de totum
        if (world instanceof ServerWorld sw2) {
            sw2.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
            sw2.spawnParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(), 80, 0.6, 0.9, 0.6, 0.6);
            sw2.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
        player.sendMessage(Text.literal("Sua nova relíquia: ").append(chosen.getName()), true);
        return ActionResult.SUCCESS;
    }
}
