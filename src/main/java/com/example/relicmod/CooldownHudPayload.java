package com.example.relicmod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Avisa o cliente quantos segundos faltam em cada cooldown (0 = pronto). */
public record CooldownHudPayload(int standard, int ultimate) implements CustomPayload {
    public static final CustomPayload.Id<CooldownHudPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RelicMod.MOD_ID, "cooldown_hud"));

    public static final PacketCodec<RegistryByteBuf, CooldownHudPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, CooldownHudPayload::standard,
            PacketCodecs.VAR_INT, CooldownHudPayload::ultimate,
            CooldownHudPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
