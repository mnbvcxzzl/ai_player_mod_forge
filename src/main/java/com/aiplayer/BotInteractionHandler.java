package com.aiplayer;

import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
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
            Player player = event.getEntity();
            
            // В творческом режиме - открываем инвентарь
            if (player.isCreative()) {
                if (!player.level().isClientSide) {
                    player.openMenu(new SimpleMenuProvider(
                        (containerId, playerInventory, playerEntity) -> 
                            new BotInventoryMenu(containerId, playerInventory, bot),
                        Component.literal("Инвентарь " + bot.getCustomName().getString())
                    ));
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
            } 
            // В выживании - забираем предмет из руки бота
            else {
                ItemStack botItem = bot.getMainHandItem();
                if (!botItem.isEmpty()) {
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
}