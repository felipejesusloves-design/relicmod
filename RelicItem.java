package com.example.relicmod;

import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cada relíquia tem 3 habilidades:
 *  - passiva: sempre ativa, qualquer energia
 *  - padrão: energia 0 ou mais
 *  - ultimate: somente energia +5
 */
public abstract class RelicItem extends Item {
    private static final Map<UUID, Integer> STANDARD_READY = new HashMap<>();
    private static final Map<UUID, Integer> ULTIMATE_READY = new HashMap<>();

    private final int standardCooldown;
    private final int ultimateCooldown;

    public RelicItem(Settings settings, int standardCooldown, int ultimateCooldown) {
        super(settings.maxCount(1).fireproof());
        this.standardCooldown = standardCooldown;
        this.ultimateCooldown = ultimateCooldown;
    }

    /** Rodado a cada segundo no servidor. */
    public abstract void applyPassive(ServerPlayerEntity player, int energy);

    /** Habilidade padrão. Devolve true se foi usada (aí começa o tempo de espera). */
    protected abstract boolean useStandard(ServerPlayerEntity player, ServerWorld world, int energy);

    public boolean hasUltimate() { return false; }

    /** Ultimate. Devolve true se foi usada. */
    protected boolean useUltimate(ServerPlayerEntity player, ServerWorld world, int energy) { return false; }

    public final void tryStandard(ServerPlayerEntity player) {
        int energy = EnergyData.get(player);
        if (energy < 0) {
            player.sendMessage(Text.literal("Habilidade padrão bloqueada: precisa de energia 0 ou mais."), true);
            return;
        }
        if (!ready(player, STANDARD_READY)) return;
        if (useStandard(player, player.getEntityWorld(), energy)) {
            startCooldown(player, STANDARD_READY, standardCooldown);
        }
    }

    public final void tryUltimate(ServerPlayerEntity player) {
        if (!hasUltimate()) {
            player.sendMessage(Text.literal("Esta relíquia ainda não tem ultimate."), true);
            return;
        }
        int energy = EnergyData.get(player);
        if (energy < EnergyData.MAX) {
            player.sendMessage(Text.literal("Ultimate bloqueada: precisa de energia +5."), true);
            return;
        }
        if (!ready(player, ULTIMATE_READY)) return;
        if (useUltimate(player, player.getEntityWorld(), energy)) {
            startCooldown(player, ULTIMATE_READY, ultimateCooldown);
        }
    }

    private static int now(ServerPlayerEntity p) {
        return p.getEntityWorld().getServer().getTicks();
    }

    private static boolean ready(ServerPlayerEntity p, Map<UUID, Integer> map) {
        Integer until = map.get(p.getUuid());
        int now = now(p);
        if (until != null && now < until) {
            int seconds = (until - now + 19) / 20;
            p.sendMessage(Text.literal("Em recarga: " + seconds + " s"), true);
            return false;
        }
        return true;
    }

    private static void startCooldown(ServerPlayerEntity p, Map<UUID, Integer> map, int ticks) {
        map.put(p.getUuid(), now(p) + ticks);
    }
}
