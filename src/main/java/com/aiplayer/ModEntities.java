package com.aiplayer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AIPlayerMod.MODID);

    public static final RegistryObject<EntityType<AIPlayerEntity>> AI_PLAYER =
            ENTITIES.register("ai_player",
                    () -> EntityType.Builder.<AIPlayerEntity>of(AIPlayerEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.95f)
                            .build(ResourceLocation.fromNamespaceAndPath(AIPlayerMod.MODID, "ai_player").toString())
            );
}