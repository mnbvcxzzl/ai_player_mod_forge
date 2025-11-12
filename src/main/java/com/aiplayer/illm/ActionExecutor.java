// src/main/java/com/aiplayer/illm/ActionExecutor.java
package com.aiplayer.illm;

import com.aiplayer.AIPlayerEntity;
import com.aiplayer.AIPlayerMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

public class ActionExecutor {

    public static void executeAction(AIPlayerEntity bot, String jsonResponse) {
        AIPlayerMod.LOGGER.info("EXECUTING: {}", jsonResponse);
        try {
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();

            JsonElement actionElem = json.get("action");
            if (actionElem == null) {
                AIPlayerMod.LOGGER.warn("No 'action' field in JSON: {}", jsonResponse);
                return;
            }
            String action = actionElem.getAsString().toLowerCase().trim();
            bot.setOrderedToSit(false);

            switch (action) {
                case "move_to", "goto" -> {
                    double x = getDouble(json, "x", bot.getX());
                    double y = getDouble(json, "y", bot.getY());
                    double z = getDouble(json, "z", bot.getZ());
                    bot.getNavigation().moveTo(x, y, z, 1.0);
                    AiBrain.sendChatMessageAsPlayer(bot, "Иду к " + (int)x + "," + (int)y + "," + (int)z);
                }
                case "mine", "dig" -> {
                    double mx = getDouble(json, "x", bot.getX());
                    double my = getDouble(json, "y", bot.getY());
                    double mz = getDouble(json, "z", bot.getZ());
                    BlockPos pos = BlockPos.containing(mx, my, mz);
                    if (pos == null || pos.equals(BlockPos.ZERO)) {
                        pos = bot.blockPosition().below(); // fallback: копать под собой
                    }
                    bot.setMiningTarget(pos);
                    AiBrain.sendChatMessageAsPlayer(bot, "Копаю " + pos);
                }
                case "collect", "pickup" -> {
                    double cx = getDouble(json, "x", bot.getX());
                    double cy = getDouble(json, "y", bot.getY());
                    double cz = getDouble(json, "z", bot.getZ());
                    AABB box = new AABB(cx-3, cy-3, cz-3, cx+3, cy+3, cz+3);
                    var items = bot.level().getEntitiesOfClass(ItemEntity.class, box);
                    if (!items.isEmpty()) {
                        bot.setCollectTarget(items.get(0));
                        AiBrain.sendChatMessageAsPlayer(bot, "Беру предмет");
                    }
                }
                case "follow_owner", "follow" -> {
                    bot.startFollowingOwner();
                    AiBrain.sendChatMessageAsPlayer(bot, "Иду за хозяином");
                }
                case "idle", "stop", "wait", "nothing", "stand" -> {
                    bot.getNavigation().stop();
                    bot.setMiningTarget(null);
                    bot.setCollectTarget(null);
                    AiBrain.sendChatMessageAsPlayer(bot, "Стою на месте");
                }
                case "attack" -> {
                    // fallback — ломаем блок над собой
                    bot.setMiningTarget(bot.blockPosition().above());
                    AiBrain.sendChatMessageAsPlayer(bot, "Атакую!");
                }
                case "place_block" -> {
                    // fallback — просто стоим
                    bot.getNavigation().stop();
                    AiBrain.sendChatMessageAsPlayer(bot, "Строю (не реализовано)");
                }
                default -> AIPlayerMod.LOGGER.warn("Unknown action '{}'", action);
            }
        } catch (Exception e) {
            AIPlayerMod.LOGGER.error("Action parse error: {}", e.toString());
        }
    }

    private static double getDouble(JsonObject json, String key, double fallback) {
        JsonElement elem = json.get(key);
        if (elem != null && elem.isJsonPrimitive()) {
            try {
                return elem.getAsDouble();
            } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }
}