package com.aiplayer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

public class BotSpawnCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bot")
            .then(Commands.literal("spawn")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        return spawnBot(ctx.getSource(), name);
                    })))
            .then(Commands.literal("spawn") // allow no-name spawn
                .executes(ctx -> spawnBot(ctx.getSource(), "AI_Bot"))
            )
        );
    }

    private static int spawnBot(CommandSourceStack source, String name) {
        if (source.getEntity() == null) {
            source.sendFailure(Component.literal("Команду можно использовать только из игры."));
            return 0;
        }
        
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("Только игроки могут использовать эту команду!"));
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();

        // Создаём сущность через EntityType.create(...)
        EntityType<AIPlayerEntity> type = ModEntities.AI_PLAYER.get();
        AIPlayerEntity bot = type.create(level);
        if (bot == null) {
            source.sendFailure(Component.literal("Ошибка: не удалось создать сущность бота."));
            return 0;
        }

        bot.setPos(player.getX() + 1.0, player.getY(), player.getZ() + 1.0);
        bot.setCustomName(Component.literal(name));
        bot.setCustomNameVisible(true);

        // Добавляем на серверный мир
        level.addFreshEntity(bot);

        source.sendSuccess(() -> Component.literal("Бот " + name + " создан."), true);
        return 1;
    }
}

