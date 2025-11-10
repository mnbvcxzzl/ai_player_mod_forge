package com.aiplayer;

import com.aiplayer.illm.AiBrain;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AIPlayerMod.MODID)
public class ChatEventHandler {

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        if (event.isCanceled()) return;

        ServerPlayer player = event.getPlayer();
        // В Forge 1.20.1 getMessage() возвращает Component → нужно .getString()
        String message = event.getMessage().getString();

        player.level().getEntitiesOfClass(AIPlayerEntity.class, player.getBoundingBox().inflate(32.0D))
            .forEach(bot -> {
                if (bot.isTame() && bot.getOwnerUUID() != null &&
                    bot.getOwnerUUID().equals(player.getUUID())) {
                    AiBrain.processChatMessage(bot, player, message);
                }
            });
    }
}