package com.aiplayer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

public class BotSpawnCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bot")
            .then(Commands.literal("spawn")
                .then(Commands.argument("name", StringArgumentType.string())  // ← string() вместо word()
                    .then(Commands.argument("skin", StringArgumentType.string())  // ← string() вместо word()
                        .executes(ctx -> spawnBot(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "skin")))
                    )
                    .executes(ctx -> spawnBot(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "Steve"))  // Дефолтный скин
                )
                .executes(ctx -> spawnBot(ctx.getSource(), "AI_Bot", "Steve"))  // /bot spawn
            )
        );
    }

    private static int spawnBot(CommandSourceStack source, String name, String skinName) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("Только игроки могут использовать эту команду!"));
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();

        EntityType<AIPlayerEntity> type = ModEntities.AI_PLAYER.get();
        AIPlayerEntity bot = type.create(level);
        if (bot == null) {
            source.sendFailure(Component.literal("Ошибка: не удалось создать сущность бота."));
            return 0;
        }

        bot.setPos(player.getX() + 1.0, player.getY(), player.getZ() + 1.0);
        bot.setCustomName(Component.literal(name));  // Поддержка кириллицы
        bot.setCustomNameVisible(true);

        bot.setOwnerUUID(player.getUUID());
        bot.setTame(true);

        bot.setSkinName(skinName);

        level.addFreshEntity(bot);

        source.sendSuccess(() -> Component.literal("Бот " + name + " создан с скином " + skinName + "."), true);
        return 1;
    }
}