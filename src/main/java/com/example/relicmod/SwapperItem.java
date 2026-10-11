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
        if (PossessionManager.isPossessing(player)) return ActionResult.FAIL;

        Item current = RelicData.getRelicItem(player);
        List<RelicItem> options = new ArrayList<>();
        for (RelicItem r : ModItems.RELICS) {
            if (r != current) options.add(r);
        }
        if (options.isEmpty()) return ActionResult.FAIL;

        RelicItem chosen = options.get(RANDOM.nextInt(options.size()));
        RelicData.changeRelic(player, chosen);

        ItemStack stack = player.getStackInHand(hand);
        if (!player.isCreative()) stack.decrement(1);

        player.getEntityWorld().spawnParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1, player.getZ(), 60, 0.5, 0.8, 0.5, 0.5);
        player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        player.sendMessage(Text.literal("Sua nova relíquia: ").append(chosen.getName()), true);
        return ActionResult.SUCCESS;
    }
}
