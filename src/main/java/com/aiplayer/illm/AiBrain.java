package com.aiplayer.illm;

import com.aiplayer.AIPlayerEntity;
import com.aiplayer.AIPlayerMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AiBrain {

    private static final Map<UUID, Long> lastResponseTime = new ConcurrentHashMap<>();
    private static final long MIN_RESPONSE_DELAY_MS = 5000; // 5 секунд между запросами

    public static String generateResponseForBot(AIPlayerEntity bot, String playerName, String message) {
        String personality = "Ты дружелюбный ИИ-компаньон в Minecraft по имени " + bot.getCustomName().getString() +
                ". Твоя задача — помогать игроку " + playerName +
                ", быть поддержкой и иногда шутить. Ты можешь шутить про свои 'особенности' (ты часто пускаешь газы и чавкаешь), но не злоупотребляй этим. ";

        String context = " Сейчас ты находишься в мире Minecraft. У тебя в руке: " +
                (bot.getMainHandItem().isEmpty() ? "ничего" : bot.getMainHandItem().getDisplayName().getString()) +
                ". Ты можешь двигаться, собирать предметы и следовать за игроком.";

        String fullPrompt = personality + context + "\n\n" +
                "Сообщение от игрока '" + playerName + "':\n" + message + "\n\n" +
                "Ответь кратко (максимум 2 предложения), как настоящий друг. Не используй форматирование, только текст.";

        return fullPrompt;
    }

    public static void processChatMessage(AIPlayerEntity bot, ServerPlayer player, String rawMessage) {
        String botName = bot.getCustomName().getString().toLowerCase();
        String message = rawMessage.toLowerCase();

        if (!message.contains(botName) && !message.contains("бот") && !message.contains("ai")) {
            return;
        }

        // Защита от спама
        UUID botId = bot.getUUID();
        long now = System.currentTimeMillis();
        if (lastResponseTime.containsKey(botId) && now - lastResponseTime.get(botId) < MIN_RESPONSE_DELAY_MS) {
            return;
        }
        lastResponseTime.put(botId, now);

        String prompt = generateResponseForBot(bot, player.getName().getString(), rawMessage);

        OllamaClient.generateResponse(prompt, "llama3")
            .thenAccept(response -> {
                if (response != null && !response.isEmpty()) {
                    player.getServer().execute(() -> {
                        String cleanResponse = sanitizeResponse(response);
                        // Отправляем как обычное сообщение в чат
                        player.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal(bot.getCustomName().getString() + ": " + cleanResponse),
                            false
                        );
                        AIPlayerMod.LOGGER.info("[AI RESPONSE] Bot: {} | Player: {} | Message: '{}'",
                                bot.getCustomName().getString(), player.getName().getString(), cleanResponse);
                    });
                } else {
                    player.getServer().execute(() -> {
                        player.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal(bot.getCustomName().getString() + ": Я не понял... Попробуй переформулировать?"),
                            false
                        );
                    });
                }
            })
            .exceptionally(throwable -> {
                AIPlayerMod.LOGGER.error("Ollama processing failed", throwable);
                player.getServer().execute(() -> {
                    player.getServer().getPlayerList().broadcastSystemMessage(
                        Component.literal(bot.getCustomName().getString() + ": Ой! Мой мозг сейчас не отвечает. Запусти Ollama!"),
                        false
                    );
                });
                return null;
            });
    }

    private static String sanitizeResponse(String response) {
        return response.replaceAll("[\\[\\]\\*_`]", "").trim();
    }
}