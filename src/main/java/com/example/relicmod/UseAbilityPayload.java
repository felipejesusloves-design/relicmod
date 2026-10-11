package com.example.relicmod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UseAbilityPayload() implements CustomPayload {
    public static final CustomPayload.Id<UseAbilityPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RelicMod.MOD_ID, "use_ability"));
    public static final PacketCodec<RegistryByteBuf, UseAbilityPayload> CODEC =
            PacketCodec.unit(new UseAbilityPayload());

    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
