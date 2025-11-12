// src/main/java/com/aiplayer/illm/WorldState.java
package com.aiplayer.illm;

import com.aiplayer.AIPlayerEntity;
import net.minecraft.world.phys.Vec3;

public class WorldState {

    private final AIPlayerEntity bot;

    public WorldState(AIPlayerEntity bot) {
        this.bot = bot;
    }

    public String getShortState() {
        Vec3 pos = bot.position();
        return String.format("Бот %s на %.1f,%.1f,%.1f | Здоровье %.1f | Ближайший игрок %s",
                bot.getCustomName().getString(),
                pos.x, pos.y, pos.z,
                bot.getHealth(),
                bot.getOwner() != null ? bot.getOwner().getName().getString() : "нет");
    }

    public String toChatPrompt(String playerName, String message) {
        String state = getShortState();
        double x = bot.getX();
        double y = bot.getY();
        double z = bot.getZ();

        return """
            ТЫ — игрок Minecraft. Отвечай ТОЛЬКО валидным JSON! Никакого текста вне JSON!
            Состояние: %s
            Команда от %s: %s

            Доступные действия:
            - mine / dig → копать блок
            - collect / pickup → взять предмет
            - move_to / goto → идти к координатам
            - follow_owner / follow → идти за хозяином
            - idle / stop / wait / stand → стоять на месте

            ОБЯЗАТЕЛЬНЫЙ ФОРМАТ:
            {"action":"mine","x":%d,"y":%d,"z":%d}
            или
            {"action":"move_to","x":%d,"y":%d,"z":%d}
            или
            {"action":"collect","x":%d,"y":%d,"z":%d}
            или
            {"action":"follow_owner"}
            или
            {"action":"-idle"}

            ТОЛЬКО ОДИН JSON-ОБЪЕКТ! НИКАКИХ ПОЯСНЕНИЙ!
            """.formatted(state, playerName, message,
                    (int)x, (int)y, (int)z,
                    (int)x, (int)y, (int)z,
                    (int)x, (int)y, (int)z);
    }
}