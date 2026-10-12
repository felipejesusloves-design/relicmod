package com.example.relicmod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Servidor -> cliente: estado visual da Glacial.
 *  canFreeze: mostra o ícone de gelo (a habilidade padrão pode ser usada)
 *  fog: o jogador está dentro de uma nevasca (tela de névoa branca)
 */
public record GlacialHudPayload(boolean canFreeze, boolean fog) implements CustomPayload {
    public static final CustomPayload.Id<GlacialHudPayload> ID =
            new CustomPayload.Id<>(Identifier.of(RelicMod.MOD_ID, "glacial_hud"));
    public static final PacketCodec<RegistryByteBuf, GlacialHudPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, GlacialHudPayload::canFreeze,
            PacketCodecs.BOOLEAN, GlacialHudPayload::fog,
            GlacialHudPayload::new);

    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
