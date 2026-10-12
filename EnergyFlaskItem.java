package com.example.relicmod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** Frasco de energia: botão direito dá +1 de energia (não gasta se já estiver em +5). */
public class EnergyFlaskItem extends Item {
    public EnergyFlaskItem(Settings settings) {
        super(settings.maxCount(16));
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!(user instanceof ServerPlayerEntity player)) return ActionResult.SUCCESS;

        if (EnergyData.get(player) >= EnergyData.MAX) {
            player.sendMessage(Text.literal("Sua energia já está no máximo (+5)."), true);
            return ActionResult.FAIL;
        }
        EnergyData.add(player, 1);

        ItemStack stack = player.getStackInHand(hand);
        if (!player.isCreative()) stack.decrement(1);

        player.getEntityWorld().spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1, player.getZ(), 25, 0.4, 0.7, 0.4, 0.1);
        player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.6f);
        return ActionResult.SUCCESS;
    }
}
