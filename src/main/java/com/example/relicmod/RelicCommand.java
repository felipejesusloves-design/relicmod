package com.example.relicmod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Random;

/**
 * Comandos (somente operadores):
 *   /relic set <igneous|glacial|shadow|storm>
 *   /relic random
 *   /energy set <-5 a 5>
 *   /energy get
 */
public class RelicCommand {
    private static final Random RANDOM = new Random();

    private static List<String> names() {
        return ModItems.RELICS.stream()
                .map(r -> Registries.ITEM.getId(r).getPath().replace("_relic", ""))
                .toList();
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("relic")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("tipo", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandSource.suggestMatching(names(), builder))
                        .executes(ctx -> setRelic(ctx.getSource(), StringArgumentType.getString(ctx, "tipo")))))
                .then(CommandManager.literal("random")
                    .executes(ctx -> randomRelic(ctx.getSource()))));

            dispatcher.register(CommandManager.literal("energy")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("valor", IntegerArgumentType.integer(EnergyData.MIN, EnergyData.MAX))
                        .executes(ctx -> setEnergy(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "valor")))))
                .then(CommandManager.literal("get")
                    .executes(ctx -> getEnergy(ctx.getSource()))));
        });
    }

    private static int setRelic(ServerCommandSource source, String name) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        for (RelicItem relic : ModItems.RELICS) {
            String n = Registries.ITEM.getId(relic).getPath().replace("_relic", "");
            if (n.equalsIgnoreCase(name)) {
                RelicData.changeRelic(player, relic);
                source.sendFeedback(() -> Text.literal("Sua relíquia agora é: ").append(relic.getName()), false);
                return 1;
            }
        }
        source.sendError(Text.literal("Relíquia desconhecida. Opções: " + String.join(", ", names())));
        return 0;
    }

    private static int randomRelic(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        RelicItem relic = ModItems.RELICS.get(RANDOM.nextInt(ModItems.RELICS.size()));
        RelicData.changeRelic(player, relic);
        source.sendFeedback(() -> Text.literal("Sua relíquia agora é: ").append(relic.getName()), false);
        return 1;
    }

    private static int setEnergy(ServerCommandSource source, int value) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        EnergyData.set(player, value);
        source.sendFeedback(() -> Text.literal("Energia definida para " + value), false);
        return 1;
    }

    private static int getEnergy(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        int v = EnergyData.get(player);
        source.sendFeedback(() -> Text.literal("Sua energia: " + v), false);
        return v;
    }
}
