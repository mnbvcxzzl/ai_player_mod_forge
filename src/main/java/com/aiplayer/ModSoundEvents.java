package com.aiplayer.sound;

import com.aiplayer.AIPlayerMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, AIPlayerMod.MODID);

    public static final RegistryObject<SoundEvent> BURP = SOUND_EVENTS.register("burp",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AIPlayerMod.MODID, "burp")));

    public static final RegistryObject<SoundEvent> FART = SOUND_EVENTS.register("fart",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AIPlayerMod.MODID, "fart")));

    public static final RegistryObject<SoundEvent> HURT = SOUND_EVENTS.register("hurt",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AIPlayerMod.MODID, "hurt")));

    // НОВЫЙ ЗВУК СМЕРТИ
    public static final RegistryObject<SoundEvent> DEXTER = SOUND_EVENTS.register("dexter",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AIPlayerMod.MODID, "dexter")));

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}