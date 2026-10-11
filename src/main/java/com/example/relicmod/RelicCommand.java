package com.example.relicmod;

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
 * Comandos:
 *   /relic set <igneous|glacial|shadow|storm>   troca a sua relíquia
 *   /relic random                               sorteia uma relíquia
 */
public class RelicCommand {
    private static final Random RANDOM = new Random();

    private static List<String> names() {
        return ModItems.RELICS.stream()
                .map(r -> Registries.ITEM.getId(r).getPath().replace("_relic", ""))
                .toList();
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("relic")
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("tipo", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandSource.suggestMatching(names(), builder))
                        .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "tipo")))))
                .then(CommandManager.literal("random")
                    .executes(ctx -> random(ctx.getSource())))));
    }

    private static int set(ServerCommandSource source, String name) throws CommandSyntaxException {
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

    private static int random(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        RelicItem relic = ModItems.RELICS.get(RANDOM.nextInt(ModItems.RELICS.size()));
        RelicData.changeRelic(player, relic);
        source.sendFeedback(() -> Text.literal("Sua relíquia agora é: ").append(relic.getName()), false);
        return 1;
    }
}
