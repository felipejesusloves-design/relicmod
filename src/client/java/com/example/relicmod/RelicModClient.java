package com.example.relicmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class RelicModClient implements ClientModInitializer {
    private static KeyBinding standardKey;
    private static KeyBinding ultimateKey;

    // estado visual da Glacial (enviado pelo servidor)
    private static boolean canFreeze = false;
    private static boolean fog = false;

    // NOVO: cooldowns em segundos (enviados pelo servidor)
    private static int cdStandard = 0;
    private static int cdUltimate = 0;
    private static int cdStandardTotal = 1;
    private static int cdUltimateTotal = 1;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(RelicMod.MOD_ID, "main"));
        standardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.relicmod.ability", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
        ultimateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.relicmod.ultimate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                canFreeze = false;
                fog = false;
                cdStandard = 0;
                cdUltimate = 0;
            }
            while (standardKey.wasPressed()) {
                if (client.player != null) ClientPlayNetworking.send(new UseAbilityPayload(false));
            }
            while (ultimateKey.wasPressed()) {
                if (client.player != null) ClientPlayNetworking.send(new UseAbilityPayload(true));
            }
        });

        // Glacial: recebe do servidor se o ícone e a névoa devem aparecer
        ClientPlayNetworking.registerGlobalReceiver(GlacialHudPayload.ID, (payload, context) -> {
            canFreeze = payload.canFreeze();
            fog = payload.fog();
        });

        // NOVO: recebe os tempos de cooldown do servidor
        ClientPlayNetworking.registerGlobalReceiver(CooldownHudPayload.ID, (payload, context) -> {
            // se o tempo SUBIU, é um cooldown novo: guarda o total para a barra encolher certinho
            if (payload.standard() > cdStandard) cdStandardTotal = Math.max(1, payload.standard());
            if (payload.ultimate() > cdUltimate) cdUltimateTotal = Math.max(1, payload.ultimate());
            cdStandard = payload.standard();
            cdUltimate = payload.ultimate();
        });

        // Névoa branca da nevasca (quase opaca)
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.of(RelicMod.MOD_ID, "glacial_fog"), (context, tickCounter) -> {
                    if (!fog) return;
                    context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0xE6FFFFFF);
                });

        // Ícone de gelo: a habilidade padrão da Glacial pode ser usada
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.of(RelicMod.MOD_ID, "glacial_icon"), (context, tickCounter) -> {
                    if (!canFreeze) return;
                    int x = context.getScaledWindowWidth() / 2 + 96;
                    int y = context.getScaledWindowHeight() - 41;
                    context.drawItem(new ItemStack(Items.BLUE_ICE), x, y);
                });

        // NOVO: barras de cooldown no TOPO da tela (só enquanto recarrega)
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.of(RelicMod.MOD_ID, "cooldown_bars"), (context, tickCounter) -> {
                    if (cdStandard <= 0 && cdUltimate <= 0) return;
                    int screenW = context.getScaledWindowWidth();
                    int y = 4;
                    if (cdStandard > 0) {
                        y = drawCooldownBar(context, screenW, y, "PADRÃO", cdStandard, cdStandardTotal, 0xCC55FFFF);
                    }
                    if (cdUltimate > 0) {
                        drawCooldownBar(context, screenW, y, "ULTIMATE", cdUltimate, cdUltimateTotal, 0xCCFFAA00);
                    }
                });
    }

    /** Desenha uma barra de cooldown e devolve a altura da próxima linha. */
    private static int drawCooldownBar(DrawContext context, int screenW, int y, String label, int left, int total, int color) {
        int w = 100;
        int h = 12;
        int x0 = screenW / 2 - w / 2;

        // moldura preta + fundo escuro
        context.fill(x0 - 1, y - 1, x0 + w + 1, y + h + 1, 0xFF000000);
        context.fill(x0, y, x0 + w, y + h, 0xAA000000);

        // parte colorida que encolhe com o tempo
        int filled = total > 0 ? (w * left) / total : 0;
        if (filled > 0) context.fill(x0, y, x0 + filled, y + h, color);

        // texto centralizado: "PADRÃO 7s"
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String text = label + " " + left + "s";
        context.drawText(tr, text, x0 + (w - tr.getWidth(text)) / 2, y + 2, 0xFFFFFFFF, true);

        return y + h + 4;
    }
}
