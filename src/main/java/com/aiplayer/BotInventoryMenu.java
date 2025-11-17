package com.aiplayer;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

public class BotInventoryMenu extends ChestMenu {
    
    public BotInventoryMenu(int containerId, Inventory playerInventory, AIPlayerEntity bot) {
        super(MenuType.GENERIC_9x4, containerId, playerInventory, bot.getInventory(), 4);
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // Всегда доступно в творческом режиме
    }
}