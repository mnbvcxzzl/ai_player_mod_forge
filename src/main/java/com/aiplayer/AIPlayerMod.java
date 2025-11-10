package com.aiplayer;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(AIPlayerMod.MODID)
public class AIPlayerMod {
    public static final String MODID = "aiplayerid_00";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AIPlayerMod(FMLJavaModLoadingContext context) {  // <-- Добавьте параметр
        LOGGER.info("AIPlayerMod (Forge) loading...");

        IEventBus modBus = context.getModEventBus();  // <-- Теперь без .get()

        // Регистрируем сущности
        ModEntities.ENTITIES.register(modBus);

        // Регистрируем создание атрибутов для наших сущностей
        modBus.addListener(this::onEntityAttributeCreation);

        // Регистрация команд на Forge bus
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.AI_PLAYER.get(), AIPlayerEntity.createAttributes().build());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        BotSpawnCommand.register(event.getDispatcher());
    }
}