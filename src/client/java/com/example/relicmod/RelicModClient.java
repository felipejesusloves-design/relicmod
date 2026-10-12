package com.example.relicmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
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
    }
}
