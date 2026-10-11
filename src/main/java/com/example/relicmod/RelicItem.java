package com.example.relicmod;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public abstract class RelicItem extends Item {
    private final int cooldownTicks;

    public RelicItem(Settings settings, int cooldownTicks) {
        super(settings.maxCount(1).fireproof());
        this.cooldownTicks = cooldownTicks;
    }

    /** Rodado a cada segundo no servidor. */
    public abstract void applyPassive(ServerPlayerEntity player);

    /** Habilidade ativa (tecla). */
    protected abstract void useAbility(ServerPlayerEntity player, ServerWorld world);

    public final void tryActivate(ServerPlayerEntity player, ItemStack stack) {
        if (player.getItemCooldownManager().isCoolingDown(stack)) return;
        useAbility(player, player.getEntityWorld());
        player.getItemCooldownManager().set(stack, cooldownTicks);
    }
}
