package com.aiplayer;

import com.aiplayer.sound.ModSoundEvents;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(AIPlayerMod.MODID)
public class AIPlayerMod {
    public static final String MODID = "aiplayerid_00";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public AIPlayerMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.ENTITIES.register(modBus);
        ModSoundEvents.SOUND_EVENTS.register(modBus);

        modBus.addListener(this::onEntityAttributeCreation);

        MinecraftForge.EVENT_BUS.register(new BotInteractionHandler());
        MinecraftForge.EVENT_BUS.register(new ChatEventHandler());
        MinecraftForge.EVENT_BUS.register(new CommandRegistry());
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.AI_PLAYER.get(), AIPlayerEntity.createAttributes().build());
    }
}