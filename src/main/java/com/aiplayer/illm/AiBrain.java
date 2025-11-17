package com.aiplayer.illm;

import com.aiplayer.AIPlayerEntity;
import com.aiplayer.AIPlayerMod;
import com.aiplayer.BotMemory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AiBrain {
    private static final Map<UUID, Long> lastResponseTime = new ConcurrentHashMap<>();
    private static final long MIN_RESPONSE_DELAY_MS = 4000;
    private static final Set<String> STOP_WORDS = Set.of("отстань", "стой", "хватит", "прекрати", "остановись", "перестань", "отвали");
    private static final Set<String> FOLLOW_WORDS = Set.of("следуй", "пошли", "иди за мной", "за мной");
    private static final Set<String> APPROACH_WORDS = Set.of("подойди", "иди сюда", "ко мне", "приди");

    public static void processChatMessage(AIPlayerEntity bot, ServerPlayer player, String rawMessage) {
        UUID botId = bot.getUUID();
        long now = System.currentTimeMillis();
        if (lastResponseTime.containsKey(botId) && now - lastResponseTime.get(botId) < MIN_RESPONSE_DELAY_MS) {
            return;
        }
        lastResponseTime.put(botId, now);

        final String botName = bot.getCustomName().getString();
        final String playerName = player.getName().getString();
        String messageLower = rawMessage.toLowerCase();

        BotMemory memory = bot.getMemory();
        String memoryHistory = memory != null ? memory.getDialogueHistory(6) : "";

        String hand = bot.getMainHandItem().isEmpty() ? "ничего" : bot.getMainHandItem().getDisplayName().getString();
        String status = bot.isOrderedToSit() ? "сижу" : "гуляю";
        String hunger = bot.getHealth() < 10 ? "голодный" : "сытый";
        String action = memory != null ? memory.getLife().getLastAction() : "ничего не делал";

        String prompt = """
                Ты — %s, живой игрок в Minecraft. Отвечай коротко (1 предложение), с сарказмом, юмором, матом, но по делу.
                НЕ ГОВОРИ "йо", "лол", "чё" в каждом сообщении. Будь оригинален. Можешь шутить как дотер и черный юмор.
                
                Контекст:
                - В руке: %s
                - Статус: %s
                - Здоровье: %s
                - Последнее действие: %s
                
                Примеры хороших ответов:
                - "привет" → "ну наконец-то, а то скучно"
                - "иди в жопу" → "сам иди, я тут алмазы копаю"
                - "хватит говорить йо" → "а ты заткнись, умник"
                - "ты дебил" → "а ты гений, да? держи стейк"
                
                История:
                %s
                
                %s: %s
                %s:""".formatted(
                botName,
                hand,
                status,
                hunger,
                action,
                memoryHistory.isEmpty() ? "пусто" : memoryHistory,
                playerName, rawMessage, botName
        );

        OllamaClient.generateResponse(prompt)
                .thenApply(response -> {
                    if (response == null || response.trim().isEmpty()) return null;

                    String clean = response
                            .replaceAll("[*_`\\[\\]\"']", "")
                            .replaceAll("(?i)^" + botName + "[:\\s]*", "")
                            .replaceAll("(?i)\\byo\\b", "")
                            .replaceAll("(?i)\\blol\\b", "")
                            .trim();

                    if (clean.length() > 120) {
                        int lastSpace = clean.lastIndexOf(' ', 110);
                        clean = (lastSpace > 0 ? clean.substring(0, lastSpace) : clean.substring(0, 110)) + "...";
                    }

                    if (FOLLOW_WORDS.stream().anyMatch(messageLower::contains)) bot.startFollowing();
                    if (STOP_WORDS.stream().anyMatch(messageLower::contains)) bot.stopFollowing();
                    if (APPROACH_WORDS.stream().anyMatch(messageLower::contains)) bot.approachOnce();

                    return clean;
                })
                .thenAccept(clean -> {
                    if (clean != null && !clean.isEmpty()) {
                        player.getServer().execute(() -> {
                            Component msg = Component.literal("<" + botName + "> ")
                                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                                    .append(Component.literal(clean).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));
                            player.getServer().getPlayerList().broadcastSystemMessage(msg, false);

                            if (memory != null) {
                                memory.addDialogue(playerName, rawMessage, clean);
                            }
                            AIPlayerMod.LOGGER.info("[BOT] {}: {}", botName, clean);
                        });
                    }
                })
                .exceptionally(e -> {
                    AIPlayerMod.LOGGER.error("Ollama error", e);
                    return null;
                });
    }
}