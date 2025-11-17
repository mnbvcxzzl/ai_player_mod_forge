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
import net.minecraft.world.item.ItemStack;

public class BotSpawnCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bot")
            .then(Commands.literal("spawn")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("skin", StringArgumentType.string())
                        .executes(ctx -> spawnBot(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "skin")))
                    )
                    .executes(ctx -> spawnBot(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "Steve"))
                )
                .executes(ctx -> spawnBot(ctx.getSource(), "AI_Bot", "Steve"))
            )
            .then(Commands.literal("clearinventory")
                .then(Commands.argument("botname", StringArgumentType.string())
                    .executes(ctx -> clearBotInventory(ctx.getSource(), StringArgumentType.getString(ctx, "botname")))
                )
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
        bot.setCustomName(Component.literal(name));
        bot.setCustomNameVisible(true);

        bot.setOwnerUUID(player.getUUID());
        bot.setTame(true);

        bot.setSkinName(skinName);

        level.addFreshEntity(bot);

        source.sendSuccess(() -> Component.literal("Бот " + name + " создан с скином " + skinName + "."), true);
        return 1;
    }

    private static int clearBotInventory(CommandSourceStack source, String botName) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("Только игроки могут использовать эту команду!"));
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();

        AIPlayerEntity targetBot = null;
        for (AIPlayerEntity bot : level.getEntitiesOfClass(AIPlayerEntity.class, 
                player.getBoundingBox().inflate(100.0D))) {
            if (bot.getCustomName().getString().equalsIgnoreCase(botName)) {
                targetBot = bot;
                break;
            }
        }

        if (targetBot == null) {
            source.sendFailure(Component.literal("Бот с именем '" + botName + "' не найден рядом!"));
            return 0;
        }

        int clearedItems = 0;
        for (int i = 0; i < targetBot.getInventory().getContainerSize(); i++) {
            if (!targetBot.getInventory().getItem(i).isEmpty()) {
                clearedItems++;
                targetBot.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }

        final int finalClearedItems = clearedItems;
        final String finalBotName = botName;

        source.sendSuccess(() -> Component.literal("Инвентарь бота " + finalBotName + " очищен! Удалено предметов: " + finalClearedItems), true);
        
        if (targetBot.getMemory() != null) {
            targetBot.getMemory().addAction("игрок очистил инвентарь");
        }
        
        return 1;
    }
}