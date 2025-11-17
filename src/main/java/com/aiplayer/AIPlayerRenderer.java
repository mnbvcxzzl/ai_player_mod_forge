// src/main/java/com/aiplayer/AIPlayerRenderer.java
package com.aiplayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class AIPlayerRenderer extends HumanoidMobRenderer<AIPlayerEntity, HumanoidModel<AIPlayerEntity>> {

    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AIPlayerMod.MODID, "textures/entity/ai_player.png");

    public AIPlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);

        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
    }

    @Override
    public ResourceLocation getTextureLocation(AIPlayerEntity entity) {
        String skinName = entity.getSkinName().toLowerCase();

        // Пробуем загрузить текстуру: textures/entity/название.png
        ResourceLocation custom = ResourceLocation.fromNamespaceAndPath(AIPlayerMod.MODID, "textures/entity/" + skinName + ".png");

        // Проверяем, существует ли файл (только на клиенте)
        if (Minecraft.getInstance().getResourceManager().getResource(custom).isPresent()) {
            return custom;
        }

        // Если нет — дефолтный
        return DEFAULT_TEXTURE;
    }
}