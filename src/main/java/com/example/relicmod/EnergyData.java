package com.example.relicmod;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Energia do jogador: vai de -5 a +5, começa em 0. Salva no mundo e sobrevive à morte. */
public class EnergyData {
    public static final int MIN = -5;
    public static final int MAX = 5;

    public static final AttachmentType<Integer> ENERGY = AttachmentRegistry.create(
            Identifier.of(RelicMod.MOD_ID, "energy"),
            builder -> builder.persistent(Codec.INT).copyOnDeath());

    public static void init() {}

    public static int get(ServerPlayerEntity p) {
        return p.getAttachedOrElse(ENERGY, 0);
    }

    public static void set(ServerPlayerEntity p, int value) {
        p.setAttached(ENERGY, MathHelper.clamp(value, MIN, MAX));
        show(p);
    }

    public static void add(ServerPlayerEntity p, int delta) {
        set(p, get(p) + delta);
    }

    /** Mostra a energia acima da barra de itens. */
    public static void show(ServerPlayerEntity p) {
        int v = get(p);
        String text = "Energia: " + (v > 0 ? "+" : "") + v;
        Formatting color = v > 0 ? Formatting.GREEN : (v < 0 ? Formatting.RED : Formatting.WHITE);
        p.sendMessage(Text.literal(text).formatted(color), true);
    }
}
