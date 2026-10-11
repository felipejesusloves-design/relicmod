package com.example.relicmod;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class RelicData {
    /** Guarda o id da relíquia do jogador. Persiste no save e sobrevive à morte. */
    public static final AttachmentType<String> RELIC = AttachmentRegistry.create(
            Identifier.of(RelicMod.MOD_ID, "relic"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath());

    public static void init() {}

    public static String getRelicId(ServerPlayerEntity p) { return p.getAttached(RELIC); }

    public static void setRelicId(ServerPlayerEntity p, String id) { p.setAttached(RELIC, id); }

    public static Item getRelicItem(ServerPlayerEntity p) {
        String id = getRelicId(p);
        if (id == null) return null;
        return Registries.ITEM.get(Identifier.of(id));
    }

    public static ItemStack findRelicStack(ServerPlayerEntity p) {
        Item relic = getRelicItem(p);
        if (relic == null) return ItemStack.EMPTY;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(relic)) return s;
        }
        return ItemStack.EMPTY;
    }

    /** Dá a relíquia se o jogador não tiver. */
    public static void ensureRelic(ServerPlayerEntity p) {
        Item relic = getRelicItem(p);
        if (!(relic instanceof RelicItem)) return;
        if (findRelicStack(p).isEmpty()) {
            p.getInventory().offerOrDrop(new ItemStack(relic));
        }
    }

    /** Remove relíquias do inventário (para não dropar na morte). */
    public static void removeRelics(ServerPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).getItem() instanceof RelicItem) inv.setStack(i, ItemStack.EMPTY);
        }
    }

    /** Troca a relíquia do jogador por outra. */
    public static void changeRelic(ServerPlayerEntity p, RelicItem newRelic) {
        removeRelics(p);
        setRelicId(p, Registries.ITEM.getId(newRelic).toString());
        ensureRelic(p);
    }

    public static void useStandard(ServerPlayerEntity p) {
        if (getRelicItem(p) instanceof RelicItem relic) relic.tryStandard(p);
    }

    public static void useUltimate(ServerPlayerEntity p) {
        if (getRelicItem(p) instanceof RelicItem relic) relic.tryUltimate(p);
    }
}
