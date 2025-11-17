// src/main/java/com/aiplayer/ChatEventHandler.java
package com.aiplayer;

import com.aiplayer.illm.AiBrain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = AIPlayerMod.MODID)
public class ChatEventHandler {

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getRawText();

        // Ищем ботов игрока в радиусе 32 блоков
        player.getServer().getAllLevels().forEach(level -> {
            if (!(level instanceof ServerLevel)) return;  // ← УБРАЛИ pattern matching

            ServerLevel serverLevel = (ServerLevel) level;  // ← Явное приведение

            AABB box = player.getBoundingBox().inflate(32);
            List<AIPlayerEntity> bots = serverLevel.getEntitiesOfClass(
                AIPlayerEntity.class,
                box,
                bot -> bot.getOwnerUUID() != null && bot.getOwnerUUID().equals(player.getUUID())
            );

            bots.forEach(bot -> AiBrain.processChatMessage(bot, player, message));
        });
    }
}