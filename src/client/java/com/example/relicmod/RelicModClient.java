package com.example.relicmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class RelicModClient implements ClientModInitializer {
    private static KeyBinding standardKey;
    private static KeyBinding ultimateKey;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(RelicMod.MOD_ID, "main"));
        standardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.relicmod.ability", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
        ultimateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.relicmod.ultimate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (standardKey.wasPressed()) {
                if (client.player != null) ClientPlayNetworking.send(new UseAbilityPayload(false));
            }
            while (ultimateKey.wasPressed()) {
                if (client.player != null) ClientPlayNetworking.send(new UseAbilityPayload(true));
            }
        });
    }
}
