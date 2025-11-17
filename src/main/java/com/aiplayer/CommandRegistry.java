// src/main/java/com/aiplayer/CommandRegistry.java
package com.aiplayer;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AIPlayerMod.MODID)
public class CommandRegistry {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BotSpawnCommand.register(event.getDispatcher());
    }
}