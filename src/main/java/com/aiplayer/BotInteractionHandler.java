package com.aiplayer;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AIPlayerMod.MODID)
public class BotInteractionHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof AIPlayerEntity bot && event.getHand() == InteractionHand.MAIN_HAND) {
            ItemStack botItem = bot.getMainHandItem();
            if (!botItem.isEmpty()) {
                Player player = event.getEntity();
                if (!player.getInventory().add(botItem)) {
                    player.drop(botItem, false);
                }
                bot.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}