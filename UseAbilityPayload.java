package com.example.relicmod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Cliente -> servidor: apertou a tecla da habilidade (ultimate = false: padrão). */
public record UseAbilityPayload(boolean ultimate) implements CustomPayload {
    public static final CustomPayload.Id<UseAbilityPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RelicMod.MOD_ID, "use_ability"));
    public static final PacketCodec<RegistryByteBuf, UseAbilityPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.BOOLEAN, UseAbilityPayload::ultimate, UseAbilityPayload::new);

    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
