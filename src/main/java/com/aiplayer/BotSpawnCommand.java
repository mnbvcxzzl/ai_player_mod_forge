package com.aiplayer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

public class BotSpawnCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bot")
            .requires(source -> source.hasPermission(2)) // Только операторы
            .then(Commands.literal("spawn")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("skin", StringArgumentType.word())
                        .executes(ctx -> spawnBot(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "name"),
                            StringArgumentType.getString(ctx, "skin")
                        ))
                    )
                    .executes(ctx -> spawnBot(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "name"),
                        "Steve" // Дефолтный скин
                    ))
                )
                .executes(ctx -> spawnBot(ctx.getSource(), "AI_Bot", "Steve")) // /bot spawn
            )
        );
    }

    private static int spawnBot(CommandSourceStack source, String name, String skinName) {
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("Только игроки могут спавнить ботов!"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        BlockPos pos = player.blockPosition().offset(1, 0, 1);

        EntityType<AIPlayerEntity> type = ModEntities.AI_PLAYER.get();
        AIPlayerEntity bot = type.create(level);
        if (bot == null) {
            source.sendFailure(Component.literal("Не удалось создать бота!"));
            return 0;
        }

        bot.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        bot.setCustomName(Component.literal(name));
        bot.setCustomNameVisible(true);

        // Приручаем и устанавливаем владельца
        bot.setTame(true);
        bot.setOwnerUUID(player.getUUID());

        // Сохраняем имя скина (для будущего использования)
        bot.setSkinName(skinName);

        level.addFreshEntity(bot);

        source.sendSuccess(() -> Component.literal("Бот '" + name + "' заспавнен со скином '" + skinName + "'!"), true);
        return 1;
    }
}